package com.umar.policy.service;

import com.umar.enums.auth.AuthRole;
import com.umar.enums.auth.DelegationScope;
import com.umar.enums.auth.StepUpActionClass;
import com.umar.enums.policy.*;
import com.umar.policy.client.PolicyExternalClients;
import com.umar.policy.config.PolicyProperties;
import com.umar.policy.entity.*;
import com.umar.policy.exception.PolicyApiException;
import com.umar.policy.repository.*;
import com.umar.policy.security.PolicyIdentity;
import com.umar.policy.security.PolicySecurityContext;
import com.umar.payload.request.policy.*;
import com.umar.payload.response.policy.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PolicyLifecycleService {

    private static final String CURRENT_TERMS_VERSION = "5";

    private final ProductVersionRepository productRepository;
    private final QuoteRepository quoteRepository;
    private final PolicyRepository policyRepository;
    private final PolicyHistoryRepository historyRepository;
    private final SubsidySchemeRepository subsidyRepository;
    private final RegionEnrollmentStateRepository regionRepository;
    private final ProductGovernanceService productService;
    private final PolicyExternalClients clients;
    private final PolicyOutboxService outboxService;
    private final PolicyIdempotencyService idempotencyService;
    private final PolicyRateLimitService rateLimitService;
    private final PolicyProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public QuoteResponse generateQuote(GenerateQuoteRequest request, String delegationHeader, String idempotencyKey) {
        PolicyIdentity actor = PolicySecurityContext.require();
        PolicyExternalClients.PlotDetails plot = clients.getPlotDetails(request.getPlotId());
        if (!plot.available()) {
            throw new PolicyApiException(HttpStatus.SERVICE_UNAVAILABLE, "FARMER_SERVICE_UNAVAILABLE",
                    "Please try again in a moment");
        }
        if (!plot.found()) {
            throw new PolicyApiException(HttpStatus.NOT_FOUND, "PLOT_NOT_FOUND", "Plot not found");
        }

        UUID farmerId = resolveFarmerId(request.getFarmerId(), actor, plot);
        authorizeAgentOrSelf(actor, farmerId, delegationHeader, false);

        int limit = actor.hasRole(AuthRole.AGENT) ? 200 : 30;
        rateLimitService.check("pol02:" + actor.actorId(), limit, 3600);

        PolicyExternalClients.FarmerContext farmer = clients.getFarmerContext(farmerId);
        if (!farmer.available()) {
            throw new PolicyApiException(HttpStatus.SERVICE_UNAVAILABLE, "FARMER_SERVICE_UNAVAILABLE",
                    "Please try again in a moment");
        }

        if (!"VALIDATED".equals(plot.status()) || !plot.activeSeason()) {
            throw new PolicyApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PLOT_NOT_ELIGIBLE",
                    "This plot cannot be insured yet", Map.of("reasons", List.of("NO_ACTIVE_SEASON")));
        }

        ProductVersionEntity pv = resolvePublishedProduct(request.getProductId());
        CreateProductRequest def = productService.readDefinition(pv.getDefinitionJson());

        if (!def.getEligibility().getCropCodes().contains(plot.cropCode())
                || !def.getEligibility().getRegionCodes().contains(plot.regionCode())
                || plot.areaHa() < def.getEligibility().getMinAreaHa()
                || plot.areaHa() > def.getEligibility().getMaxAreaHa()) {
            throw new PolicyApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PRODUCT_NOT_ELIGIBLE",
                    "This product is not available for this plot");
        }

        LocalDate today = LocalDate.now();
        boolean regionClosed = regionRepository.findById(plot.regionCode())
                .map(r -> "CLOSED".equals(r.getStatus())).orElse(false);
        if (regionClosed || def.getEnrollment().getOpensOn().isAfter(today)
                || def.getEnrollment().getClosesOn().isBefore(today)) {
            throw new PolicyApiException(HttpStatus.CONFLICT, "ENROLLMENT_CLOSED",
                    "Cover is not available for this area right now");
        }

        List<PolicyEntity> existing = policyRepository.findByPlotIdAndPerilAndStatusIn(
                request.getPlotId(), pv.getPeril(),
                List.of(PolicyStatus.PENDING_PAYMENT, PolicyStatus.PAID_WAITING, PolicyStatus.ON_HOLD, PolicyStatus.ACTIVE));
        if (!existing.isEmpty()) {
            throw new PolicyApiException(HttpStatus.CONFLICT, "POLICY_EXISTS",
                    "This plot already has cover", Map.of("policyId", existing.getFirst().getPolicyId()));
        }

        if (request.getCoverageLevel() == null && request.getSumInsuredMinor() == null) {
            throw new PolicyApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Either coverageLevel or sumInsuredMinor is required");
        }

        long sumInsuredMinor = computeSumInsured(request, def, plot.areaHa());

        String inputsHash = productService.sha256(farmerId + "|" + request.getPlotId() + "|" + pv.getProductId()
                + "|" + pv.getVersionNum() + "|" + sumInsuredMinor + "|" + Objects.toString(request.getSubsidyCode(), ""));

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<QuoteResponse> replay = idempotencyService.findReplay(
                    idempotencyKey, "POL-02:" + actor.actorId(), request, QuoteResponse.class);
            if (replay.isPresent()) return replay.get();
        }
        Optional<QuoteEntity> existingQuote = quoteRepository
                .findFirstByFarmerIdAndPlotIdAndProductIdAndInputsHashAndValidUntilAfterAndUsedByPolicyIdIsNull(
                        farmerId, request.getPlotId(), pv.getProductId(), inputsHash, Instant.now());
        if (existingQuote.isPresent()) {
            return toQuoteResponse(existingQuote.get(), def);
        }

        PolicyExternalClients.RiskScoreResult score = clients.scoreRisk(request.getPlotId(), plot.regionCode(), plot.cropCode());
        ScoreSource scoreSource = score.modelAvailable() ? ScoreSource.MODEL : ScoreSource.FALLBACK_TARIFF;

        long pureRiskMinor = Math.round(sumInsuredMinor * 0.055); // 5.5% base actuarial rate
        int riskAdjBps = score.modelAvailable()
                ? score.adjustmentBps()
                : (int) Math.round(properties.getRules().getFallbackTariffLoadingPercent() * 100);
        long riskLoadingMinor = Math.round(pureRiskMinor * (riskAdjBps / 10000.0));
        long expenseLoadingMinor = Math.round(pureRiskMinor * (def.getPricing().getExpenseLoadingPercent() / 100.0));

        List<Map<String, Object>> loadings = new ArrayList<>();
        loadings.add(Map.of("code", "EXPENSE", "label", "Running costs", "amountMinor", expenseLoadingMinor));
        loadings.add(Map.of("code", "RISK", "label",
                scoreSource == ScoreSource.MODEL ? "Local rainfall risk" : "Standard zone tariff loading",
                "amountMinor", riskLoadingMinor));

        long customLoadingTotal = 0;
        for (Map<String, Object> cl : clients.preQuoteHook(request.getPlotId(), pv.getProductId()).customLoadings()) {
            long amt = ((Number) cl.getOrDefault("amountMinor", 0L)).longValue();
            customLoadingTotal += Math.max(0L, amt);
            loadings.add(cl);
        }

        long grossPremium = pureRiskMinor + riskLoadingMinor + expenseLoadingMinor + customLoadingTotal;
        grossPremium = Math.max(grossPremium, def.getPricing().getMinPremiumMinor());

        SubsidySchemeEntity scheme = selectSubsidy(request.getSubsidyCode(), plot.regionCode(), plot.cropCode());
        long subsidyMinor = 0;
        List<String> warnings = new ArrayList<>();
        Map<String, Object> subsidyMap = null;
        if (scheme != null && (scheme.getBudgetMinor() - scheme.getReservedMinor()) > 0) {
            subsidyMinor = Math.round(grossPremium * (scheme.getPercentBps() / 10000.0));
            if (scheme.getCapMinor() != null) subsidyMinor = Math.min(subsidyMinor, scheme.getCapMinor());
            long avail = scheme.getBudgetMinor() - scheme.getReservedMinor();
            if (subsidyMinor > avail) subsidyMinor = avail;
            boolean lowBudget = (avail - subsidyMinor) < Math.max(100_000L, scheme.getBudgetMinor() / 10);
            if (lowBudget) warnings.add("SUBSIDY_MAY_RUN_OUT");
            subsidyMap = Map.of(
                    "schemeCode", scheme.getSchemeCode(),
                    "funder", scheme.getFunder(),
                    "percent", scheme.getPercentBps() / 100.0,
                    "budgetWarning", lowBudget);
        }
        if (!farmer.walletUsable()) warnings.add("WALLET_NOT_VERIFIED");

        long levyMinor = Math.round((grossPremium - subsidyMinor) * 0.015);
        long totalPayableMinor = Math.max(def.getPricing().getMinPremiumMinor(), grossPremium - subsidyMinor + levyMinor);

        String lang = request.getLanguage() != null ? request.getLanguage() : farmer.language();
        Instant now = Instant.now();
        Instant enrollClose = def.getEnrollment().getClosesOn().atTime(23, 59, 59).toInstant(ZoneOffset.UTC);
        Instant validityEnd = now.plus(properties.getRules().getQuoteValidityHours(), ChronoUnit.HOURS);
        if (enrollClose.isBefore(validityEnd)) validityEnd = enrollClose;
        Instant payBy = now.plus(def.getPayment().getPayByDays(), ChronoUnit.DAYS);

        List<Map<String, Object>> factors = List.of(Map.of(
                "code", score.factorCodes().getFirst(),
                "label", "Risk factor",
                "direction", "INCREASE",
                "amountMinor", riskLoadingMinor,
                "reason", scoreSource == ScoreSource.MODEL
                        ? "Rainfall history in your area affects the base risk rate."
                        : "Priced using the approved standard zone tariff."));

        List<Map<String, Object>> payoutTiers = def.getTrigger().getTiers().stream()
                .map(t -> Map.<String, Object>of(
                        "operator", t.getOperator(),
                        "thresholdValue", t.getThresholdValue(),
                        "payoutPercent", t.getPayoutPercent(),
                        "payoutAmountMinor", Math.round(sumInsuredMinor * (t.getPayoutPercent() / 100.0))))
                .toList();

        Map<String, Object> breakdown = Map.of(
                "pureRiskMinor", pureRiskMinor,
                "loadings", loadings,
                "subsidyMinor", -subsidyMinor,
                "levyMinor", levyMinor,
                "totalPayableMinor", totalPayableMinor);

        String quoteId = "qt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        QuoteEntity entity = QuoteEntity.builder()
                .quoteId(quoteId)
                .farmerId(farmerId)
                .plotId(request.getPlotId())
                .productId(pv.getProductId())
                .productVersion(pv.getVersionNum())
                .inputsHash(inputsHash)
                .sumInsuredMinor(sumInsuredMinor)
                .currency(def.getSumInsured().getCurrency())
                .totalPayableMinor(totalPayableMinor)
                .subsidyMinor(subsidyMinor)
                .subsidySchemeCode(scheme != null && subsidyMinor > 0 ? scheme.getSchemeCode() : null)
                .priceBreakdownJson(writeJson(breakdown))
                .factorsJson(writeJson(factors))
                .payoutTiersJson(writeJson(payoutTiers))
                .scoreBand(score.scoreBand())
                .scoreSource(scoreSource)
                .language(lang)
                .channel(request.getChannel())
                .waitingPeriodDays(def.getWaitingPeriodDays())
                .validUntil(validityEnd)
                .payBy(payBy)
                .createdAt(now)
                .build();
        quoteRepository.save(entity);

        outboxService.enqueue(properties.getKafka().getPolicyTopic(), "quote.created", quoteId, Map.of(
                "eventType", "quote.created",
                "quoteId", quoteId,
                "productId", pv.getProductId(),
                "productVersion", pv.getVersionNum(),
                "regionCode", plot.regionCode(),
                "totalPayableMinor", totalPayableMinor,
                "scoreSource", scoreSource.name(),
                "channel", request.getChannel(),
                "timestamp", now.toString()));

        QuoteResponse resp = QuoteResponse.builder()
                .quoteId(quoteId)
                .productId(pv.getProductId())
                .productVersion(pv.getVersionNum())
                .sumInsuredMinor(sumInsuredMinor)
                .currency(entity.getCurrency())
                .premium(breakdown)
                .factors(factors)
                .payoutTiers(payoutTiers)
                .waitingPeriodDays(def.getWaitingPeriodDays())
                .coverageWindowEstimate(estimateWindow(plot, def))
                .scoreSource(scoreSource.name())
                .subsidy(subsidyMap)
                .priceValidUntil(validityEnd.toString())
                .payBy(payBy.toString())
                .warnings(warnings.isEmpty() ? null : warnings)
                .build();
        idempotencyService.save(idempotencyKey, "POL-02:" + actor.actorId(), request, 200, resp);
        return resp;
    }

    @Transactional
    public PolicyResponse issuePolicy(IssuePolicyRequest request, String delegationHeader, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new PolicyApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Idempotency-Key header is required");
        }
        PolicyIdentity actor = PolicySecurityContext.require();
        Optional<PolicyResponse> replay = idempotencyService.findReplay(
                idempotencyKey, "POL-03:" + actor.actorId(), request, PolicyResponse.class);
        if (replay.isPresent()) return replay.get();

        QuoteEntity quote = quoteRepository.findById(request.getQuoteId())
                .orElseThrow(() -> new PolicyApiException(HttpStatus.NOT_FOUND, "QUOTE_NOT_FOUND", "Quote not found"));
        authorizeAgentOrSelf(actor, quote.getFarmerId(), delegationHeader, false);

        if (quote.getUsedByPolicyId() != null) {
            throw new PolicyApiException(HttpStatus.CONFLICT, "QUOTE_ALREADY_USED",
                    "This quote was already used", Map.of("policyId", quote.getUsedByPolicyId()));
        }
        if (quote.getValidUntil().isBefore(Instant.now())) {
            throw new PolicyApiException(HttpStatus.UNPROCESSABLE_ENTITY, "QUOTE_EXPIRED",
                    "This quote has expired. Please request a new one");
        }

        rateLimitService.check("pol03:" + actor.actorId(), actor.hasRole(AuthRole.AGENT) ? 100 : 10, 86400);

        if (!CURRENT_TERMS_VERSION.equals(request.getAcceptance().getTermsVersion())) {
            throw new PolicyApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ACCEPTANCE_INVALID",
                    "Please confirm again", Map.of("currentTermsVersion", CURRENT_TERMS_VERSION));
        }
        if (actor.hasRole(AuthRole.AGENT)) {
            var ev = request.getAcceptance().getEvidence();
            if (!"OTP_CONFIRMATION".equals(ev.getType()) || !clients.verifyOtpConfirmation(ev.getReference())) {
                throw new PolicyApiException(HttpStatus.UNPROCESSABLE_ENTITY, "ACCEPTANCE_INVALID",
                        "Agent-assisted purchases require valid farmer OTP confirmation");
            }
        }

        PolicyExternalClients.IdentityStanding idStanding = clients.getIdentityContext(quote.getFarmerId());
        if (!idStanding.available()) {
            throw new PolicyApiException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_SERVICE_UNAVAILABLE",
                    "Identity facts could not be confirmed");
        }

        ProductVersionEntity pv = productRepository.findByProductIdAndVersionNum(
                quote.getProductId(), quote.getProductVersion())
                .orElseThrow(() -> new PolicyApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found"));
        CreateProductRequest def = productService.readDefinition(pv.getDefinitionJson());

        if (!"ACTIVE".equals(idStanding.accountStatus())
                || ("TIER_2".equals(def.getEligibility().getMinKycTier()) && !"TIER_2".equals(idStanding.kycTier()))
                || "TIER_0".equals(idStanding.kycTier())) {
            throw new PolicyApiException(HttpStatus.PRECONDITION_FAILED, "KYC_REQUIRED",
                    "Identity verification is required first");
        }
        List<String> missingConsents = def.getEligibility().getRequiredConsents().stream()
                .filter(c -> !idStanding.activeConsents().contains(c))
                .toList();
        if (!missingConsents.isEmpty()) {
            throw new PolicyApiException(HttpStatus.PRECONDITION_FAILED, "CONSENT_REQUIRED",
                    "Consent is required", Map.of("missing", missingConsents));
        }

        PolicyExternalClients.FarmerContext farmer = clients.getFarmerContext(quote.getFarmerId());
        if (!farmer.available()) {
            throw new PolicyApiException(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_SERVICE_UNAVAILABLE",
                    "Farmer profile could not be confirmed");
        }
        if (!farmer.walletUsable()) {
            throw new PolicyApiException(HttpStatus.PRECONDITION_FAILED, "PAYOUT_DESTINATION_REQUIRED",
                    "A verified payout wallet is required");
        }

        PolicyExternalClients.PlotDetails plot = clients.getPlotDetails(quote.getPlotId());
        if (!plot.available()) {
            throw new PolicyApiException(HttpStatus.SERVICE_UNAVAILABLE, "PLOT_SERVICE_UNAVAILABLE",
                    "Plot could not be confirmed");
        }
        if (!"VALIDATED".equals(plot.status()) || !plot.activeSeason()) {
            throw new PolicyApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PLOT_NOT_ELIGIBLE",
                    "This plot is no longer eligible");
        }

        boolean regionClosed = regionRepository.findById(plot.regionCode())
                .map(r -> "CLOSED".equals(r.getStatus())).orElse(false);
        if (regionClosed) {
            throw new PolicyApiException(HttpStatus.CONFLICT, "ENROLLMENT_CLOSED",
                    "Cover is no longer available for this area");
        }
        List<PolicyEntity> conflicts = policyRepository.findByPlotIdAndPerilAndStatusIn(
                quote.getPlotId(), pv.getPeril(),
                List.of(PolicyStatus.PENDING_PAYMENT, PolicyStatus.PAID_WAITING, PolicyStatus.ON_HOLD, PolicyStatus.ACTIVE));
        if (!conflicts.isEmpty()) {
            throw new PolicyApiException(HttpStatus.CONFLICT, "POLICY_EXISTS",
                    "This plot already has cover", Map.of("policyId", conflicts.getFirst().getPolicyId()));
        }

        if (quote.getSubsidyMinor() > 0 && quote.getSubsidySchemeCode() != null) {
            int updated = subsidyRepository.reserveBudget(quote.getSubsidySchemeCode(), quote.getSubsidyMinor());
            if (updated == 0) {
                throw new PolicyApiException(HttpStatus.CONFLICT, "SUBSIDY_EXHAUSTED",
                        "The subsidy is no longer available", Map.of("newQuoteHint", true));
            }
        }

        Map<String, Object> attrs = clients.validateCustomAttributes(request.getAttributes());
        String summaryText = buildPlainSummary(def, quote, quote.getLanguage());
        String summaryDocRef = clients.storeDocument("POLICY_SUMMARY", summaryText);
        clients.storeDocument("ACCEPTANCE_EVIDENCE", writeJson(request.getAcceptance()));

        Map<String, Object> termsSnapshot = new LinkedHashMap<>();
        termsSnapshot.put("productVersion", pv.getVersionNum());
        termsSnapshot.put("definitionHash", pv.getDefinitionHash());
        termsSnapshot.put("index", def.getIndex());
        termsSnapshot.put("trigger", def.getTrigger());
        termsSnapshot.put("coverageWindow", def.getCoverageWindow());
        termsSnapshot.put("waitingPeriodDays", def.getWaitingPeriodDays());
        termsSnapshot.put("cancellation", def.getCancellation());
        termsSnapshot.put("sumInsuredMinor", quote.getSumInsuredMinor());
        termsSnapshot.put("currency", quote.getCurrency());
        termsSnapshot.put("totalPayableMinor", quote.getTotalPayableMinor());
        String termsJson = writeJson(termsSnapshot);
        String termsHash = productService.sha256(termsJson);

        String policyId = "pol_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String policyNumber = "AGS-" + farmer.country() + "-" + String.valueOf(LocalDate.now().getYear()).substring(2)
                + "-" + String.format("%06d", Math.abs(policyId.hashCode() % 1_000_000));
        Instant now = Instant.now();

        Map<String, Object> bd = readMap(quote.getPriceBreakdownJson());
        long levyMinor = ((Number) bd.getOrDefault("levyMinor", 0L)).longValue();

        PolicyEntity policy = PolicyEntity.builder()
                .policyId(policyId)
                .policyNumber(policyNumber)
                .farmerId(quote.getFarmerId())
                .plotId(quote.getPlotId())
                .quoteId(quote.getQuoteId())
                .productId(pv.getProductId())
                .productVersion(pv.getVersionNum())
                .peril(pv.getPeril())
                .definitionHash(pv.getDefinitionHash())
                .termsSnapshotJson(termsJson)
                .termsHash(termsHash)
                .status(PolicyStatus.PENDING_PAYMENT)
                .sumInsuredMinor(quote.getSumInsuredMinor())
                .currency(quote.getCurrency())
                .totalPayableMinor(quote.getTotalPayableMinor())
                .subsidyMinor(quote.getSubsidyMinor())
                .subsidySchemeCode(quote.getSubsidySchemeCode())
                .levyMinor(levyMinor)
                .payerType(request.getPayerType() != null ? request.getPayerType() : PayerType.FARMER)
                .premiumStatus("UNPAID")
                .payBy(quote.getPayBy())
                .waitingPeriodDays(quote.getWaitingPeriodDays())
                .payoutState(PayoutState.NONE)
                .paidToDateMinor(0L)
                .summaryText(summaryText)
                .summaryLanguage(quote.getLanguage())
                .termsVersion(request.getAcceptance().getTermsVersion())
                .summaryDocRef(summaryDocRef)
                .acceptanceJson(writeJson(request.getAcceptance()))
                .attributesJson(writeJson(attrs))
                .regionCode(plot.regionCode())
                .cropCode(plot.cropCode())
                .createdAt(now)
                .updatedAt(now)
                .build();
        policy = policyRepository.saveAndFlush(policy);

        quote.setUsedByPolicyId(policyId);
        quoteRepository.save(quote);

        recordHistory(policyId, null, PolicyStatus.PENDING_PAYMENT.name(), actor.actorId(), "Policy issued", now);

        outboxService.enqueue(properties.getKafka().getPolicyTopic(), "policy.issued", policyId, Map.ofEntries(
                Map.entry("eventType", "policy.issued"),
                Map.entry("policyId", policyId),
                Map.entry("policyNumber", policyNumber),
                Map.entry("farmerId", quote.getFarmerId().toString()),
                Map.entry("plotId", quote.getPlotId()),
                Map.entry("productId", pv.getProductId()),
                Map.entry("productVersion", pv.getVersionNum()),
                Map.entry("premiumMinor", quote.getTotalPayableMinor()),
                Map.entry("subsidyMinor", quote.getSubsidyMinor()),
                Map.entry("currency", quote.getCurrency()),
                Map.entry("payerType", policy.getPayerType().name()),
                Map.entry("payBy", quote.getPayBy().toString()),
                Map.entry("language", quote.getLanguage()),
                Map.entry("summaryRef", summaryDocRef),
                Map.entry("timestamp", now.toString())));

        PolicyResponse resp = PolicyResponse.builder()
                .policyId(policyId)
                .policyNumber(policyNumber)
                .status(PolicyStatus.PENDING_PAYMENT.name())
                .plotId(policy.getPlotId())
                .farmerId(policy.getFarmerId().toString())
                .productId(policy.getProductId())
                .productVersion(policy.getProductVersion())
                .premium(Map.of(
                        "totalPayableMinor", policy.getTotalPayableMinor(),
                        "subsidyMinor", policy.getSubsidyMinor(),
                        "currency", policy.getCurrency(),
                        "payerType", policy.getPayerType().name()))
                .payBy(policy.getPayBy().toString())
                .summary(Map.of(
                        "language", policy.getSummaryLanguage(),
                        "text", policy.getSummaryText(),
                        "termsVersion", policy.getTermsVersion()))
                .waitingPeriodDays(policy.getWaitingPeriodDays())
                .coverageWindowEstimate(estimateWindow(plot, def))
                .paymentInstructions(Map.of("channel", request.getAcceptance().getChannel(), "reference", policyNumber))
                .version(policy.getVersion())
                .createdAt(now.toString())
                .build();
        idempotencyService.save(idempotencyKey, "POL-03:" + actor.actorId(), request, 201, resp);
        return resp;
    }

    @Transactional
    public PolicyResponse activatePolicy(String policyId, ActivatePolicyRequest request) {
        PolicyIdentity actor = PolicySecurityContext.require();
        boolean allowed = actor.isServiceCaller() || actor.hasAnyRole(AuthRole.SUPPORT, AuthRole.ADMIN);
        if (!allowed) {
            throw new PolicyApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        if (request.getTrigger() == ActivationTrigger.MANUAL) {
            if (request.getReasonCode() == null || request.getNote() == null || request.getNote().trim().length() < 20) {
                throw new PolicyApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "reasonCode and note (20-500 chars) required for manual activation");
            }
        }
        PolicyEntity policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new PolicyApiException(HttpStatus.NOT_FOUND, "POLICY_NOT_FOUND", "Policy not found"));
        if (policy.getStatus() != PolicyStatus.PAID_WAITING && policy.getStatus() != PolicyStatus.ON_HOLD) {
            throw new PolicyApiException(HttpStatus.CONFLICT, "POLICY_NOT_ACTIVATABLE",
                    "This policy cannot be activated", Map.of("status", policy.getStatus().name()));
        }
        if (!"PAID".equals(policy.getPremiumStatus())) {
            throw new PolicyApiException(HttpStatus.CONFLICT, "PREMIUM_NOT_CONFIRMED",
                    "The premium has not been confirmed");
        }
        Instant now = Instant.now();
        if (policy.getWaitingEndsAt() != null && policy.getWaitingEndsAt().isAfter(now)) {
            throw new PolicyApiException(HttpStatus.CONFLICT, "WAITING_PERIOD_ACTIVE",
                    "The waiting period has not ended", Map.of("activatesAfter", policy.getWaitingEndsAt().toString()));
        }

        PolicyExternalClients.IdentityStanding idStanding = clients.getIdentityContext(policy.getFarmerId());
        PolicyExternalClients.FarmerContext farmer = clients.getFarmerContext(policy.getFarmerId());
        PolicyExternalClients.PlotDetails plot = clients.getPlotDetails(policy.getPlotId());
        if (!idStanding.available() || !farmer.available() || !plot.available()) {
            throw new PolicyApiException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                    "Activation will be retried");
        }

        ProductVersionEntity pv = productRepository.findByProductIdAndVersionNum(
                policy.getProductId(), policy.getProductVersion()).orElseThrow();
        CreateProductRequest def = productService.readDefinition(pv.getDefinitionJson());

        Set<PolicyHoldReason> holds = EnumSet.noneOf(PolicyHoldReason.class);
        if (!"ACTIVE".equals(idStanding.accountStatus()) || "TIER_0".equals(idStanding.kycTier())) {
            holds.add(PolicyHoldReason.KYC_REQUIRED);
        }
        if (!idStanding.activeConsents().containsAll(def.getEligibility().getRequiredConsents())) {
            holds.add(PolicyHoldReason.CONSENT_REQUIRED);
        }
        if (!farmer.walletUsable()) {
            holds.add(PolicyHoldReason.PAYOUT_DESTINATION_REQUIRED);
        }
        if (!"VALIDATED".equals(plot.status()) || !"ACTIVE".equals(plot.monitoringStatus())
                || !plot.activeSeason() || plot.unresolvedFlags()) {
            holds.add(PolicyHoldReason.PLOT_NOT_READY);
        }
        boolean regionClosed = regionRepository.findById(plot.regionCode())
                .map(r -> "CLOSED".equals(r.getStatus())).orElse(false);
        if (regionClosed) {
            holds.add(PolicyHoldReason.ENROLLMENT_CLOSED);
        }
        if (clients.preActivateCheckHook(policyId).hold()) {
            holds.add(PolicyHoldReason.PARTNER_HOLD);
        }

        String fromStatus = policy.getStatus().name();
        if (!holds.isEmpty()) {
            policy.setStatus(PolicyStatus.ON_HOLD);
            policy.setHoldReasons(holds);
            policy.setUpdatedAt(now);
            policy = policyRepository.saveAndFlush(policy);
            recordHistory(policyId, fromStatus, PolicyStatus.ON_HOLD.name(), actor.actorId(),
                    "Activation held: " + policy.getHoldReasonList(), now);
            outboxService.enqueue(properties.getKafka().getPolicyTopic(), "policy.activation.held", policyId, Map.of(
                    "eventType", "policy.activation.held",
                    "policyId", policyId,
                    "farmerId", policy.getFarmerId().toString(),
                    "plotId", policy.getPlotId(),
                    "holdReasons", policy.getHoldReasonList(),
                    "timestamp", now.toString()));
            String nextAction = (holds.contains(PolicyHoldReason.CONSENT_REQUIRED)
                    || holds.contains(PolicyHoldReason.PAYOUT_DESTINATION_REQUIRED))
                    ? "FARMER_ACTION" : "UNDERWRITER_REVIEW";
            return PolicyResponse.builder()
                    .policyId(policyId)
                    .status(PolicyStatus.ON_HOLD.name())
                    .holdReasons(policy.getHoldReasonList())
                    .nextAction(nextAction)
                    .version(policy.getVersion())
                    .build();
        }

        LocalDate anchor = plot.plantingDate() != null ? plot.plantingDate() : LocalDate.now();
        Instant covStart = anchor.plusDays(def.getCoverageWindow().getStartOffsetDays()).atStartOfDay().toInstant(ZoneOffset.UTC);
        if (now.isAfter(covStart)) covStart = now;
        Instant covEnd = anchor.plusDays(def.getCoverageWindow().getEndOffsetDays()).atTime(23, 59, 59).toInstant(ZoneOffset.UTC);

        policy.setStatus(PolicyStatus.ACTIVE);
        policy.setHoldReasons(Set.of());
        policy.setCoverageStart(covStart);
        policy.setCoverageEnd(covEnd);
        policy.setUpdatedAt(now);
        policy = policyRepository.saveAndFlush(policy);
        recordHistory(policyId, fromStatus, PolicyStatus.ACTIVE.name(), actor.actorId(),
                "Policy activated (" + request.getTrigger() + ")", now);

        outboxService.enqueue(properties.getKafka().getPolicyTopic(), "policy.activated", policyId, Map.ofEntries(
                Map.entry("eventType", "policy.activated"),
                Map.entry("policyId", policyId),
                Map.entry("policyNumber", policy.getPolicyNumber()),
                Map.entry("farmerId", policy.getFarmerId().toString()),
                Map.entry("plotId", policy.getPlotId()),
                Map.entry("productId", policy.getProductId()),
                Map.entry("productVersion", policy.getProductVersion()),
                Map.entry("definitionHash", policy.getDefinitionHash()),
                Map.entry("sumInsuredMinor", policy.getSumInsuredMinor()),
                Map.entry("currency", policy.getCurrency()),
                Map.entry("coverageStart", covStart.toString()),
                Map.entry("coverageEnd", covEnd.toString()),
                Map.entry("timestamp", now.toString())));

        return PolicyResponse.builder()
                .policyId(policyId)
                .status(PolicyStatus.ACTIVE.name())
                .coverageStart(covStart.toString())
                .coverageEnd(covEnd.toString())
                .holdReasons(List.of())
                .nextAction("NONE")
                .version(policy.getVersion())
                .build();
    }

    public PolicyResponse getPolicy(String idOrNumber, String includeParam, String delegationHeader) {
        PolicyIdentity actor = PolicySecurityContext.require();
        PolicyEntity p = policyRepository.findByPolicyIdOrPolicyNumber(idOrNumber, idOrNumber)
                .orElseThrow(() -> new PolicyApiException(HttpStatus.NOT_FOUND, "POLICY_NOT_FOUND", "Policy not found"));

        boolean isStaff = actor.hasAnyRole(AuthRole.SUPPORT, AuthRole.UNDERWRITER, AuthRole.FINANCE,
                AuthRole.COMPLIANCE_OFFICER, AuthRole.ADMIN);
        boolean isOwner = actor.userId() != null && actor.userId().equals(p.getFarmerId());
        boolean isDelegated = !isStaff && !isOwner && isValidAgentDelegation(actor, p.getFarmerId(), delegationHeader);

        if (!isStaff && !isOwner && !isDelegated) {
            throw new PolicyApiException(HttpStatus.NOT_FOUND, "POLICY_NOT_FOUND", "Policy not found");
        }

        Set<String> inc = includeParam == null ? Set.of("summary")
                : new HashSet<>(Arrays.asList(includeParam.toLowerCase().split(",")));

        ProductVersionEntity pv = productRepository.findByProductIdAndVersionNum(p.getProductId(), p.getProductVersion()).orElse(null);
        CreateProductRequest def = pv != null ? productService.readDefinition(pv.getDefinitionJson()) : null;

        Map<String, Object> prodMap = new LinkedHashMap<>();
        prodMap.put("productId", p.getProductId());
        prodMap.put("productVersion", p.getProductVersion());
        prodMap.put("peril", p.getPeril().name());
        if (def != null) prodMap.put("name", def.getName().getOrDefault(p.getSummaryLanguage(), def.getName().get("en")));
        if (isStaff) prodMap.put("definitionHash", p.getDefinitionHash());

        Map<String, Object> termsMap = new LinkedHashMap<>();
        termsMap.put("sumInsuredMinor", p.getSumInsuredMinor());
        termsMap.put("currency", p.getCurrency());
        termsMap.put("waitingPeriodDays", p.getWaitingPeriodDays());
        if (p.getCoverageStart() != null) termsMap.put("coverageStart", p.getCoverageStart().toString());
        if (p.getCoverageEnd() != null) termsMap.put("coverageEnd", p.getCoverageEnd().toString());
        if (def != null) termsMap.put("payoutTiers", def.getTrigger().getTiers());

        PolicyResponse.PolicyResponseBuilder b = PolicyResponse.builder()
                .policyId(p.getPolicyId())
                .policyNumber(p.getPolicyNumber())
                .status(p.getStatus().name())
                .plotId(p.getPlotId())
                .farmerId(actor.hasRole(AuthRole.FINANCE) && !isOwner ? null : p.getFarmerId().toString())
                .product(prodMap)
                .terms(termsMap)
                .version(p.getVersion())
                .createdAt(p.getCreatedAt().toString());

        if (inc.contains("summary")) {
            b.summary(Map.of("language", p.getSummaryLanguage(), "text", p.getSummaryText(), "termsVersion", p.getTermsVersion()));
        }
        if (inc.contains("premium") && !isDelegated) {
            Map<String, Object> prem = new LinkedHashMap<>();
            prem.put("totalPayableMinor", p.getTotalPayableMinor());
            prem.put("subsidyMinor", p.getSubsidyMinor());
            prem.put("status", p.getPremiumStatus());
            if (p.getPaidAt() != null) prem.put("paidAt", p.getPaidAt().toString());
            if (p.getPayBy() != null) prem.put("payBy", p.getPayBy().toString());
            b.premium(prem);
        }
        if (inc.contains("payout") && !isDelegated) {
            b.payout(Map.of(
                    "state", p.getPayoutState().name(),
                    "paidToDateMinor", p.getPaidToDateMinor(),
                    "remainingMinor", Math.max(0L, p.getSumInsuredMinor() - p.getPaidToDateMinor())));
        }
        if (p.getStatus() == PolicyStatus.ON_HOLD) {
            b.holdReasons(p.getHoldReasonList());
        }
        if (inc.contains("document") && p.getSummaryDocRef() != null) {
            b.documentUrl(clients.createSignedUrl(p.getSummaryDocRef()));
        }
        if (inc.contains("history") && actor.hasAnyRole(AuthRole.COMPLIANCE_OFFICER, AuthRole.ADMIN, AuthRole.SUPPORT)) {
            b.history(historyRepository.findByPolicyIdOrderByOccurredAtAsc(p.getPolicyId()).stream()
                    .map(h -> Map.<String, Object>of(
                            "fromStatus", Objects.toString(h.getFromStatus(), ""),
                            "toStatus", h.getToStatus(),
                            "actor", h.getActor(),
                            "reason", h.getReason(),
                            "timestamp", h.getOccurredAt().toString()))
                    .toList());
        }
        return b.build();
    }

    @Transactional
    public CancelPolicyResponse cancelPolicy(
            String policyId, CancelPolicyRequest request, String stepUpToken, String idempotencyKey) {
        PolicyIdentity actor = PolicySecurityContext.require();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<CancelPolicyResponse> replay = idempotencyService.findReplay(
                    idempotencyKey, "POL-06:" + policyId, request, CancelPolicyResponse.class);
            if (replay.isPresent()) return replay.get();
        }
        PolicyEntity policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new PolicyApiException(HttpStatus.NOT_FOUND, "POLICY_NOT_FOUND", "Policy not found"));

        boolean isStaff = actor.hasAnyRole(AuthRole.ADMIN, AuthRole.COMPLIANCE_OFFICER, AuthRole.SUPPORT);
        boolean isOwner = actor.userId() != null && actor.userId().equals(policy.getFarmerId());
        if (!isStaff && !isOwner) {
            throw new PolicyApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        if (actor.hasRole(AuthRole.AGENT) && !isStaff) {
            throw new PolicyApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Delegations cannot cancel policies");
        }
        if (policy.getStatus() == PolicyStatus.EXPIRED || policy.getStatus() == PolicyStatus.LAPSED
                || policy.getStatus() == PolicyStatus.CANCELLED) {
            throw new PolicyApiException(HttpStatus.CONFLICT, "POLICY_NOT_CANCELLABLE",
                    "This policy cannot be cancelled", Map.of("status", policy.getStatus().name()));
        }
        if (!isStaff && request.getReasonCode() != CancelReasonCode.FARMER_REQUEST) {
            throw new PolicyApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Staff-only cancellation reason");
        }
        if (isStaff && !isOwner && (request.getNote() == null || request.getNote().trim().length() < 20)) {
            throw new PolicyApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Staff justification note (20-500 chars) is required");
        }
        if (isOwner && "PAID".equals(policy.getPremiumStatus())) {
            if (!clients.consumeStepUp(stepUpToken, actor.userId(), StepUpActionClass.POLICY_CANCEL)) {
                throw new PolicyApiException(HttpStatus.UNAUTHORIZED, "STEP_UP_REQUIRED",
                        "Please confirm with a new code", Map.of("stepUp", true));
            }
        }
        boolean hasClaim = policy.getPayoutState() != PayoutState.NONE;
        if (hasClaim && (!isStaff || (request.getReasonCode() != CancelReasonCode.FRAUD
                && request.getReasonCode() != CancelReasonCode.COMPLIANCE_REQUIREMENT))) {
            throw new PolicyApiException(HttpStatus.CONFLICT, "CLAIM_IN_PROGRESS",
                    "This policy cannot be cancelled while a claim is in progress");
        }

        ProductVersionEntity pv = productRepository.findByProductIdAndVersionNum(
                policy.getProductId(), policy.getProductVersion()).orElseThrow();
        CreateProductRequest def = productService.readDefinition(pv.getDefinitionJson());
        Instant now = Instant.now();

        long refundMinor = 0;
        RefundBasis basis = RefundBasis.NONE;
        if ("PAID".equals(policy.getPremiumStatus())) {
            boolean beforeCoverStart = policy.getCoverageStart() == null || now.isBefore(policy.getCoverageStart());
            boolean inCoolingOff = policy.getPaidAt() != null
                    && ChronoUnit.DAYS.between(policy.getPaidAt(), now) <= def.getCancellation().getCoolingOffDays();
            if (beforeCoverStart && inCoolingOff) {
                basis = RefundBasis.FULL_LESS_FEE;
                refundMinor = Math.max(0L, policy.getTotalPayableMinor() - def.getCancellation().getAdminFeeMinor());
            } else if (isStaff && !isOwner && def.getCancellation().getRefundAfterCoverStart() == RefundBasis.PRO_RATA
                    && policy.getCoverageStart() != null && policy.getCoverageEnd() != null) {
                long totalSec = Math.max(1L, ChronoUnit.SECONDS.between(policy.getCoverageStart(), policy.getCoverageEnd()));
                long remSec = Math.max(0L, ChronoUnit.SECONDS.between(now, policy.getCoverageEnd()));
                basis = RefundBasis.PRO_RATA;
                refundMinor = Math.round(policy.getTotalPayableMinor() * ((double) remSec / totalSec));
            }
        }

        long subsidyReleased = 0;
        if (policy.getSubsidyMinor() > 0 && policy.getSubsidySchemeCode() != null && policy.getCoverageStart() == null) {
            subsidyRepository.releaseBudget(policy.getSubsidySchemeCode(), policy.getSubsidyMinor());
            subsidyReleased = policy.getSubsidyMinor();
        }

        String fromStatus = policy.getStatus().name();
        policy.setStatus(PolicyStatus.CANCELLED);
        policy.setUpdatedAt(now);
        policyRepository.save(policy);
        recordHistory(policyId, fromStatus, PolicyStatus.CANCELLED.name(), actor.actorId(),
                "Cancelled: " + request.getReasonCode(), now);

        outboxService.enqueue(properties.getKafka().getPolicyTopic(), "policy.cancelled", policyId, Map.ofEntries(
                Map.entry("eventType", "policy.cancelled"),
                Map.entry("policyId", policyId),
                Map.entry("policyNumber", policy.getPolicyNumber()),
                Map.entry("farmerId", policy.getFarmerId().toString()),
                Map.entry("plotId", policy.getPlotId()),
                Map.entry("reasonCode", request.getReasonCode().name()),
                Map.entry("cancelledBy", actor.actorId()),
                Map.entry("refundMinor", refundMinor),
                Map.entry("refundBasis", basis.name()),
                Map.entry("subsidyReleasedMinor", subsidyReleased),
                Map.entry("coverEndedAt", now.toString()),
                Map.entry("timestamp", now.toString())));

        CancelPolicyResponse resp = CancelPolicyResponse.builder()
                .policyId(policyId)
                .status(PolicyStatus.CANCELLED.name())
                .cancelledAt(now.toString())
                .reasonCode(request.getReasonCode().name())
                .refund(Map.of(
                        "amountMinor", refundMinor,
                        "currency", policy.getCurrency(),
                        "basis", basis.name(),
                        "status", refundMinor > 0 ? "PENDING" : "NONE"))
                .subsidyReleasedMinor(subsidyReleased)
                .coverEndedAt(policy.getCoverageStart() != null ? now.toString() : null)
                .message(refundMinor > 0
                        ? "Policy cancelled. Refund will be sent to the original payer."
                        : "Policy cancelled with no refund due.")
                .build();
        idempotencyService.save(idempotencyKey, "POL-06:" + policyId, request, 200, resp);
        return resp;
    }

    public RenewalsResponse listUpcomingRenewals(
            Integer daysAhead, String farmerIdParam, String regionCode, String cropCode, String statusFilter,
            int page, int size, String delegationHeader) {
        PolicyIdentity actor = PolicySecurityContext.require();
        int days = daysAhead == null ? 45 : daysAhead;
        if (days < 1 || days > 120 || size < 1 || size > 100 || page < 0) {
            throw new PolicyApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid paging or daysAhead");
        }
        boolean isStaff = actor.hasAnyRole(AuthRole.SUPPORT, AuthRole.UNDERWRITER, AuthRole.ADMIN);
        UUID filterFarmer = null;
        if (farmerIdParam != null && !farmerIdParam.isBlank()) {
            filterFarmer = UUID.fromString(farmerIdParam.trim());
            authorizeAgentOrSelf(actor, filterFarmer, delegationHeader, true);
        } else if (!isStaff) {
            if (actor.userId() == null) {
                throw new PolicyApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
            }
            filterFarmer = actor.userId();
        }

        Instant now = Instant.now();
        Instant cutoff = now.plus(days, ChronoUnit.DAYS);
        List<PolicyEntity> candidates = policyRepository.findByStatusInAndCoverageEndBetween(
                List.of(PolicyStatus.ACTIVE, PolicyStatus.EXPIRED), now.minus(30, ChronoUnit.DAYS), cutoff);

        List<Map<String, Object>> allItems = new ArrayList<>();
        for (PolicyEntity p : candidates) {
            if (filterFarmer != null && !p.getFarmerId().equals(filterFarmer)) continue;
            if (regionCode != null && !regionCode.equals(p.getRegionCode())) continue;
            if (cropCode != null && !cropCode.equals(p.getCropCode())) continue;

            long indicative = Math.round(p.getTotalPayableMinor() * 1.05);
            long diff = indicative - p.getTotalPayableMinor();
            double pct = Math.round((diff * 1000.0) / Math.max(1L, p.getTotalPayableMinor())) / 10.0;
            List<String> blockers = new ArrayList<>();
            PolicyExternalClients.PlotDetails plot = clients.getPlotDetails(p.getPlotId());
            if (!plot.activeSeason()) blockers.add("SEASON_NOT_SET");
            PolicyExternalClients.FarmerContext farmer = clients.getFarmerContext(p.getFarmerId());
            if (!farmer.walletUsable()) blockers.add("PAYOUT_DESTINATION_REQUIRED");

            if ("ELIGIBLE".equalsIgnoreCase(statusFilter) && !blockers.isEmpty()) continue;
            if ("BLOCKED".equalsIgnoreCase(statusFilter) && blockers.isEmpty()) continue;

            allItems.add(Map.ofEntries(
                    Map.entry("policyId", p.getPolicyId()),
                    Map.entry("policyNumber", p.getPolicyNumber()),
                    Map.entry("plotId", p.getPlotId()),
                    Map.entry("productId", p.getProductId()),
                    Map.entry("coverageEnd", p.getCoverageEnd().toString()),
                    Map.entry("indicativePremiumMinor", indicative),
                    Map.entry("changeFromLastMinor", diff),
                    Map.entry("changePercent", pct),
                    Map.entry("priceCapApplied", false),
                    Map.entry("reasons", List.of("Rainfall risk updated for the upcoming season.")),
                    Map.entry("blockers", blockers)));
        }
        int start = Math.min(page * size, allItems.size());
        int end = Math.min(start + size, allItems.size());
        return RenewalsResponse.builder()
                .items(allItems.subList(start, end))
                .asOf(now.toString())
                .page(page)
                .size(size)
                .totalElements((long) allItems.size())
                .totalPages((int) Math.ceil((double) allItems.size() / size))
                .build();
    }

    @Transactional
    public void onPremiumPaid(String policyId, Instant paidAt, boolean activationThresholdMet) {
        policyRepository.findById(policyId).ifPresent(p -> {
            if (!activationThresholdMet || "PAID".equals(p.getPremiumStatus())) return;
            String from = p.getStatus().name();
            p.setPremiumStatus("PAID");
            p.setPaidAt(paidAt != null ? paidAt : Instant.now());
            p.setWaitingEndsAt(p.getPaidAt().plus(p.getWaitingPeriodDays(), ChronoUnit.DAYS));
            if (p.getStatus() == PolicyStatus.PENDING_PAYMENT) {
                p.setStatus(PolicyStatus.PAID_WAITING);
            }
            p.setUpdatedAt(Instant.now());
            policyRepository.save(p);
            recordHistory(policyId, from, p.getStatus().name(), "billing-service", "Premium confirmed", p.getUpdatedAt());
        });
    }

    @Transactional
    public void onClaimOpened(String policyId) {
        policyRepository.findById(policyId).ifPresent(p -> {
            if (p.getPayoutState() == PayoutState.NONE) {
                p.setPayoutState(PayoutState.CLAIM_OPEN);
                p.setUpdatedAt(Instant.now());
                policyRepository.save(p);
            }
        });
    }

    @Transactional
    public void onPayoutCompleted(String policyId, long amountMinor) {
        policyRepository.findById(policyId).ifPresent(p -> {
            long totalPaid = Math.min(p.getSumInsuredMinor(), p.getPaidToDateMinor() + amountMinor);
            p.setPaidToDateMinor(totalPaid);
            p.setPayoutState(totalPaid >= p.getSumInsuredMinor() ? PayoutState.PAID_FULL : PayoutState.PAID_PARTIAL);
            p.setUpdatedAt(Instant.now());
            policyRepository.save(p);
        });
    }

    @Transactional
    public void setRegionEnrollment(String regionCode, String status, String reason, String forecastId) {
        regionRepository.save(RegionEnrollmentStateEntity.builder()
                .regionCode(regionCode)
                .status(status)
                .reason(reason)
                .sourceForecastId(forecastId)
                .updatedAt(Instant.now())
                .build());
    }

    private ProductVersionEntity resolvePublishedProduct(String productId) {
        if (productId != null && !productId.isBlank()) {
            return productRepository.findByProductIdAndStatusIn(productId, List.of(ProductVersionStatus.PUBLISHED))
                    .stream().findFirst()
                    .orElseThrow(() -> new PolicyApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PRODUCT_NOT_ELIGIBLE",
                            "This product is not available for this plot"));
        }
        List<ProductVersionEntity> all = productRepository.findByStatus(ProductVersionStatus.PUBLISHED);
        if (all.isEmpty()) {
            throw new PolicyApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PRODUCT_NOT_ELIGIBLE",
                    "No published product available");
        }
        return all.getFirst();
    }

    private SubsidySchemeEntity selectSubsidy(String requestedCode, String regionCode, String cropCode) {
        if (requestedCode != null && !requestedCode.isBlank()) {
            return subsidyRepository.findById(requestedCode).filter(SubsidySchemeEntity::isActive).orElse(null);
        }
        return subsidyRepository.findByActiveTrue().stream().findFirst().orElse(null);
    }

    private long computeSumInsured(GenerateQuoteRequest req, CreateProductRequest def, double areaHa) {
        var si = def.getSumInsured();
        long raw;
        if (req.getSumInsuredMinor() != null) {
            raw = "PER_HECTARE".equals(si.getMode()) ? Math.round(req.getSumInsuredMinor() * areaHa) : req.getSumInsuredMinor();
        } else {
            long base = switch (req.getCoverageLevel()) {
                case BASIC -> si.getMinMinor();
                case STANDARD -> (si.getMinMinor() + si.getMaxMinor()) / 2;
                case PREMIUM -> si.getMaxMinor();
            };
            raw = "PER_HECTARE".equals(si.getMode()) ? Math.round(base * areaHa) : base;
        }
        long minTotal = "PER_HECTARE".equals(si.getMode()) ? Math.round(si.getMinMinor() * areaHa) : si.getMinMinor();
        long maxTotal = "PER_HECTARE".equals(si.getMode()) ? Math.round(si.getMaxMinor() * areaHa) : si.getMaxMinor();
        if (req.getSumInsuredMinor() != null && (req.getSumInsuredMinor() < si.getMinMinor()
                || req.getSumInsuredMinor() > si.getMaxMinor())) {
            throw new PolicyApiException(HttpStatus.UNPROCESSABLE_ENTITY, "SUM_INSURED_INVALID",
                    "Choose a different sum insured", Map.of("min", minTotal, "max", maxTotal));
        }
        return Math.max(minTotal, Math.min(maxTotal, raw));
    }

    private Map<String, Object> estimateWindow(PolicyExternalClients.PlotDetails plot, CreateProductRequest def) {
        LocalDate anchor = plot.plantingDate() != null ? plot.plantingDate() : LocalDate.now();
        return Map.of(
                "start", anchor.plusDays(def.getCoverageWindow().getStartOffsetDays()).toString(),
                "end", anchor.plusDays(def.getCoverageWindow().getEndOffsetDays()).toString());
    }

    private String buildPlainSummary(CreateProductRequest def, QuoteEntity q, String lang) {
        String name = def.getName().getOrDefault(lang, def.getName().getOrDefault("en", q.getProductId()));
        if ("sw".equalsIgnoreCase(lang)) {
            return "Bima ya " + name + ": ulinzi hadi " + q.getSumInsuredMinor() + " " + q.getCurrency()
                    + ". Kipindi cha kusubiri siku " + q.getWaitingPeriodDays() + ".";
        }
        return name + ": Sum insured " + q.getSumInsuredMinor() + " " + q.getCurrency()
                + ", waiting period " + q.getWaitingPeriodDays() + " days.";
    }

    private void recordHistory(String policyId, String from, String to, String actor, String reason, Instant at) {
        historyRepository.save(PolicyHistoryEntity.builder()
                .historyId(UUID.randomUUID())
                .policyId(policyId)
                .fromStatus(from)
                .toStatus(to)
                .actor(actor)
                .reason(reason)
                .occurredAt(at)
                .build());
    }

    private QuoteResponse toQuoteResponse(QuoteEntity q, CreateProductRequest def) {
        return QuoteResponse.builder()
                .quoteId(q.getQuoteId())
                .productId(q.getProductId())
                .productVersion(q.getProductVersion())
                .sumInsuredMinor(q.getSumInsuredMinor())
                .currency(q.getCurrency())
                .premium(readMap(q.getPriceBreakdownJson()))
                .factors(readList(q.getFactorsJson()))
                .payoutTiers(readList(q.getPayoutTiersJson()))
                .waitingPeriodDays(q.getWaitingPeriodDays())
                .scoreSource(q.getScoreSource().name())
                .priceValidUntil(q.getValidUntil().toString())
                .payBy(q.getPayBy().toString())
                .build();
    }

    private UUID resolveFarmerId(String param, PolicyIdentity actor, PolicyExternalClients.PlotDetails plot) {
        if (param != null && !param.isBlank()) return UUID.fromString(param.trim());
        if (plot.farmerId() != null && !plot.farmerId().isBlank()) return UUID.fromString(plot.farmerId());
        if (actor.userId() != null) return actor.userId();
        throw new PolicyApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "farmerId required");
    }

    private void authorizeAgentOrSelf(PolicyIdentity actor, UUID farmerId, String delegationHeader, boolean staffAllowed) {
        if (actor.hasRole(AuthRole.SUPPORT) || (staffAllowed && actor.hasAnyRole(AuthRole.UNDERWRITER, AuthRole.ADMIN))) return;
        if (actor.userId() != null && actor.userId().equals(farmerId)) return;
        if (actor.hasRole(AuthRole.AGENT) && isValidAgentDelegation(actor, farmerId, delegationHeader)) return;
        throw new PolicyApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
    }

    private boolean isValidAgentDelegation(PolicyIdentity actor, UUID farmerId, String delegationHeader) {
        if (delegationHeader == null || delegationHeader.isBlank() || actor.userId() == null) return false;
        try {
            return clients.validateDelegation(UUID.fromString(delegationHeader.trim()), actor.userId(), farmerId, DelegationScope.POLICY_ASSIST);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String writeJson(Object o) {
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return "{}"; }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String json) {
        try { return objectMapper.readValue(json, Map.class); } catch (Exception e) { return Map.of(); }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readList(String json) {
        try { return objectMapper.readValue(json, List.class); } catch (Exception e) { return List.of(); }
    }
}
