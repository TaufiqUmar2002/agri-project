package com.umar;

import com.umar.billing.client.BillingExternalClients;
import com.umar.billing.entity.AgentFloatEntity;
import com.umar.billing.entity.SponsorAccountEntity;
import com.umar.billing.repository.AgentFloatRepository;
import com.umar.billing.repository.SponsorAccountRepository;
import com.umar.billing.security.BillingIdentityFilter;
import com.umar.billing.service.BillingCoreService;
import com.umar.billing.service.BillingRateLimitService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = PremiumBillingApplication.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PremiumBillingEndToEndTest {

    @Autowired private WebApplicationContext wac;
    @Autowired private BillingIdentityFilter identityFilter;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BillingExternalClients clients;
    @Autowired private BillingCoreService billingCoreService;
    @Autowired private SponsorAccountRepository sponsorRepository;
    @Autowired private AgentFloatRepository floatRepository;
    @Autowired private BillingRateLimitService rateLimitService;

    private MockMvc mockMvc;
    private static final UUID FARMER_1 = UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111");
    private static final UUID FARMER_2 = UUID.fromString("bbbbbbbb-2222-2222-2222-222222222222");
    private static final UUID AGENT_1 = UUID.fromString("cccccccc-3333-3333-3333-333333333333");
    private static String invoiceId1;
    private static String payRef1;
    private static String attemptId1;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).addFilters(identityFilter).build();
        clients.reset();
        rateLimitService.reset();
    }

    @Test
    @Order(1)
    void bil01_bil02_bil03_bil07_invoicePayCallbackAndGetInvoice() throws Exception {
        billingCoreService.onPolicyIssued("pol_1", FARMER_1.toString(), "prd_1", 324_000L, 135_000L, "KES", "FARMER",
                Instant.now().plus(7, ChronoUnit.DAYS));

        MvcResult invRes = mockMvc.perform(post("/api/billing/invoices")
                        .header("X-Service-Identity", "billing-consumer")
                        .header("Idempotency-Key", "evt_pol_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "POLICY",
                                  "policyId": "pol_1",
                                  "payerType": "FARMER",
                                  "payerRef": "%s",
                                  "amountMinor": 324000,
                                  "currency": "KES"
                                }
                                """.formatted(FARMER_1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();
        JsonNode invNode = objectMapper.readTree(invRes.getResponse().getContentAsString());
        invoiceId1 = invNode.get("invoiceId").asString();
        payRef1 = invNode.get("paymentReference").asString();

        // BIL-02 Initiate Payment
        MvcResult attRes = mockMvc.perform(post("/api/billing/pay")
                        .header("X-Actor-Id", FARMER_1.toString())
                        .header("X-Auth-Roles", "FARMER")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invoiceId": "%s",
                                  "method": "MOBILE_MONEY",
                                  "provider": "MPESA",
                                  "payerNumber": "+254712345678",
                                  "channel": "APP"
                                }
                                """.formatted(invoiceId1)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_PROVIDER"))
                .andReturn();
        attemptId1 = objectMapper.readTree(attRes.getResponse().getContentAsString()).get("attemptId").asString();

        // Conflict when a live attempt exists -> 409 PAYMENT_IN_PROGRESS
        mockMvc.perform(post("/api/billing/pay")
                        .header("X-Actor-Id", FARMER_1.toString())
                        .header("X-Auth-Roles", "FARMER")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invoiceId": "%s",
                                  "method": "MOBILE_MONEY",
                                  "provider": "MPESA",
                                  "payerNumber": "+254712345678",
                                  "channel": "APP"
                                }
                                """.formatted(invoiceId1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT_IN_PROGRESS"));

        // BIL-03 Forged callback -> 401
        String cbBody = """
                {
                  "providerTxnId": "RGH7K2LM91",
                  "attemptRef": "%s",
                  "accountRef": "%s",
                  "amount": "3240.00",
                  "currency": "KES",
                  "resultCode": 0,
                  "payerNumber": "254712345678"
                }
                """.formatted(attemptId1, payRef1);
        mockMvc.perform(post("/api/billing/callback/mpesa")
                        .header("X-Signature", "deadbeef")
                        .header("X-Timestamp", Instant.now().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cbBody))
                .andExpect(status().isUnauthorized());

        // BIL-03 Valid HMAC signature -> 200 & invoice PAID
        String sig = clients.signHmacSha256(clients.getProviderSecret("mpesa"), cbBody);
        mockMvc.perform(post("/api/billing/callback/mpesa")
                        .header("X-Signature", sig)
                        .header("X-Timestamp", Instant.now().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cbBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ResultCode").value(0));

        // BIL-07 Get Invoice shows PAID
        mockMvc.perform(get("/api/billing/invoices/" + invoiceId1 + "?include=attempts,payments")
                        .header("X-Actor-Id", FARMER_1.toString())
                        .header("X-Auth-Roles", "FARMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.activationThreshold.met").value(true));

        // Non-owner gets 404
        mockMvc.perform(get("/api/billing/invoices/" + invoiceId1)
                        .header("X-Actor-Id", FARMER_2.toString())
                        .header("X-Auth-Roles", "FARMER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INVOICE_NOT_FOUND"));
    }

    @Test
    @Order(2)
    void bil04_bil06_bil09_instalmentPlanAndMakerCheckerRefundApproval() throws Exception {
        // BIL-04 Invalid plan (deposit < 40) -> 422 PLAN_INVALID
        mockMvc.perform(post("/api/billing/instalments")
                        .header("X-Actor-Id", "fin-author")
                        .header("X-Auth-Roles", "FINANCE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": { "en": "Bad plan" },
                                  "planCode": "BAD_PLAN",
                                  "scope": { "countryCodes": ["KE"] },
                                  "instalments": [ { "seq": 1, "percent": 10, "dueOffsetDays": 0 }, { "seq": 2, "percent": 90, "dueOffsetDays": 30 } ],
                                  "depositPercent": 10,
                                  "activationThresholdPercent": 50,
                                  "minInstalmentMinor": 50000,
                                  "grace": { "days": 5 },
                                  "lapseRule": { "action": "LAPSE_POLICY" },
                                  "reinstatement": { "allowed": true },
                                  "reminders": { "daysBeforeDue": [3] },
                                  "effectiveFrom": "%s"
                                }
                                """.formatted(LocalDate.now().plusDays(14))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PLAN_INVALID"));

        // Valid plan -> PENDING_APPROVAL
        mockMvc.perform(post("/api/billing/instalments")
                        .header("X-Actor-Id", "fin-author")
                        .header("X-Auth-Roles", "FINANCE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": { "en": "Two part" },
                                  "planCode": "TWO_PART_MAIZE",
                                  "scope": { "countryCodes": ["KE"] },
                                  "instalments": [ { "seq": 1, "percent": 60, "dueOffsetDays": 0 }, { "seq": 2, "percent": 40, "dueOffsetDays": 45 } ],
                                  "depositPercent": 60,
                                  "activationThresholdPercent": 60,
                                  "minInstalmentMinor": 50000,
                                  "grace": { "days": 5 },
                                  "lapseRule": { "action": "LAPSE_POLICY" },
                                  "reinstatement": { "allowed": true },
                                  "reminders": { "daysBeforeDue": [3] },
                                  "effectiveFrom": "%s"
                                }
                                """.formatted(LocalDate.now().plusDays(14))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        // BIL-06 Refund (manual) requires Finance maker-checker BIL-09
        MvcResult rfRes = mockMvc.perform(post("/api/billing/refunds")
                        .header("X-Actor-Id", "fin-requester")
                        .header("X-Auth-Roles", "FINANCE")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invoiceId": "%s",
                                  "amountMinor": 100000,
                                  "reasonCode": "ERROR_CORRECTION",
                                  "note": "Refunding overcharge to original payer wallet."
                                }
                                """.formatted(invoiceId1)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn();
        JsonNode rfNode = objectMapper.readTree(rfRes.getResponse().getContentAsString());
        String approvalId = rfNode.path("approvalRequired").path("approvalId").asString();
        String rfId = rfNode.get("refundId").asString();
        String maskedDest = rfNode.path("destination").path("maskedReference").asString();
        String subjectHash = clients.sha256(rfId + "|100000|" + maskedDest);

        // Requester tries to approve -> 403 REQUESTER_CANNOT_APPROVE
        mockMvc.perform(put("/api/billing/approvals/" + approvalId + "/decision")
                        .header("X-Actor-Id", "fin-requester")
                        .header("X-Auth-Roles", "FINANCE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "subjectHash": "%s",
                                  "checklist": { "destinationChecked": true, "amountChecked": true, "evidenceChecked": true },
                                  "note": "Self-approval attempt should be rejected."
                                }
                                """.formatted(subjectHash)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REQUESTER_CANNOT_APPROVE"));

        // Second Finance approves -> refund moves to PROCESSING
        mockMvc.perform(put("/api/billing/approvals/" + approvalId + "/decision")
                        .header("X-Actor-Id", "fin-approver")
                        .header("X-Auth-Roles", "FINANCE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "subjectHash": "%s",
                                  "checklist": { "destinationChecked": true, "amountChecked": true, "evidenceChecked": true },
                                  "note": "Verified destination and refundable amount against original M-Pesa receipt."
                                }
                                """.formatted(subjectHash)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.subjectStatus").value("PROCESSING"));
    }

    @Test
    @Order(3)
    void bil05_bil08_bil10_sponsorBatchAgentCashAndReconciliation() throws Exception {
        // Seed sponsor & policies for BIL-05
        sponsorRepository.save(SponsorAccountEntity.builder()
                .sponsorId("spn_1")
                .name("AgriLend Coop")
                .status("ACTIVE")
                .creditLimitMinor(1_000_000L)
                .usedMinor(0L)
                .allowedMethodsCsv("BANK_TRANSFER,CREDIT")
                .build());
        billingCoreService.onPolicyIssued("pol_sp1", FARMER_2.toString(), "prd_1", 200_000L, 0L, "KES", "SPONSOR",
                Instant.now().plus(7, ChronoUnit.DAYS));
        mockMvc.perform(post("/api/billing/invoices")
                        .header("X-Service-Identity", "billing-consumer")
                        .header("Idempotency-Key", "evt_pol_sp1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "POLICY",
                                  "policyId": "pol_sp1",
                                  "payerType": "SPONSOR",
                                  "payerRef": "spn_1",
                                  "amountMinor": 200000,
                                  "currency": "KES"
                                }
                                """))
                .andExpect(status().isCreated());

        // BIL-05 Sponsor batch with CREDIT
        mockMvc.perform(post("/api/billing/sponsors/spn_1/batch")
                        .header("X-Actor-Id", "sp-admin")
                        .header("X-Auth-Roles", "SPONSOR_ADMIN")
                        .header("X-Sponsor-Id", "spn_1")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "policyIds": ["pol_sp1", "pol_unknown"],
                                  "paymentMethod": "CREDIT",
                                  "allocationMode": "ALL_OR_NOTHING"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COVERED_ON_CREDIT"))
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.rejected").value(1));

        // BIL-08 Agent Cash Collection with float debit
        floatRepository.save(AgentFloatEntity.builder()
                .agentId(AGENT_1.toString())
                .limitMinor(1_000_000L)
                .balanceMinor(500_000L)
                .currency("KES")
                .collectorActive(true)
                .build());
        billingCoreService.onPolicyIssued("pol_cash", FARMER_2.toString(), "prd_1", 150_000L, 0L, "KES", "FARMER",
                Instant.now().plus(7, ChronoUnit.DAYS));
        MvcResult cashInvRes = mockMvc.perform(post("/api/billing/invoices")
                        .header("X-Service-Identity", "billing-consumer")
                        .header("Idempotency-Key", "evt_pol_cash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "POLICY",
                                  "policyId": "pol_cash",
                                  "payerType": "FARMER",
                                  "payerRef": "%s",
                                  "amountMinor": 150000,
                                  "currency": "KES"
                                }
                                """.formatted(FARMER_2)))
                .andExpect(status().isCreated())
                .andReturn();
        String cashInvId = objectMapper.readTree(cashInvRes.getResponse().getContentAsString()).get("invoiceId").asString();

        mockMvc.perform(post("/api/billing/payments/offline")
                        .header("X-Actor-Id", AGENT_1.toString())
                        .header("X-Auth-Roles", "AGENT")
                        .header("X-Collector-Active", "true")
                        .header("X-Agent-Delegation", UUID.randomUUID().toString())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "invoiceId": "%s",
                                  "method": "AGENT_CASH",
                                  "amountMinor": 150000,
                                  "collectedAt": "%s",
                                  "receiptNumber": "RCP-JK-000481"
                                }
                                """.formatted(cashInvId, Instant.now().minus(5, ChronoUnit.MINUTES))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.floatBalanceMinor").value(350000));

        // BIL-10 Statement reconciliation detects clean match for RGH7K2LM91
        clients.putStatementFile("vt_stmt_1", "raw-csv", List.of(
                new BillingExternalClients.StatementLine("RGH7K2LM91", 324_000L, "KES",
                        LocalDate.now().toString(), payRef1, "COLLECTION")));
        mockMvc.perform(post("/api/billing/reconciliation/import")
                        .header("X-Actor-Id", "fin-recon")
                        .header("X-Auth-Roles", "FINANCE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "MPESA",
                                  "statementDate": "%s",
                                  "format": "CSV",
                                  "fileToken": "vt_stmt_1"
                                }
                                """.formatted(LocalDate.now())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.matched").value(1));
    }
}
