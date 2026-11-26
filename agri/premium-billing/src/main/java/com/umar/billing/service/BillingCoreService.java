package com.umar.billing.service;

import com.umar.billing.client.BillingExternalClients;
import com.umar.billing.config.BillingProperties;
import com.umar.billing.entity.*;
import com.umar.billing.exception.BillingApiException;
import com.umar.billing.repository.*;
import com.umar.billing.security.BillingIdentity;
import com.umar.billing.security.BillingSecurityContext;
import com.umar.enums.auth.AuthRole;
import com.umar.enums.auth.DelegationScope;
import com.umar.enums.billing.*;
import com.umar.enums.policy.PayerType;
import com.umar.payload.request.billing.*;
import com.umar.payload.response.billing.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BillingCoreService {

    private final PolicyBillingFactRepository factRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository lineRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentRepository paymentRepository;
    private final InstalmentPlanRepository planRepository;
    private final InstalmentScheduleRepository scheduleRepository;
    private final RefundRepository refundRepository;
    private final SponsorAccountRepository sponsorRepository;
    private final SponsorBatchRepository batchRepository;
    private final AgentFloatRepository floatRepository;
    private final CallbackInboxRepository inboxRepository;
    private final BillingApprovalRepository approvalRepository;
    private final JournalEntryRepository journalRepository;
    private final BillingExternalClients clients;
    private final BillingOutboxService outboxService;
    private final BillingIdempotencyService idempotencyService;
    private final BillingRateLimitService rateLimitService;
    private final BillingProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public InvoiceResponse createInvoice(CreateInvoiceRequest req, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BillingApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Idempotency-Key required");
        }
        BillingIdentity actor = BillingSecurityContext.require();
        boolean isInternal = actor.isServiceCaller();
        if (!isInternal && !actor.hasAnyRole(AuthRole.FINANCE, AuthRole.ADMIN)) {
            throw new BillingApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        if (!isInternal && (req.getReason() == null || req.getReason().trim().length() < 10)) {
            throw new BillingApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Reason (10-300 chars) required for manual invoice creation");
        }
        Optional<InvoiceResponse> replay = idempotencyService.findReplay(
                idempotencyKey, "BIL-01", req, InvoiceResponse.class);
        if (replay.isPresent()) return replay.get();

        long subsidyMinor = 0;
        Instant dueDate = req.getDueDate() != null ? req.getDueDate() : Instant.now().plus(7, ChronoUnit.DAYS);
        String farmerId = req.getPayerRef();

        if (req.getType() == InvoiceType.POLICY) {
            if (req.getPolicyId() == null || req.getPolicyId().isBlank()) {
                throw new BillingApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "policyId required");
            }
            Optional<InvoiceEntity> existingInv = invoiceRepository.findByPolicyId(req.getPolicyId());
            if (existingInv.isPresent()) {
                throw new BillingApiException(HttpStatus.CONFLICT, "INVOICE_EXISTS",
                        "An invoice already exists", Map.of("invoiceId", existingInv.get().getInvoiceId()));
            }
            PolicyBillingFactEntity fact = factRepository.findById(req.getPolicyId())
                    .orElseThrow(() -> new BillingApiException(HttpStatus.NOT_FOUND, "POLICY_NOT_KNOWN",
                            "Policy not found in billing"));
            if (Set.of("CANCELLED", "LAPSED", "EXPIRED").contains(fact.getStatus())) {
                throw new BillingApiException(HttpStatus.CONFLICT, "POLICY_NOT_PAYABLE",
                        "This policy can no longer be invoiced");
            }
            if (!Objects.equals(fact.getPremiumMinor(), req.getAmountMinor())
                    || !Objects.equals(fact.getCurrency(), req.getCurrency())) {
                throw new BillingApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AMOUNT_MISMATCH",
                        "Amount does not match the policy premium");
            }
            if (!Objects.equals(fact.getPayerType(), req.getPayerType().name())) {
                throw new BillingApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "payerType mismatch with policy facts");
            }
            subsidyMinor = fact.getSubsidyMinor();
            farmerId = fact.getFarmerId();
            if (req.getDueDate() == null && fact.getPayBy() != null) dueDate = fact.getPayBy();
        } else {
            if (req.getBatchId() == null || req.getBatchId().isBlank()) {
                throw new BillingApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "batchId required");
            }
        }

        String invoiceId = "inv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        List<InvoiceLineEntity> lines = new ArrayList<>();
        lines.add(InvoiceLineEntity.builder()
                .lineId(UUID.randomUUID())
                .invoiceId(invoiceId)
                .code(InvoiceLineCode.PREMIUM)
                .label("Premium")
                .amountMinor(req.getAmountMinor())
                .refundable(true)
                .build());

        long netExtra = 0;
        for (Map<String, Object> extra : clients.preInvoiceHook(invoiceId, req.getAmountMinor())) {
            InvoiceLineCode code = InvoiceLineCode.valueOf(String.valueOf(extra.get("code")));
            long amt = ((Number) extra.get("amountMinor")).longValue();
            if (code == InvoiceLineCode.FEE) {
                double pct = (amt * 100.0) / Math.max(1L, req.getAmountMinor());
                if (pct > properties.getRules().getFeeCapPercent()) {
                    throw new BillingApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FEE_OR_DISCOUNT_OUT_OF_CAP",
                            "Fees exceed allowed limits");
                }
                netExtra += Math.abs(amt);
                lines.add(InvoiceLineEntity.builder()
                        .lineId(UUID.randomUUID())
                        .invoiceId(invoiceId)
                        .code(code)
                        .label(String.valueOf(extra.getOrDefault("label", "Collection fee")))
                        .amountMinor(Math.abs(amt))
                        .refundable(Boolean.TRUE.equals(extra.get("refundable")))
                        .build());
            } else if (code == InvoiceLineCode.DISCOUNT_CREDIT) {
                double pct = (Math.abs(amt) * 100.0) / Math.max(1L, req.getAmountMinor());
                String funder = (String) extra.get("funder");
                if (pct > properties.getRules().getDiscountCapPercent() || funder == null || funder.isBlank()) {
                    throw new BillingApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FEE_OR_DISCOUNT_OUT_OF_CAP",
                            "Discounts exceed allowed limits or lack a funder");
                }
                netExtra -= Math.abs(amt);
                lines.add(InvoiceLineEntity.builder()
                        .lineId(UUID.randomUUID())
                        .invoiceId(invoiceId)
                        .code(code)
                        .label(String.valueOf(extra.getOrDefault("label", "Discount")))
                        .amountMinor(-Math.abs(amt))
                        .refundable(false)
                        .funder(funder)
                        .build());
            }
        }
        lineRepository.saveAll(lines);

        long totalMinor = Math.max(0L, req.getAmountMinor() + netExtra);
        double activationPct = 100.0;
        String planHash = null;
        if (req.getInstalmentPlanId() != null && !req.getInstalmentPlanId().isBlank()) {
            InstalmentPlanEntity plan = planRepository.findById(req.getInstalmentPlanId())
                    .filter(p -> p.getStatus() == PlanStatus.APPROVED)
                    .orElseThrow(() -> new BillingApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PLAN_NOT_ALLOWED",
                            "This plan cannot be used here"));
            activationPct = plan.getActivationThresholdPercent();
            planHash = plan.getPlanHash();
        }

        Instant now = Instant.now();
        String payRef = generateCheckDigitRef();
        String invNumber = "INV-KE-" + String.valueOf(LocalDate.now().getYear()).substring(2) + "-" + payRef;

        InvoiceEntity invoice = InvoiceEntity.builder()
                .invoiceId(invoiceId)
                .invoiceNumber(invNumber)
                .paymentReference(payRef)
                .type(req.getType())
                .policyId(req.getPolicyId())
                .batchId(req.getBatchId())
                .payerType(req.getPayerType())
                .payerRef(req.getPayerRef())
                .status(InvoiceStatus.OPEN)
                .totalMinor(totalMinor)
                .premiumMinor(req.getAmountMinor())
                .paidMinor(0L)
                .outstandingMinor(totalMinor)
                .currency(req.getCurrency())
                .subsidyMinor(subsidyMinor)
                .dueDate(dueDate)
                .instalmentPlanId(req.getInstalmentPlanId())
                .planHash(planHash)
                .activationThresholdPercent(activationPct)
                .createdAt(now)
                .build();
        invoice = invoiceRepository.saveAndFlush(invoice);

        if (req.getInstalmentPlanId() != null && !req.getInstalmentPlanId().isBlank()) {
            buildSchedule(invoice, planRepository.findById(req.getInstalmentPlanId()).orElseThrow(), now);
        }

        postJournal("RECEIVABLE", totalMinor, 0, req.getCurrency(), invoiceId, now);
        postJournal("PREMIUM_LIABILITY", 0, totalMinor, req.getCurrency(), invoiceId, now);

        outboxService.enqueue(properties.getKafka().getBillingTopic(), "invoice.created", invoiceId, Map.ofEntries(
                Map.entry("eventType", "invoice.created"),
                Map.entry("invoiceId", invoiceId),
                Map.entry("invoiceNumber", invNumber),
                Map.entry("paymentReference", payRef),
                Map.entry("policyId", Objects.toString(req.getPolicyId(), "")),
                Map.entry("farmerId", farmerId),
                Map.entry("payerType", req.getPayerType().name()),
                Map.entry("totalMinor", totalMinor),
                Map.entry("currency", req.getCurrency()),
                Map.entry("dueDate", dueDate.toString()),
                Map.entry("timestamp", now.toString())));

        InvoiceResponse resp = buildInvoiceResponse(invoice, true, Set.of("instalments"));
        idempotencyService.save(idempotencyKey, "BIL-01", req, 201, resp);
        return resp;
    }

    @Transactional
    public PaymentAttemptResponse initiatePayment(InitiatePaymentRequest req, String delegationHeader, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BillingApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Idempotency-Key required");
        }
        BillingIdentity actor = BillingSecurityContext.require();
        Optional<PaymentAttemptResponse> replay = idempotencyService.findReplay(
                idempotencyKey, "BIL-02:" + actor.actorId(), req, PaymentAttemptResponse.class);
        if (replay.isPresent()) return replay.get();

        InvoiceEntity invoice = invoiceRepository.findById(req.getInvoiceId())
                .orElseThrow(() -> new BillingApiException(HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND", "Invoice not found"));

        authorizePaymentInitiator(actor, invoice, delegationHeader);

        if (!Set.of(InvoiceStatus.OPEN, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.IN_GRACE).contains(invoice.getStatus())) {
            throw new BillingApiException(HttpStatus.CONFLICT, "INVOICE_NOT_PAYABLE",
                    "This invoice cannot be paid", Map.of("status", invoice.getStatus().name()));
        }

        Instant now = Instant.now();
        Optional<PaymentAttemptEntity> live = attemptRepository.findFirstByInvoiceIdAndStatusInAndExpiresAtAfter(
                invoice.getInvoiceId(),
                List.of(PaymentAttemptStatus.INITIATED, PaymentAttemptStatus.PENDING_PROVIDER),
                now);
        if (live.isPresent()) {
            throw new BillingApiException(HttpStatus.CONFLICT, "PAYMENT_IN_PROGRESS",
                    "A payment is already in progress",
                    Map.of("attemptId", live.get().getAttemptId(), "expiresAt", live.get().getExpiresAt().toString()));
        }

        rateLimitService.check("pay-inv:" + invoice.getInvoiceId(), 5, 3600);
        if (req.getPayerNumber() != null && !req.getPayerNumber().isBlank()) {
            rateLimitService.check("pay-num:" + clients.sha256(req.getPayerNumber()), 10, 86400);
        }

        List<Map<String, Object>> scheduleView = null;
        if (req.getInstalmentPlanId() != null && !req.getInstalmentPlanId().isBlank()) {
            if (invoice.getPaidMinor() > 0 || invoice.getInstalmentPlanId() != null) {
                throw new BillingApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PLAN_NOT_ALLOWED",
                        "Instalments cannot be chosen now");
            }
            InstalmentPlanEntity plan = planRepository.findById(req.getInstalmentPlanId())
                    .filter(p -> p.getStatus() == PlanStatus.APPROVED)
                    .orElseThrow(() -> new BillingApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PLAN_NOT_ALLOWED",
                            "Instalments cannot be chosen now"));
            invoice.setInstalmentPlanId(plan.getPlanId());
            invoice.setPlanHash(plan.getPlanHash());
            invoice.setActivationThresholdPercent(plan.getActivationThresholdPercent());
            buildSchedule(invoice, plan, now);
            invoiceRepository.save(invoice);
            scheduleView = scheduleRepository.findByInvoiceIdOrderBySeqAsc(invoice.getInvoiceId()).stream()
                    .map(s -> Map.<String, Object>of("seq", s.getSeq(), "dueDate", s.getDueDate().toString(),
                            "amountMinor", s.getAmountMinor(), "status", s.getStatus().name()))
                    .toList();
        }

        long amountMinor = req.getAmountMinor() != null ? req.getAmountMinor() : nextDueAmount(invoice);
        if (amountMinor <= 0 || amountMinor > invoice.getOutstandingMinor()) {
            throw new BillingApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AMOUNT_INVALID",
                    "Check the amount", Map.of("outstandingMinor", invoice.getOutstandingMinor()));
        }

        String phoneToken = null;
        String maskedPayer = null;
        if (req.getMethod() == PaymentMethod.MOBILE_MONEY) {
            if (req.getProvider() == null || req.getProvider().isBlank()
                    || req.getPayerNumber() == null || req.getPayerNumber().isBlank()) {
                throw new BillingApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "provider and payerNumber are required for MOBILE_MONEY");
            }
            phoneToken = clients.tokenizePayerPhone(req.getPayerNumber());
            if (phoneToken == null) {
                throw new BillingApiException(HttpStatus.SERVICE_UNAVAILABLE, "VAULT_UNAVAILABLE",
                        "Unable to start a payment right now");
            }
            maskedPayer = clients.maskPhone(req.getPayerNumber());
        }

        String attemptId = "att_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Instant expiresAt = now.plus(properties.getRules().getAttemptExpiryMinutes(), ChronoUnit.MINUTES);

        PaymentAttemptEntity attempt = PaymentAttemptEntity.builder()
                .attemptId(attemptId)
                .invoiceId(invoice.getInvoiceId())
                .method(req.getMethod())
                .provider(req.getProvider())
                .payerNumberToken(phoneToken)
                .maskedPayer(maskedPayer)
                .amountMinor(amountMinor)
                .status(PaymentAttemptStatus.INITIATED)
                .idempotencyKey(idempotencyKey)
                .expiresAt(expiresAt)
                .createdAt(now)
                .build();

        if (req.getMethod() == PaymentMethod.MOBILE_MONEY) {
            BillingExternalClients.ProviderInitResult res = clients.startProviderCollection(
                    req.getProvider(), invoice.getPaymentReference(), amountMinor, invoice.getCurrency());
            if (!res.reachable()) {
                attempt.setStatus(PaymentAttemptStatus.FAILED);
                attemptRepository.save(attempt);
                throw new BillingApiException(HttpStatus.SERVICE_UNAVAILABLE, "PROVIDER_UNAVAILABLE",
                        "Payment is temporarily unavailable. Use the manual instructions",
                        Map.of("manual", Map.of("accountReference", invoice.getPaymentReference())));
            }
            if (!res.accepted()) {
                attempt.setStatus(PaymentAttemptStatus.FAILED);
                attemptRepository.save(attempt);
                outboxService.enqueue(properties.getKafka().getBillingTopic(), "premium.failed", invoice.getInvoiceId(),
                        Map.of("eventType", "premium.failed", "invoiceId", invoice.getInvoiceId(),
                                "policyId", Objects.toString(invoice.getPolicyId(), ""),
                                "attemptId", attemptId, "method", req.getMethod().name(),
                                "reasonCode", res.reasonCode(), "timestamp", now.toString()));
                throw new BillingApiException(HttpStatus.BAD_GATEWAY, "PROVIDER_REJECTED",
                        "The provider could not start the payment", Map.of("reason", res.reasonCode()));
            }
            attempt.setStatus(PaymentAttemptStatus.PENDING_PROVIDER);
            attempt.setProviderRef(res.providerRef());
            attemptRepository.save(attempt);

            PaymentAttemptResponse resp = PaymentAttemptResponse.builder()
                    .attemptId(attemptId)
                    .invoiceId(invoice.getInvoiceId())
                    .status(attempt.getStatus().name())
                    .amountMinor(amountMinor)
                    .currency(invoice.getCurrency())
                    .maskedPayer(maskedPayer)
                    .expiresAt(expiresAt.toString())
                    .instructions(Map.of("channelPrompt", res.prompt(), "accountReference", invoice.getPaymentReference()))
                    .instalment(scheduleView != null ? Map.of("planId", invoice.getInstalmentPlanId(), "schedule", scheduleView) : null)
                    .pollAfterSeconds(10)
                    .build();
            idempotencyService.save(idempotencyKey, "BIL-02:" + actor.actorId(), req, 202, resp);
            return resp;
        }

        attempt.setStatus(req.getMethod() == PaymentMethod.CARD_CHECKOUT
                ? PaymentAttemptStatus.CHECKOUT_REQUIRED : PaymentAttemptStatus.REFERENCE_ISSUED);
        attemptRepository.save(attempt);
        return PaymentAttemptResponse.builder()
                .attemptId(attemptId)
                .invoiceId(invoice.getInvoiceId())
                .status(attempt.getStatus().name())
                .amountMinor(amountMinor)
                .currency(invoice.getCurrency())
                .expiresAt(expiresAt.toString())
                .instructions(req.getMethod() == PaymentMethod.CARD_CHECKOUT
                        ? Map.of("checkoutUrl", "https://checkout.agrishield.local/" + attemptId)
                        : Map.of("businessNumber", "522522", "accountReference", invoice.getPaymentReference()))
                .pollAfterSeconds(15)
                .build();
    }

    @Transactional
    public Map<String, Object> processCallback(
            String provider, String rawBody, String signatureHeader, String timestampHeader, String sourceIp) {
        if (clients.getProviderSecret(provider) == null) {
            throw new BillingApiException(HttpStatus.NOT_FOUND, "PROVIDER_UNKNOWN", "Unknown provider", Map.of(), true);
        }
        if (signatureHeader == null || timestampHeader == null
                || !clients.verifyProviderSignature(provider, rawBody, signatureHeader)) {
            throw new BillingApiException(HttpStatus.UNAUTHORIZED, "SIGNATURE_INVALID", "Invalid signature", Map.of(), true);
        }
        Instant ts;
        try {
            ts = Instant.parse(timestampHeader.trim());
        } catch (Exception e) {
            throw new BillingApiException(HttpStatus.UNAUTHORIZED, "SIGNATURE_INVALID", "Invalid timestamp", Map.of(), true);
        }
        if (Math.abs(ChronoUnit.MINUTES.between(ts, Instant.now())) > properties.getRules().getCallbackFreshnessMinutes()) {
            throw new BillingApiException(HttpStatus.UNAUTHORIZED, "SIGNATURE_INVALID", "Stale callback timestamp", Map.of(), true);
        }

        JsonNode body;
        String providerTxnId;
        long amountMinor;
        String currency;
        int resultCode;
        try {
            body = objectMapper.readTree(rawBody);
            providerTxnId = body.path("providerTxnId").asString("");
            currency = body.path("currency").asString("");
            resultCode = body.path("resultCode").asInt(-1);
            String amtStr = body.path("amountMinor").isNumber() ? String.valueOf(body.path("amountMinor").asLong()) : "";
            if (!amtStr.isBlank()) {
                amountMinor = Long.parseLong(amtStr);
            } else {
                amountMinor = Math.round(Double.parseDouble(body.path("amount").asString("0")) * 100);
            }
            if (providerTxnId.isBlank() || currency.isBlank() || amountMinor <= 0) {
                throw new IllegalArgumentException("Missing required callback fields");
            }
        } catch (Exception e) {
            throw new BillingApiException(HttpStatus.BAD_REQUEST, "MALFORMED_CALLBACK", "Malformed callback", Map.of(), true);
        }

        Optional<CallbackInboxEntity> dup = inboxRepository.findByProviderAndProviderTxnId(provider.toLowerCase(), providerTxnId);
        if (dup.isPresent()) {
            return Map.of("ResultCode", 0, "ResultDesc", "Accepted");
        }

        String rawPhone = body.path("payerNumber").asString("");
        String payerToken = rawPhone.isBlank() ? null : clients.tokenizePayerPhone(rawPhone);
        String maskedPayer = rawPhone.isBlank() ? null : clients.maskPhone(rawPhone);
        String attemptRef = body.path("attemptRef").asString("");
        String accountRef = body.path("accountRef").asString("");
        Instant now = Instant.now();

        Map<String, Object> ack = Map.of("ResultCode", 0, "ResultDesc", "Accepted");
        CallbackInboxEntity inbox = CallbackInboxEntity.builder()
                .inboxId(UUID.randomUUID())
                .provider(provider.toLowerCase())
                .providerTxnId(providerTxnId)
                .receivedAt(now)
                .signatureValid(true)
                .sourceIp(sourceIp)
                .payloadSanitized("providerTxnId=" + providerTxnId + ",amountMinor=" + amountMinor + ",payerToken=" + payerToken)
                .status("RECEIVED")
                .ackBody("{\"ResultCode\":0,\"ResultDesc\":\"Accepted\"}")
                .build();
        inboxRepository.save(inbox);

        PaymentAttemptEntity attempt = attemptRef.isBlank() ? null : attemptRepository.findById(attemptRef).orElse(null);
        InvoiceEntity invoice = attempt != null
                ? invoiceRepository.findById(attempt.getInvoiceId()).orElse(null)
                : invoiceRepository.findByPaymentReference(accountRef).orElse(null);

        if (resultCode != 0) {
            if (attempt != null) {
                attempt.setStatus(PaymentAttemptStatus.FAILED);
                attemptRepository.save(attempt);
            }
            inbox.setStatus("FAILED");
            inboxRepository.save(inbox);
            outboxService.enqueue(properties.getKafka().getBillingTopic(), "premium.failed",
                    invoice != null ? invoice.getInvoiceId() : providerTxnId, Map.of(
                            "eventType", "premium.failed",
                            "invoiceId", invoice != null ? invoice.getInvoiceId() : "",
                            "policyId", invoice != null ? Objects.toString(invoice.getPolicyId(), "") : "",
                            "attemptId", attemptRef,
                            "method", "MOBILE_MONEY",
                            "reasonCode", "PROVIDER_RESULT_" + resultCode,
                            "timestamp", now.toString()));
            return ack;
        }

        if (invoice == null || !invoice.getCurrency().equals(currency)) {
            postJournal("SUSPENSE", 0, amountMinor, currency, providerTxnId, now);
            outboxService.enqueue(properties.getKafka().getBillingTopic(), "reconciliation.break.detected", providerTxnId, Map.of(
                    "eventType", "reconciliation.break.detected",
                    "breakId", "brk_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                    "type", invoice == null ? "UNMATCHED" : "AMOUNT_MISMATCH",
                    "providerTxnId", providerTxnId,
                    "amountMinor", amountMinor,
                    "timestamp", now.toString()));
            inbox.setStatus("SUSPENSE");
            inboxRepository.save(inbox);
            return ack;
        }

        if (attempt != null) {
            attempt.setStatus(PaymentAttemptStatus.SUCCEEDED);
            attemptRepository.save(attempt);
        }

        long applyMinor = Math.min(amountMinor, invoice.getOutstandingMinor());
        applyPaymentToInvoice(invoice, attempt != null ? attempt.getAttemptId() : null,
                PaymentMethod.MOBILE_MONEY, PaymentSource.ONLINE, applyMinor,
                payerToken, maskedPayer, null, null, provider.toLowerCase(), providerTxnId, "callback", now);

        if (amountMinor > applyMinor) {
            long excess = amountMinor - applyMinor;
            refundRepository.save(RefundEntity.builder()
                    .refundId("rf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                    .invoiceId(invoice.getInvoiceId())
                    .policyId(invoice.getPolicyId())
                    .amountMinor(excess)
                    .currency(currency)
                    .reasonCode(RefundReasonCode.OVERPAYMENT)
                    .status(RefundStatus.PROCESSING)
                    .destinationType("MOBILE_MONEY")
                    .destinationToken(payerToken)
                    .maskedDestination(maskedPayer)
                    .provider(provider.toLowerCase())
                    .requestedBy("system")
                    .createdAt(now)
                    .build());
        }

        inbox.setStatus("APPLIED");
        inboxRepository.save(inbox);
        return ack;
    }

    @Transactional
    public SponsorBatchResponse createSponsorBatch(String sponsorId, CreateSponsorBatchRequest req, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BillingApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Idempotency-Key required");
        }
        BillingIdentity actor = BillingSecurityContext.require();
        boolean isStaff = actor.hasAnyRole(AuthRole.FINANCE, AuthRole.ADMIN);
        boolean isSponsorAdmin = actor.hasRole(AuthRole.SPONSOR_ADMIN) && Objects.equals(actor.sponsorId(), sponsorId);
        if (!isStaff && !isSponsorAdmin) {
            throw new BillingApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        Optional<SponsorBatchResponse> replay = idempotencyService.findReplay(
                idempotencyKey, "BIL-05:" + sponsorId, req, SponsorBatchResponse.class);
        if (replay.isPresent()) return replay.get();

        SponsorAccountEntity sponsor = sponsorRepository.findById(sponsorId)
                .orElseThrow(() -> new BillingApiException(HttpStatus.NOT_FOUND, "SPONSOR_NOT_FOUND", "Sponsor not found"));
        if (!"ACTIVE".equals(sponsor.getStatus())) {
            throw new BillingApiException(HttpStatus.CONFLICT, "SPONSOR_NOT_ACTIVE", "This sponsor account cannot create batches");
        }
        if (!Arrays.asList(sponsor.getAllowedMethodsCsv().split(",")).contains(req.getPaymentMethod().name())) {
            throw new BillingApiException(HttpStatus.UNPROCESSABLE_ENTITY, "METHOD_NOT_ALLOWED",
                    "This settlement method is not available");
        }

        List<Map<String, Object>> results = new ArrayList<>();
        List<InvoiceEntity> acceptedInvoices = new ArrayList<>();
        long totalMinor = 0;
        String currency = "KES";
        Instant earliestDue = req.getDueDate() != null ? req.getDueDate() : Instant.now().plus(14, ChronoUnit.DAYS);

        for (String pid : new LinkedHashSet<>(req.getPolicyIds())) {
            Optional<InvoiceEntity> invOpt = invoiceRepository.findByPolicyId(pid);
            if (invOpt.isEmpty()) {
                results.add(Map.of("policyId", pid, "status", "REJECTED", "reasonCode", "INVOICE_NOT_FOUND"));
                continue;
            }
            InvoiceEntity inv = invOpt.get();
            if (!Objects.equals(inv.getPayerRef(), sponsorId)
                    || (inv.getPayerType() != PayerType.SPONSOR && inv.getPayerType() != PayerType.LENDER)) {
                results.add(Map.of("policyId", pid, "status", "REJECTED", "reasonCode", "NOT_SPONSOR_PAYER"));
                continue;
            }
            if (inv.getStatus() != InvoiceStatus.OPEN || inv.getBatchId() != null) {
                results.add(Map.of("policyId", pid, "status", "REJECTED", "reasonCode", "INVOICE_NOT_OPEN"));
                continue;
            }
            acceptedInvoices.add(inv);
            totalMinor += inv.getOutstandingMinor();
            currency = inv.getCurrency();
            if (inv.getDueDate() != null && inv.getDueDate().isBefore(earliestDue)) earliestDue = inv.getDueDate();
            results.add(Map.of("policyId", pid, "status", "ACCEPTED"));
        }

        if (acceptedInvoices.isEmpty()) {
            throw new BillingApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NO_VALID_ITEMS",
                    "No policies could be added", Map.of("results", results));
        }

        boolean creditCover = req.getPaymentMethod() == PaymentMethod.CREDIT;
        if (creditCover) {
            if (sponsor.getUsedMinor() + totalMinor > sponsor.getCreditLimitMinor()) {
                throw new BillingApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CREDIT_LIMIT_EXCEEDED",
                        "Credit limit exceeded",
                        Map.of("usedMinor", sponsor.getUsedMinor(), "limitMinor", sponsor.getCreditLimitMinor()));
            }
            sponsor.setUsedMinor(sponsor.getUsedMinor() + totalMinor);
            sponsorRepository.save(sponsor);
        }

        Instant now = Instant.now();
        String batchId = "bat_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String batchRef = "BT-26-" + generateCheckDigitRef().substring(0, 6);
        String consolidatedId = "inv_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        InvoiceEntity consolidated = invoiceRepository.save(InvoiceEntity.builder()
                .invoiceId(consolidatedId)
                .invoiceNumber("INV-BAT-" + batchRef)
                .paymentReference(generateCheckDigitRef())
                .type(InvoiceType.SPONSOR_BATCH)
                .batchId(batchId)
                .payerType(PayerType.SPONSOR)
                .payerRef(sponsorId)
                .status(creditCover ? InvoiceStatus.PAID : InvoiceStatus.OPEN)
                .totalMinor(totalMinor)
                .premiumMinor(totalMinor)
                .paidMinor(creditCover ? totalMinor : 0L)
                .outstandingMinor(creditCover ? 0L : totalMinor)
                .currency(currency)
                .dueDate(earliestDue)
                .createdAt(now)
                .build());

        for (InvoiceEntity inv : acceptedInvoices) {
            inv.setBatchId(batchId);
            invoiceRepository.save(inv);
            if (creditCover) {
                applyPaymentToInvoice(inv, null, PaymentMethod.SPONSOR_CREDIT, PaymentSource.CREDIT,
                        inv.getOutstandingMinor(), null, sponsor.getName(), null, batchRef, null, null, actor.actorId(), now);
            }
        }

        batchRepository.save(SponsorBatchEntity.builder()
                .batchId(batchId)
                .batchReference(batchRef)
                .sponsorId(sponsorId)
                .paymentMethod(req.getPaymentMethod())
                .allocationMode(req.getAllocationMode() != null ? req.getAllocationMode() : BatchAllocationMode.ALL_OR_NOTHING)
                .consolidatedInvoiceId(consolidated.getInvoiceId())
                .policyIdsCsv(String.join(",", acceptedInvoices.stream().map(InvoiceEntity::getPolicyId).toList()))
                .totalMinor(totalMinor)
                .currency(currency)
                .status(creditCover ? "COVERED_ON_CREDIT" : "OPEN")
                .dueDate(earliestDue)
                .externalBatchRef(req.getExternalBatchRef())
                .createdAt(now)
                .build());

        outboxService.enqueue(properties.getKafka().getBillingTopic(), "sponsor.batch.created", batchId, Map.of(
                "eventType", "sponsor.batch.created",
                "batchId", batchId,
                "sponsorId", sponsorId,
                "paymentMethod", req.getPaymentMethod().name(),
                "itemCount", acceptedInvoices.size(),
                "totalMinor", totalMinor,
                "currency", currency,
                "coveredOnCredit", creditCover,
                "timestamp", now.toString()));

        SponsorBatchResponse resp = SponsorBatchResponse.builder()
                .batchId(batchId)
                .batchReference(batchRef)
                .status(creditCover ? "COVERED_ON_CREDIT" : "OPEN")
                .consolidatedInvoiceId(consolidated.getInvoiceId())
                .totalMinor(totalMinor)
                .currency(currency)
                .accepted(acceptedInvoices.size())
                .rejected(results.size() - acceptedInvoices.size())
                .results(results)
                .dueDate(earliestDue.toString())
                .settlementInstructions(Map.of("bank", "AgriShield Clearing Bank", "reference", batchRef))
                .creditExposure(creditCover ? Map.of("usedMinor", sponsor.getUsedMinor(), "limitMinor", sponsor.getCreditLimitMinor()) : null)
                .build();
        idempotencyService.save(idempotencyKey, "BIL-05:" + sponsorId, req, 201, resp);
        return resp;
    }

    public InvoiceResponse getInvoice(String idOrRef, String includeParam, String delegationHeader) {
        BillingIdentity actor = BillingSecurityContext.require();
        InvoiceEntity inv = invoiceRepository.findByInvoiceIdOrInvoiceNumberOrPaymentReference(idOrRef, idOrRef, idOrRef)
                .orElseThrow(() -> new BillingApiException(HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND", "Invoice not found"));

        boolean isStaff = actor.hasAnyRole(AuthRole.SUPPORT, AuthRole.FINANCE, AuthRole.COMPLIANCE_OFFICER, AuthRole.ADMIN);
        boolean isOwner = Objects.equals(actor.actorId(), inv.getPayerRef())
                || (actor.userId() != null && Objects.equals(actor.userId().toString(), inv.getPayerRef()));
        boolean isSponsor = actor.hasRole(AuthRole.SPONSOR_ADMIN) && Objects.equals(actor.sponsorId(), inv.getPayerRef());
        boolean isDelegated = !isStaff && !isOwner && !isSponsor && actor.hasRole(AuthRole.AGENT)
                && delegationHeader != null && actor.userId() != null && isValidDelegation(actor, inv, delegationHeader);

        if (!isStaff && !isOwner && !isSponsor && !isDelegated) {
            throw new BillingApiException(HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND", "Invoice not found");
        }
        Set<String> inc = includeParam == null ? Set.of("summary")
                : new HashSet<>(Arrays.asList(includeParam.toLowerCase().split(",")));
        return buildInvoiceResponse(inv, !isDelegated, inc);
    }

    @Transactional
    public OfflinePaymentResponse recordOfflinePayment(
            RecordOfflinePaymentRequest req, String delegationHeader, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BillingApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Idempotency-Key required");
        }
        BillingIdentity actor = BillingSecurityContext.require();
        Optional<OfflinePaymentResponse> replay = idempotencyService.findReplay(
                idempotencyKey, "BIL-08:" + actor.actorId(), req, OfflinePaymentResponse.class);
        if (replay.isPresent()) return replay.get();

        InvoiceEntity invoice = invoiceRepository.findById(req.getInvoiceId())
                .orElseThrow(() -> new BillingApiException(HttpStatus.NOT_FOUND, "INVOICE_NOT_FOUND", "Invoice not found"));

        if (!Set.of(InvoiceStatus.OPEN, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.IN_GRACE).contains(invoice.getStatus())) {
            throw new BillingApiException(HttpStatus.CONFLICT, "INVOICE_NOT_PAYABLE", "This invoice cannot be paid");
        }
        if (req.getAmountMinor() > invoice.getOutstandingMinor()) {
            throw new BillingApiException(HttpStatus.UNPROCESSABLE_ENTITY, "AMOUNT_INVALID",
                    "Check the amount", Map.of("outstandingMinor", invoice.getOutstandingMinor()));
        }

        Instant now = Instant.now();
        if (req.getMethod() == PaymentMethod.AGENT_CASH) {
            if (!actor.hasRole(AuthRole.AGENT) || !actor.collector() || actor.userId() == null) {
                throw new BillingApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Collector agent role required");
            }
            if (invoice.getPayerType() != PayerType.FARMER) {
                throw new BillingApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only farmer invoices accept cash");
            }
            if (delegationHeader == null || delegationHeader.isBlank() || !isValidDelegation(actor, invoice, delegationHeader)) {
                throw new BillingApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Valid POLICY_ASSIST delegation required");
            }
            if (req.getReceiptNumber() == null || req.getReceiptNumber().isBlank()) {
                throw new BillingApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "receiptNumber required");
            }
            if (paymentRepository.findByReceiptNumber(req.getReceiptNumber()).isPresent()) {
                throw new BillingApiException(HttpStatus.CONFLICT, "DUPLICATE_RECEIPT", "This receipt was already recorded");
            }
            AgentFloatEntity fl = floatRepository.findById(actor.actorId())
                    .orElseThrow(() -> new BillingApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FLOAT_INSUFFICIENT",
                            "Top up your float first", Map.of("floatBalanceMinor", 0L)));
            if (fl.getBalanceMinor() < req.getAmountMinor()) {
                throw new BillingApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FLOAT_INSUFFICIENT",
                        "Top up your float first", Map.of("floatBalanceMinor", fl.getBalanceMinor()));
            }
            fl.setBalanceMinor(fl.getBalanceMinor() - req.getAmountMinor());
            floatRepository.save(fl);
            postJournal("AGENT_FLOAT", 0, req.getAmountMinor(), invoice.getCurrency(), req.getReceiptNumber(), now);

            PaymentEntity pay = applyPaymentToInvoice(invoice, null, PaymentMethod.AGENT_CASH, PaymentSource.OFFLINE,
                    req.getAmountMinor(), null, "Cash receipt " + req.getReceiptNumber(),
                    req.getReceiptNumber(), null, null, null, actor.actorId(), now);

            OfflinePaymentResponse resp = OfflinePaymentResponse.builder()
                    .paymentId(pay.getPaymentId())
                    .invoiceId(invoice.getInvoiceId())
                    .status("COMPLETED")
                    .method(PaymentMethod.AGENT_CASH.name())
                    .amountMinor(req.getAmountMinor())
                    .currency(invoice.getCurrency())
                    .invoice(Map.of(
                            "status", invoice.getStatus().name(),
                            "paidMinor", invoice.getPaidMinor(),
                            "outstandingMinor", invoice.getOutstandingMinor(),
                            "activationThresholdMet", isThresholdMet(invoice)))
                    .receipt(Map.of(
                            "receiptNumber", req.getReceiptNumber(),
                            "disputeWindowEnds", now.plus(properties.getRules().getCashDisputeDays(), ChronoUnit.DAYS).toString()))
                    .floatBalanceMinor(fl.getBalanceMinor())
                    .build();
            idempotencyService.save(idempotencyKey, "BIL-08:" + actor.actorId(), req, 201, resp);
            return resp;
        }

        if (!actor.hasAnyRole(AuthRole.FINANCE, AuthRole.ADMIN)) {
            throw new BillingApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        if (req.getStatementLineRef() == null || req.getStatementLineRef().isBlank()) {
            throw new BillingApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "statementLineRef required");
        }
        if (paymentRepository.findByStatementLineRef(req.getStatementLineRef()).isPresent()) {
            throw new BillingApiException(HttpStatus.CONFLICT, "DUPLICATE_RECEIPT", "This statement reference was already used");
        }

        if (req.getAmountMinor() > properties.getRules().getBankTransferLimitMinor()) {
            String payId = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            PaymentEntity held = paymentRepository.save(PaymentEntity.builder()
                    .paymentId(payId)
                    .invoiceId(invoice.getInvoiceId())
                    .method(PaymentMethod.BANK_TRANSFER)
                    .source(PaymentSource.OFFLINE)
                    .amountMinor(req.getAmountMinor())
                    .currency(invoice.getCurrency())
                    .statementLineRef(req.getStatementLineRef())
                    .receivedAt(req.getCollectedAt())
                    .status(PaymentStatus.HELD)
                    .recordedBy(actor.actorId())
                    .build());
            String aprId = "apr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            approvalRepository.save(BillingApprovalEntity.builder()
                    .approvalId(aprId)
                    .type(ApprovalType.OFFLINE_PAYMENT)
                    .subjectId(held.getPaymentId())
                    .subjectHash(clients.sha256(held.getPaymentId() + "|" + held.getAmountMinor() + "|" + held.getStatementLineRef()))
                    .requestedBy(actor.actorId())
                    .requiredLevels(1)
                    .requiredRolesCsv("FINANCE")
                    .decisionsJson("[]")
                    .status(ApprovalStatus.OPEN)
                    .createdAt(now)
                    .build());
            return OfflinePaymentResponse.builder()
                    .paymentId(payId)
                    .invoiceId(invoice.getInvoiceId())
                    .status("PENDING_APPROVAL")
                    .method(PaymentMethod.BANK_TRANSFER.name())
                    .amountMinor(req.getAmountMinor())
                    .currency(invoice.getCurrency())
                    .approvalId(aprId)
                    .build();
        }

        PaymentEntity pay = applyPaymentToInvoice(invoice, null, PaymentMethod.BANK_TRANSFER, PaymentSource.OFFLINE,
                req.getAmountMinor(), null, req.getStatementLineRef(), null, req.getStatementLineRef(),
                null, null, actor.actorId(), now);
        return OfflinePaymentResponse.builder()
                .paymentId(pay.getPaymentId())
                .invoiceId(invoice.getInvoiceId())
                .status("COMPLETED")
                .method(PaymentMethod.BANK_TRANSFER.name())
                .amountMinor(req.getAmountMinor())
                .currency(invoice.getCurrency())
                .invoice(Map.of("status", invoice.getStatus().name(), "paidMinor", invoice.getPaidMinor(),
                        "outstandingMinor", invoice.getOutstandingMinor(), "activationThresholdMet", isThresholdMet(invoice)))
                .build();
    }

    @Transactional
    public PaymentEntity applyPaymentToInvoice(
            InvoiceEntity invoice, String attemptId, PaymentMethod method, PaymentSource source,
            long amountMinor, String payerToken, String maskedPayer, String receiptNumber,
            String statementLineRef, String provider, String providerTxnId, String recordedBy, Instant now) {
        String payId = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        PaymentEntity payment = paymentRepository.save(PaymentEntity.builder()
                .paymentId(payId)
                .invoiceId(invoice.getInvoiceId())
                .attemptId(attemptId)
                .method(method)
                .source(source)
                .amountMinor(amountMinor)
                .currency(invoice.getCurrency())
                .payerToken(payerToken)
                .maskedPayer(maskedPayer)
                .receiptNumber(receiptNumber)
                .statementLineRef(statementLineRef)
                .receivedAt(now)
                .provider(provider)
                .providerTxnId(providerTxnId)
                .status(PaymentStatus.COMPLETED)
                .recordedBy(recordedBy)
                .build());

        long newPaid = invoice.getPaidMinor() + amountMinor;
        long newOut = Math.max(0L, invoice.getTotalMinor() - newPaid);
        invoice.setPaidMinor(newPaid);
        invoice.setOutstandingMinor(newOut);
        invoice.setStatus(newOut == 0 ? InvoiceStatus.PAID : InvoiceStatus.PARTIALLY_PAID);
        invoiceRepository.save(invoice);

        // Allocate across instalment schedule in seq order
        long remaining = amountMinor;
        for (InstalmentScheduleEntity row : scheduleRepository.findByInvoiceIdOrderBySeqAsc(invoice.getInvoiceId())) {
            if (remaining <= 0) break;
            long rowDue = Math.max(0L, row.getAmountMinor() - row.getPaidMinor());
            if (rowDue > 0) {
                long alloc = Math.min(remaining, rowDue);
                row.setPaidMinor(row.getPaidMinor() + alloc);
                row.setStatus(row.getPaidMinor() >= row.getAmountMinor() ? InstalmentStatus.PAID : row.getStatus());
                scheduleRepository.save(row);
                remaining -= alloc;
            }
        }

        postJournal("CASH", amountMinor, 0, invoice.getCurrency(), payId, now);
        postJournal("RECEIVABLE", 0, amountMinor, invoice.getCurrency(), payId, now);

        boolean thresholdMet = isThresholdMet(invoice);
        outboxService.enqueue(properties.getKafka().getBillingTopic(), "premium.paid", invoice.getInvoiceId(), Map.ofEntries(
                Map.entry("eventType", "premium.paid"),
                Map.entry("invoiceId", invoice.getInvoiceId()),
                Map.entry("policyId", Objects.toString(invoice.getPolicyId(), "")),
                Map.entry("paymentId", payId),
                Map.entry("amountMinor", amountMinor),
                Map.entry("paidToDateMinor", newPaid),
                Map.entry("totalMinor", invoice.getTotalMinor()),
                Map.entry("currency", invoice.getCurrency()),
                Map.entry("method", method.name()),
                Map.entry("fullyPaid", newOut == 0),
                Map.entry("activationThresholdMet", thresholdMet),
                Map.entry("payerType", invoice.getPayerType().name()),
                Map.entry("paidAt", now.toString()),
                Map.entry("timestamp", now.toString())));
        return payment;
    }

    @Transactional
    public void onPolicyIssued(String policyId, String farmerId, String productId, long premiumMinor, long subsidyMinor,
                               String currency, String payerType, Instant payBy) {
        Instant now = Instant.now();
        factRepository.save(PolicyBillingFactEntity.builder()
                .policyId(policyId)
                .farmerId(farmerId)
                .productId(productId)
                .premiumMinor(premiumMinor)
                .subsidyMinor(subsidyMinor)
                .currency(currency)
                .payerType(payerType)
                .payerRef(farmerId)
                .payBy(payBy)
                .status("PENDING_PAYMENT")
                .updatedAt(now)
                .build());
    }

    @Transactional
    public void onPolicyCancelledOrEnded(String policyId, String newStatus) {
        factRepository.findById(policyId).ifPresent(f -> {
            f.setStatus(newStatus);
            f.setUpdatedAt(Instant.now());
            factRepository.save(f);
        });
        invoiceRepository.findByPolicyId(policyId).ifPresent(inv -> {
            if (inv.getPaidMinor() == 0 && inv.getStatus() == InvoiceStatus.OPEN) {
                inv.setStatus(InvoiceStatus.CANCELLED);
                invoiceRepository.save(inv);
                outboxService.enqueue(properties.getKafka().getBillingTopic(), "invoice.cancelled", inv.getInvoiceId(),
                        Map.of("eventType", "invoice.cancelled", "invoiceId", inv.getInvoiceId(),
                                "policyId", policyId, "reason", newStatus));
            }
        });
    }

    public boolean isThresholdMet(InvoiceEntity inv) {
        long needed = Math.round(inv.getTotalMinor() * (inv.getActivationThresholdPercent() / 100.0));
        return inv.getPaidMinor() >= needed;
    }

    public void postJournal(String account, long debit, long credit, String currency, String ref, Instant at) {
        journalRepository.save(JournalEntryEntity.builder()
                .entryId(UUID.randomUUID())
                .account(account)
                .debitMinor(debit)
                .creditMinor(credit)
                .currency(currency)
                .reference(ref)
                .postedAt(at)
                .build());
    }

    private void buildSchedule(InvoiceEntity inv, InstalmentPlanEntity plan, Instant now) {
        List<Map<String, Object>> specs = readList(plan.getScheduleJson());
        long remaining = inv.getTotalMinor();
        for (int i = 0; i < specs.size(); i++) {
            Map<String, Object> spec = specs.get(i);
            int seq = ((Number) spec.get("seq")).intValue();
            double pct = ((Number) spec.get("percent")).doubleValue();
            int offset = ((Number) spec.get("dueOffsetDays")).intValue();
            long amt = (i == specs.size() - 1) ? remaining : Math.round(inv.getTotalMinor() * (pct / 100.0));
            remaining -= amt;
            scheduleRepository.save(InstalmentScheduleEntity.builder()
                    .scheduleId(UUID.randomUUID())
                    .invoiceId(inv.getInvoiceId())
                    .seq(seq)
                    .dueDate(now.plus(offset, ChronoUnit.DAYS))
                    .amountMinor(amt)
                    .paidMinor(0L)
                    .status(InstalmentStatus.PENDING)
                    .build());
        }
    }

    private long nextDueAmount(InvoiceEntity inv) {
        return scheduleRepository.findByInvoiceIdOrderBySeqAsc(inv.getInvoiceId()).stream()
                .filter(s -> s.getPaidMinor() < s.getAmountMinor())
                .findFirst()
                .map(s -> s.getAmountMinor() - s.getPaidMinor())
                .orElse(inv.getOutstandingMinor());
    }

    private void authorizePaymentInitiator(BillingIdentity actor, InvoiceEntity inv, String delegationHeader) {
        if (actor.hasRole(AuthRole.SUPPORT)) return;
        if (Objects.equals(actor.actorId(), inv.getPayerRef())
                || (actor.userId() != null && Objects.equals(actor.userId().toString(), inv.getPayerRef()))) return;
        if (actor.hasRole(AuthRole.AGENT) && isValidDelegation(actor, inv, delegationHeader)) return;
        throw new BillingApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
    }

    private boolean isValidDelegation(BillingIdentity actor, InvoiceEntity inv, String delegationHeader) {
        if (delegationHeader == null || delegationHeader.isBlank() || actor.userId() == null) return false;
        try {
            return clients.validateDelegation(UUID.fromString(delegationHeader.trim()),
                    actor.userId(), UUID.fromString(inv.getPayerRef()), DelegationScope.POLICY_ASSIST);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private InvoiceResponse buildInvoiceResponse(InvoiceEntity inv, boolean includeDetails, Set<String> inc) {
        List<Map<String, Object>> lineMaps = lineRepository.findByInvoiceId(inv.getInvoiceId()).stream()
                .map(l -> Map.<String, Object>of(
                        "code", l.getCode().name(),
                        "label", l.getLabel(),
                        "amountMinor", l.getAmountMinor(),
                        "refundable", l.isRefundable()))
                .toList();

        InvoiceResponse.InvoiceResponseBuilder b = InvoiceResponse.builder()
                .invoiceId(inv.getInvoiceId())
                .invoiceNumber(inv.getInvoiceNumber())
                .paymentReference(inv.getPaymentReference())
                .type(inv.getType().name())
                .status(inv.getStatus().name())
                .policyId(inv.getPolicyId())
                .payerType(inv.getPayerType().name())
                .payerRef(inv.getPayerRef())
                .lines(lineMaps)
                .totalMinor(inv.getTotalMinor())
                .paidMinor(inv.getPaidMinor())
                .outstandingMinor(inv.getOutstandingMinor())
                .currency(inv.getCurrency())
                .subsidyMinor(inv.getSubsidyMinor())
                .dueDate(inv.getDueDate() != null ? inv.getDueDate().toString() : null)
                .activationThreshold(Map.of(
                        "percent", inv.getActivationThresholdPercent(),
                        "met", isThresholdMet(inv),
                        "remainingMinor", Math.max(0L, Math.round(inv.getTotalMinor() * (inv.getActivationThresholdPercent() / 100.0)) - inv.getPaidMinor())))
                .version(inv.getVersion())
                .createdAt(inv.getCreatedAt().toString());

        if (includeDetails) {
            if (inc.contains("instalments")) {
                b.instalments(scheduleRepository.findByInvoiceIdOrderBySeqAsc(inv.getInvoiceId()).stream()
                        .map(s -> Map.<String, Object>of("seq", s.getSeq(), "dueDate", s.getDueDate().toString(),
                                "amountMinor", s.getAmountMinor(), "paidMinor", s.getPaidMinor(), "status", s.getStatus().name()))
                        .toList());
            }
            if (inc.contains("attempts")) {
                b.attempts(attemptRepository.findByInvoiceIdOrderByCreatedAtDesc(inv.getInvoiceId()).stream()
                        .map(a -> Map.<String, Object>of("attemptId", a.getAttemptId(), "method", a.getMethod().name(),
                                "maskedPayer", Objects.toString(a.getMaskedPayer(), ""),
                                "status", a.getStatus().name(), "at", a.getCreatedAt().toString()))
                        .toList());
            }
            if (inc.contains("payments")) {
                b.payments(paymentRepository.findByInvoiceIdOrderByReceivedAtDesc(inv.getInvoiceId()).stream()
                        .map(p -> Map.<String, Object>of("paymentId", p.getPaymentId(), "method", p.getMethod().name(),
                                "amountMinor", p.getAmountMinor(), "status", p.getStatus().name(), "at", p.getReceivedAt().toString()))
                        .toList());
            }
            if (inc.contains("refunds")) {
                b.refunds(refundRepository.findByInvoiceIdOrderByCreatedAtDesc(inv.getInvoiceId()).stream()
                        .map(r -> Map.<String, Object>of("refundId", r.getRefundId(), "amountMinor", r.getAmountMinor(),
                                "status", r.getStatus().name(), "reasonCode", r.getReasonCode().name()))
                        .toList());
            }
            b.refundableMinor(Math.max(0L, inv.getPaidMinor()
                    - refundRepository.findByInvoiceIdOrderByCreatedAtDesc(inv.getInvoiceId()).stream()
                    .filter(r -> r.getStatus() != RefundStatus.REJECTED && r.getStatus() != RefundStatus.FAILED)
                    .mapToLong(RefundEntity::getAmountMinor).sum()));
        }
        return b.build();
    }

    private String generateCheckDigitRef() {
        String base = String.format("%07d", Math.abs(UUID.randomUUID().hashCode()) % 10_000_000);
        int sum = 0;
        for (int i = 0; i < base.length(); i++) {
            int d = base.charAt(i) - '0';
            sum += (i % 2 == 0) ? d : (d * 3);
        }
        return base + ((10 - (sum % 10)) % 10);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readList(String json) {
        try { return objectMapper.readValue(json, List.class); } catch (Exception e) { return List.of(); }
    }
}
