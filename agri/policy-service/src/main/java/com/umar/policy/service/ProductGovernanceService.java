package com.umar.policy.service;

import com.umar.enums.auth.AuthRole;
import com.umar.enums.auth.DelegationScope;
import com.umar.enums.policy.ProductDecisionType;
import com.umar.enums.policy.ProductVersionStatus;
import com.umar.policy.client.PolicyExternalClients;
import com.umar.policy.config.PolicyProperties;
import com.umar.policy.entity.ProductApprovalEntity;
import com.umar.policy.entity.ProductVersionEntity;
import com.umar.policy.entity.RegionEnrollmentStateEntity;
import com.umar.policy.exception.PolicyApiException;
import com.umar.policy.repository.ProductApprovalRepository;
import com.umar.policy.repository.ProductVersionRepository;
import com.umar.policy.repository.RegionEnrollmentStateRepository;
import com.umar.policy.security.PolicyIdentity;
import com.umar.policy.security.PolicySecurityContext;
import com.umar.payload.request.policy.CreateProductRequest;
import com.umar.payload.request.policy.ProductDecisionRequest;
import com.umar.payload.response.policy.EligibleProductsResponse;
import com.umar.payload.response.policy.ProductVersionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductGovernanceService {

    private final ProductVersionRepository productRepository;
    private final ProductApprovalRepository approvalRepository;
    private final RegionEnrollmentStateRepository regionRepository;
    private final PolicyExternalClients clients;
    private final PolicyOutboxService outboxService;
    private final PolicyRateLimitService rateLimitService;
    private final PolicyProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public ProductVersionResponse createProductVersion(CreateProductRequest request) {
        PolicyIdentity actor = PolicySecurityContext.require();
        if (!actor.hasAnyRole(AuthRole.UNDERWRITER, AuthRole.ADMIN)) {
            throw new PolicyApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        rateLimitService.check("pol01:" + actor.actorId(), 30, 3600);

        validateProductSemantics(request);

        String definitionJson = writeJson(request);
        String defHash = sha256(definitionJson);

        Optional<ProductVersionEntity> idempMatch = productRepository.findByProductCodeAndDefinitionHash(
                request.getProductCode(), defHash);
        if (idempMatch.isPresent()) {
            return toResponse(idempMatch.get());
        }

        String productId;
        int nextVersion;
        List<Map<String, Object>> diff = List.of();

        if (request.getBaseProductId() != null && !request.getBaseProductId().isBlank()) {
            ProductVersionEntity base = productRepository.findFirstByProductIdOrderByVersionNumDesc(request.getBaseProductId())
                    .orElseThrow(() -> new PolicyApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found"));
            List<ProductVersionEntity> openDrafts = productRepository.findByProductIdAndStatusIn(
                    base.getProductId(), List.of(ProductVersionStatus.DRAFT, ProductVersionStatus.IN_APPROVAL));
            if (!openDrafts.isEmpty()) {
                throw new PolicyApiException(HttpStatus.CONFLICT, "DRAFT_EXISTS",
                        "Finish or withdraw the open draft first",
                        Map.of("version", openDrafts.getFirst().getVersionNum()));
            }
            productId = base.getProductId();
            nextVersion = base.getVersionNum() + 1;
            diff = computeDiff(base, request);
        } else {
            if (productRepository.findFirstByProductCodeOrderByVersionNumDesc(request.getProductCode()).isPresent()) {
                throw new PolicyApiException(HttpStatus.CONFLICT, "PRODUCT_CODE_EXISTS",
                        "This product code is already in use");
            }
            productId = "prd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            nextVersion = 1;
        }

        clients.validateCustomAttributes(request.getAttributes());

        boolean submit = Boolean.TRUE.equals(request.getSubmitForApproval());
        Map<String, Object> backtestMap = null;
        if (submit) {
            if (request.getBacktestRef() == null || request.getBacktestRef().isBlank()) {
                throw new PolicyApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BACKTEST_REQUIRED",
                        "A back-test is required to submit");
            }
            PolicyExternalClients.BacktestResult bt = clients.getBacktest(request.getBacktestRef());
            if (!bt.found() || bt.seasons() < properties.getRules().getBacktestMinSeasons()) {
                throw new PolicyApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BACKTEST_REQUIRED",
                        "A back-test covering at least 10 seasons is required");
            }
            if (bt.expectedLossRatio() < properties.getRules().getLossRatioMin()
                    || bt.expectedLossRatio() > properties.getRules().getLossRatioMax()) {
                throw new PolicyApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BACKTEST_OUT_OF_BAND",
                        "Back-test result is outside the allowed range",
                        Map.of("expectedLossRatio", bt.expectedLossRatio()));
            }
            backtestMap = Map.of(
                    "seasons", bt.seasons(),
                    "expectedPayoutFrequency", bt.expectedPayoutFrequency(),
                    "expectedLossRatio", bt.expectedLossRatio());
        } else if (request.getBacktestRef() != null && !request.getBacktestRef().isBlank()) {
            PolicyExternalClients.BacktestResult bt = clients.getBacktest(request.getBacktestRef());
            if (bt.found()) {
                backtestMap = Map.of(
                        "seasons", bt.seasons(),
                        "expectedPayoutFrequency", bt.expectedPayoutFrequency(),
                        "expectedLossRatio", bt.expectedLossRatio());
            }
        }

        ProductVersionStatus initialStatus = submit ? ProductVersionStatus.IN_APPROVAL : ProductVersionStatus.DRAFT;
        ProductVersionEntity entity = ProductVersionEntity.builder()
                .id(productId + "_v" + nextVersion)
                .productId(productId)
                .productCode(request.getProductCode())
                .versionNum(nextVersion)
                .status(initialStatus)
                .peril(request.getPeril())
                .definitionJson(definitionJson)
                .definitionHash(defHash)
                .diffJson(writeJson(diff))
                .backtestRef(request.getBacktestRef())
                .backtestJson(backtestMap != null ? writeJson(backtestMap) : null)
                .authorId(actor.actorId())
                .effectiveFrom(request.getEffectiveFrom())
                .createdAt(Instant.now())
                .build();
        productRepository.save(entity);
        return toResponse(entity);
    }

    @Transactional
    public ProductVersionResponse decideProductVersion(String productId, int version, ProductDecisionRequest request) {
        PolicyIdentity actor = PolicySecurityContext.require();
        ProductVersionEntity pv = productRepository.findByProductIdAndVersionNum(productId, version)
                .orElseThrow(() -> new PolicyApiException(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND",
                        "Product version not found"));

        if (request.getDecision() == ProductDecisionType.WITHDRAW) {
            if (!actor.hasRole(AuthRole.ADMIN)) {
                throw new PolicyApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
            }
            if (pv.getStatus() != ProductVersionStatus.PUBLISHED) {
                throw new PolicyApiException(HttpStatus.CONFLICT, "VERSION_NOT_IN_APPROVAL",
                        "Only published versions can be withdrawn");
            }
            pv.setStatus(ProductVersionStatus.RETIRED);
            pv.setRetiredAt(Instant.now());
            productRepository.save(pv);
            CreateProductRequest def = readDefinition(pv.getDefinitionJson());
            outboxService.enqueue(properties.getKafka().getPolicyTopic(), "product.retired", productId, Map.of(
                    "eventType", "product.retired",
                    "productId", productId,
                    "version", version,
                    "retiredAt", pv.getRetiredAt().toString(),
                    "timestamp", Instant.now().toString()));
            return toResponse(pv);
        }

        if (!actor.hasAnyRole(AuthRole.UNDERWRITER, AuthRole.COMPLIANCE_OFFICER)) {
            throw new PolicyApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        AuthRole actingRole = actor.hasRole(AuthRole.COMPLIANCE_OFFICER)
                ? AuthRole.COMPLIANCE_OFFICER : AuthRole.UNDERWRITER;

        if (pv.getStatus() != ProductVersionStatus.IN_APPROVAL) {
            throw new PolicyApiException(HttpStatus.CONFLICT, "VERSION_NOT_IN_APPROVAL",
                    "This version is not awaiting approval");
        }
        if (actor.actorId().equals(pv.getAuthorId())) {
            throw new PolicyApiException(HttpStatus.FORBIDDEN, "AUTHOR_CANNOT_APPROVE",
                    "You cannot approve your own work");
        }
        if (approvalRepository.findByProductIdAndVersionNumAndApproverRole(productId, version, actingRole).isPresent()) {
            throw new PolicyApiException(HttpStatus.CONFLICT, "ALREADY_DECIDED_BY_ROLE",
                    "Your approval is already recorded");
        }

        String recomputedHash = sha256(pv.getDefinitionJson());
        if (!recomputedHash.equals(pv.getDefinitionHash())) {
            throw new PolicyApiException(HttpStatus.CONFLICT, "DEFINITION_CHANGED",
                    "The definition changed. Please review again");
        }

        if (request.getDecision() == ProductDecisionType.APPROVE) {
            validateChecklist(actingRole, request.getChecklist());
        }

        approvalRepository.save(ProductApprovalEntity.builder()
                .approvalId(UUID.randomUUID())
                .productId(productId)
                .versionNum(version)
                .approverId(actor.actorId())
                .approverRole(actingRole)
                .decision(request.getDecision())
                .definitionHash(pv.getDefinitionHash())
                .note(request.getNote())
                .decidedAt(Instant.now())
                .build());

        if (request.getDecision() == ProductDecisionType.REJECT) {
            pv.setStatus(ProductVersionStatus.REJECTED);
        } else if (request.getDecision() == ProductDecisionType.REQUEST_CHANGES) {
            pv.setStatus(ProductVersionStatus.DRAFT);
        } else {
            List<ProductApprovalEntity> all = approvalRepository.findByProductIdAndVersionNum(productId, version);
            boolean hasUnderwriter = all.stream().anyMatch(a -> a.getApproverRole() == AuthRole.UNDERWRITER && a.getDecision() == ProductDecisionType.APPROVE);
            boolean hasCompliance = all.stream().anyMatch(a -> a.getApproverRole() == AuthRole.COMPLIANCE_OFFICER && a.getDecision() == ProductDecisionType.APPROVE);
            boolean distinctActors = all.stream().map(ProductApprovalEntity::getApproverId).distinct().count() >= 2;
            if (hasUnderwriter && hasCompliance && distinctActors) {
                if (!pv.getEffectiveFrom().isAfter(LocalDate.now())) {
                    publishVersion(pv);
                } else {
                    pv.setStatus(ProductVersionStatus.APPROVED);
                }
            }
        }
        productRepository.save(pv);
        return toResponse(pv);
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void publishDueVersions() {
        LocalDate today = LocalDate.now();
        for (ProductVersionEntity pv : productRepository.findByStatus(ProductVersionStatus.APPROVED)) {
            if (!pv.getEffectiveFrom().isAfter(today)) {
                publishVersion(pv);
                productRepository.save(pv);
            }
        }
    }

    public EligibleProductsResponse listEligibleProducts(String plotId, String farmerIdParam, String language, String delegationHeader) {
        PolicyIdentity actor = PolicySecurityContext.require();
        PolicyExternalClients.PlotDetails plot = clients.getPlotDetails(plotId);
        if (!plot.available()) {
            throw new PolicyApiException(HttpStatus.SERVICE_UNAVAILABLE, "PLOT_SERVICE_UNAVAILABLE",
                    "Plot data could not be read");
        }
        if (!plot.found()) {
            throw new PolicyApiException(HttpStatus.NOT_FOUND, "PLOT_NOT_FOUND", "Plot not found");
        }
        UUID subjectFarmerId = parseFarmerId(farmerIdParam, actor, plot);
        authorizePlotAccess(actor, subjectFarmerId, delegationHeader);

        String lang = language != null && !language.isBlank() ? language : "en";
        List<ProductVersionEntity> published = productRepository.findByStatus(ProductVersionStatus.PUBLISHED);
        boolean regionClosed = regionRepository.findById(plot.regionCode())
                .map(r -> "CLOSED".equals(r.getStatus())).orElse(false);

        List<Map<String, Object>> items = new ArrayList<>();
        String nextAction = "NONE";
        LocalDate today = LocalDate.now();

        for (ProductVersionEntity pv : published) {
            CreateProductRequest def = readDefinition(pv.getDefinitionJson());
            List<String> reasons = new ArrayList<>();
            String itemStatus;

            if (!"VALIDATED".equals(plot.status())) reasons.add("PLOT_UNDER_REVIEW");
            if (!plot.activeSeason()) reasons.add("SEASON_NOT_SET");
            if (!def.getEligibility().getCropCodes().contains(plot.cropCode())) reasons.add("CROP_NOT_COVERED");
            if (!def.getEligibility().getRegionCodes().contains(plot.regionCode())) reasons.add("REGION_NOT_COVERED");
            if (plot.areaHa() < def.getEligibility().getMinAreaHa() || plot.areaHa() > def.getEligibility().getMaxAreaHa()) {
                reasons.add("AREA_OUTSIDE_RANGE");
            }

            if (regionClosed) {
                itemStatus = "CLOSED_FOR_REGION";
                reasons.add("REGION_CLOSED");
            } else if (def.getEnrollment().getOpensOn().isAfter(today)) {
                itemStatus = "OPENS_LATER";
            } else if (def.getEnrollment().getClosesOn().isBefore(today)) {
                itemStatus = "NOT_ELIGIBLE";
                reasons.add("ENROLLMENT_CLOSED");
            } else if (!reasons.isEmpty()) {
                itemStatus = reasons.contains("SEASON_NOT_SET") || reasons.contains("PLOT_UNDER_REVIEW")
                        ? "NOT_YET" : "NOT_ELIGIBLE";
                if (reasons.contains("SEASON_NOT_SET") && "NONE".equals(nextAction)) nextAction = "RECORD_SEASON";
            } else {
                itemStatus = "ELIGIBLE";
                nextAction = "REQUEST_QUOTE";
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productId", pv.getProductId());
            item.put("productVersion", pv.getVersionNum());
            item.put("name", def.getName().getOrDefault(lang, def.getName().getOrDefault("en", pv.getProductCode())));
            item.put("peril", pv.getPeril().name());
            item.put("status", itemStatus);
            item.put("waitingPeriodDays", def.getWaitingPeriodDays());
            item.put("currency", def.getSumInsured().getCurrency());
            if ("ELIGIBLE".equals(itemStatus)) {
                LocalDate start = (plot.plantingDate() != null ? plot.plantingDate() : today)
                        .plusDays(def.getCoverageWindow().getStartOffsetDays());
                LocalDate end = (plot.plantingDate() != null ? plot.plantingDate() : today)
                        .plusDays(def.getCoverageWindow().getEndOffsetDays());
                item.put("coverPeriodEstimate", Map.of("start", start.toString(), "end", end.toString()));
                item.put("indicativeRangePerHaMinor", Map.of("min", 150000, "max", 210000));
            } else if (!reasons.isEmpty()) {
                item.put("reasons", reasons);
            }
            items.add(item);
        }

        return EligibleProductsResponse.builder()
                .items(items)
                .nextAction(nextAction)
                .asOf(Instant.now().toString())
                .build();
    }

    public CreateProductRequest readDefinition(String json) {
        try {
            return objectMapper.readValue(json, CreateProductRequest.class);
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt product definition JSON", e);
        }
    }

    private void publishVersion(ProductVersionEntity pv) {
        pv.setStatus(ProductVersionStatus.PUBLISHED);
        pv.setPublishedAt(Instant.now());
        CreateProductRequest def = readDefinition(pv.getDefinitionJson());
        outboxService.enqueue(properties.getKafka().getPolicyTopic(), "product.published", pv.getProductId(), Map.of(
                "eventType", "product.published",
                "productId", pv.getProductId(),
                "version", pv.getVersionNum(),
                "definitionHash", pv.getDefinitionHash(),
                "effectiveFrom", pv.getEffectiveFrom().toString(),
                "regionCodes", def.getEnrollment().getRegionCodes(),
                "cropCodes", def.getEligibility().getCropCodes(),
                "peril", pv.getPeril().name(),
                "timestamp", Instant.now().toString()));
    }

    private void validateProductSemantics(CreateProductRequest req) {
        double weightSum = req.getIndex().getPhases().stream()
                .mapToDouble(CreateProductRequest.PhaseDefinition::getWeight).sum();
        if (Math.abs(weightSum - 1.0) > 0.001) {
            throw new PolicyApiException(HttpStatus.BAD_REQUEST, "INVALID_WINDOW",
                    "The coverage window or phase weights are not valid");
        }
        var cw = req.getCoverageWindow();
        if (cw.getStartOffsetDays() >= cw.getEndOffsetDays() || cw.getMinDays() > cw.getMaxDays()) {
            throw new PolicyApiException(HttpStatus.BAD_REQUEST, "INVALID_WINDOW",
                    "The coverage window is not valid");
        }
        var tiers = req.getTrigger().getTiers();
        for (int i = 1; i < tiers.size(); i++) {
            if (tiers.get(i).getPayoutPercent() <= tiers.get(i - 1).getPayoutPercent()) {
                throw new PolicyApiException(HttpStatus.BAD_REQUEST, "INVALID_TIER_STRUCTURE",
                        "Check the payout tiers", Map.of("tiers", List.of(i + 1)));
            }
            String op = tiers.get(i).getOperator();
            boolean strictlyWorse = op.startsWith("LT")
                    ? tiers.get(i).getThresholdValue() < tiers.get(i - 1).getThresholdValue()
                    : tiers.get(i).getThresholdValue() > tiers.get(i - 1).getThresholdValue();
            if (!strictlyWorse) {
                throw new PolicyApiException(HttpStatus.BAD_REQUEST, "INVALID_TIER_STRUCTURE",
                        "Tier thresholds must be strictly rising in severity", Map.of("tiers", List.of(i + 1)));
            }
        }
        var si = req.getSumInsured();
        if (si.getMinMinor() > si.getMaxMinor() || (si.getMaxMinor() - si.getMinMinor()) % si.getStepMinor() != 0) {
            throw new PolicyApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Invalid sum insured bounds or step");
        }
    }

    private void validateChecklist(AuthRole role, Map<String, Boolean> checklist) {
        if (checklist == null) {
            throw new PolicyApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Checklist required for approval");
        }
        List<String> keys = role == AuthRole.UNDERWRITER
                ? List.of("backtestReviewed", "thresholdsReviewed", "pricingReviewed")
                : List.of("wordingReviewed", "refundRulesReviewed", "consentsReviewed");
        for (String k : keys) {
            if (!Boolean.TRUE.equals(checklist.get(k))) {
                throw new PolicyApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Checklist item required: " + k);
            }
        }
    }

    private List<Map<String, Object>> computeDiff(ProductVersionEntity base, CreateProductRequest next) {
        CreateProductRequest prev = readDefinition(base.getDefinitionJson());
        List<Map<String, Object>> diff = new ArrayList<>();
        if (!Objects.equals(prev.getWaitingPeriodDays(), next.getWaitingPeriodDays())) {
            diff.add(Map.of("field", "waitingPeriodDays", "from", prev.getWaitingPeriodDays(), "to", next.getWaitingPeriodDays()));
        }
        if (!Objects.equals(prev.getTrigger().getTiers().size(), next.getTrigger().getTiers().size())) {
            diff.add(Map.of("field", "trigger.tiers.size", "from", prev.getTrigger().getTiers().size(), "to", next.getTrigger().getTiers().size()));
        }
        return diff;
    }

    private ProductVersionResponse toResponse(ProductVersionEntity pv) {
        List<ProductApprovalEntity> approvals = approvalRepository.findByProductIdAndVersionNum(
                pv.getProductId(), pv.getVersionNum());
        List<String> required = new ArrayList<>();
        if (pv.getStatus() == ProductVersionStatus.IN_APPROVAL || pv.getStatus() == ProductVersionStatus.DRAFT) {
            if (approvals.stream().noneMatch(a -> a.getApproverRole() == AuthRole.UNDERWRITER && a.getDecision() == ProductDecisionType.APPROVE)) {
                required.add(AuthRole.UNDERWRITER.name());
            }
            if (approvals.stream().noneMatch(a -> a.getApproverRole() == AuthRole.COMPLIANCE_OFFICER && a.getDecision() == ProductDecisionType.APPROVE)) {
                required.add(AuthRole.COMPLIANCE_OFFICER.name());
            }
        }
        Map<String, Object> btMap = pv.getBacktestJson() != null ? readMap(pv.getBacktestJson()) : null;
        List<Map<String, Object>> diffList = pv.getDiffJson() != null ? readList(pv.getDiffJson()) : null;
        List<Map<String, Object>> approvalViews = approvals.stream()
                .map(a -> Map.<String, Object>of(
                        "role", a.getApproverRole().name(),
                        "decision", a.getDecision().name(),
                        "timestamp", a.getDecidedAt().toString()))
                .toList();

        return ProductVersionResponse.builder()
                .productId(pv.getProductId())
                .productCode(pv.getProductCode())
                .version(pv.getVersionNum())
                .status(pv.getStatus().name())
                .definitionHash(pv.getDefinitionHash())
                .diffSummary(diffList)
                .backtest(btMap)
                .approvalsRequired(required.isEmpty() ? null : required)
                .approvals(approvalViews.isEmpty() ? null : approvalViews)
                .publishesAt(pv.getStatus() == ProductVersionStatus.APPROVED ? pv.getEffectiveFrom().toString() : null)
                .effectiveFrom(pv.getEffectiveFrom().toString())
                .build();
    }

    private UUID parseFarmerId(String farmerIdParam, PolicyIdentity actor, PolicyExternalClients.PlotDetails plot) {
        if (farmerIdParam != null && !farmerIdParam.isBlank()) {
            return UUID.fromString(farmerIdParam.trim());
        }
        if (plot.farmerId() != null && !plot.farmerId().isBlank()) {
            return UUID.fromString(plot.farmerId());
        }
        if (actor.userId() != null) return actor.userId();
        throw new PolicyApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "farmerId is required");
    }

    private void authorizePlotAccess(PolicyIdentity actor, UUID farmerId, String delegationHeader) {
        if (actor.hasAnyRole(AuthRole.SUPPORT, AuthRole.UNDERWRITER, AuthRole.ADMIN)) return;
        if (actor.userId() != null && actor.userId().equals(farmerId)) return;
        if (actor.hasRole(AuthRole.AGENT) && delegationHeader != null && actor.userId() != null) {
            try {
                if (clients.validateDelegation(UUID.fromString(delegationHeader.trim()), actor.userId(), farmerId, DelegationScope.POLICY_ASSIST)) {
                    return;
                }
            } catch (IllegalArgumentException ignored) {}
        }
        throw new PolicyApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
    }

    private String writeJson(Object o) {
        try { return objectMapper.writeValueAsString(o); }
        catch (Exception e) { return "{}"; }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String json) {
        try { return objectMapper.readValue(json, Map.class); }
        catch (Exception e) { return Map.of(); }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readList(String json) {
        try { return objectMapper.readValue(json, List.class); }
        catch (Exception e) { return List.of(); }
    }

    public String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "hash_err";
        }
    }
}
