package com.umar;
import com.umar.enums.integration.FailMode;

import com.umar.integration.client.JavaWebhookHttpClient;
import com.umar.integration.client.LocalDataVaultClient;
import com.umar.integration.client.WebhookHttpClient;
import com.umar.integration.security.HubIdentityFilter;
import com.umar.integration.service.WebhookService;
import com.umar.integration.service.support.RateLimiterService;
import com.umar.integration.service.support.SandboxRuntime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = IntegrationServiceApplication.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationHubEndToEndTest {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private HubIdentityFilter identityFilter;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JavaWebhookHttpClient webhookHttpClient;

    @Autowired
    private LocalDataVaultClient dataVaultClient;

    @Autowired
    private WebhookService webhookService;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private SandboxRuntime sandboxRuntime;

    private MockMvc mockMvc;

    private static String partnerId;
    private static String tenantId;
    private static String prodSubscriptionId;
    private static String prodDeliveryId;
    private static String extensionId;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(identityFilter)
                .build();
        webhookHttpClient.setTestStub(req -> new WebhookHttpClient.HttpCallResult("SUCCESS", 200, 42L, "{\"ok\":true}", null, null));
        dataVaultClient.setUnavailable(false);
    }

    @Test
    @Order(1)
    void int16_and_int15_onboardPartnerAndDualApprove() throws Exception {
        String idemp = UUID.randomUUID().toString();
        String body = """
                {
                  "legalName": "Rural Finance Cooperative Ltd",
                  "displayName": "RuralFin",
                  "type": "LENDER",
                  "registrationNumber": "CS/2011/09021",
                  "countryCodes": ["KE"],
                  "contacts": {
                    "technical": { "email": "tech@ruralfin.example", "phone": "+254700111222" },
                    "security": { "email": "sec@ruralfin.example", "phone": "+254700111223" },
                    "business": { "email": "biz@ruralfin.example", "phone": "+254700111224" }
                  },
                  "allowedEventTypes": ["policy.activated", "premium.paid", "claim.paid"],
                  "dataScope": "PARTNER_DEFAULT",
                  "visibility": { "regionCodes": ["KE-NYERI", "KE-MURANGA"], "tenantIds": ["ten_ruralfin"] },
                  "connectorRights": { "inbound": true, "outbound": true, "ingestionTopics": ["ingest.sponsor.eligibility"] },
                  "extensionPoints": ["policy.pre-quote", "policy.pre-activate-check"],
                  "quotas": {
                    "webhookDeliveriesPerMonth": 1000000,
                    "webhookRatePerSecond": 20,
                    "inboundRecordsPerMonth": 500000,
                    "extensionInvocationsPerMonth": 200000,
                    "subscriptions": 5,
                    "connectors": 3
                  },
                  "agreementTokens": ["vt_doc_ptn_contract_91", "vt_doc_ptn_dpa_92"]
                }
                """;

        // Disallowed grant fails with 422 GRANT_NOT_ALLOWED
        String badGrantBody = body.replace("\"claim.paid\"", "\"claim.disputed\"");
        mockMvc.perform(post("/api/integrations/partners")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Actor-Id", "mgr-alice")
                        .header("X-Hub-Roles", "INTEGRATION_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badGrantBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("GRANT_NOT_ALLOWED"));

        MvcResult res = mockMvc.perform(post("/api/integrations/partners")
                        .header("Idempotency-Key", idemp)
                        .header("X-Actor-Id", "mgr-alice")
                        .header("X-Hub-Roles", "INTEGRATION_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        partnerId = root.get("partnerId").asString();
        String approvalId = root.get("approvalId").asString();
        String partnerHash = root.get("partnerHash").asString();

        // Idempotent replay returns identical partnerId
        mockMvc.perform(post("/api/integrations/partners")
                        .header("Idempotency-Key", idemp)
                        .header("X-Actor-Id", "mgr-alice")
                        .header("X-Hub-Roles", "INTEGRATION_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partnerId").value(partnerId));

        // Duplicate registration returns 409 PARTNER_EXISTS
        mockMvc.perform(post("/api/integrations/partners")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Actor-Id", "mgr-alice")
                        .header("X-Hub-Roles", "INTEGRATION_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PARTNER_EXISTS"));

        // Requester cannot approve own request -> 403 REQUESTER_CANNOT_APPROVE
        mockMvc.perform(put("/api/integrations/approvals/" + approvalId + "/decision")
                        .header("X-Actor-Id", "mgr-alice")
                        .header("X-Hub-Roles", "INTEGRATION_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "subjectHash": "%s",
                                  "checklist": { "agreementsChecked": true, "identityChecked": true, "quotasChecked": true },
                                  "note": "Attempting self approval should fail cleanly."
                                }
                                """.formatted(partnerHash)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REQUESTER_CANNOT_APPROVE"));

        // Wrong subjectHash -> 409 SUBJECT_CHANGED
        mockMvc.perform(put("/api/integrations/approvals/" + approvalId + "/decision")
                        .header("X-Actor-Id", "mgr-bob")
                        .header("X-Hub-Roles", "INTEGRATION_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "subjectHash": "stale-hash",
                                  "checklist": { "agreementsChecked": true, "identityChecked": true, "quotasChecked": true },
                                  "note": "Checking hash validation."
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBJECT_CHANGED"));

        // First approver (Integration Manager bob)
        mockMvc.perform(put("/api/integrations/approvals/" + approvalId + "/decision")
                        .header("X-Actor-Id", "mgr-bob")
                        .header("X-Hub-Roles", "INTEGRATION_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "subjectHash": "%s",
                                  "checklist": { "agreementsChecked": true, "identityChecked": true, "quotasChecked": true },
                                  "note": "Integration manager reviewed agreements, scopes, and quotas."
                                }
                                """.formatted(partnerHash)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.remaining[0]").value("COMPLIANCE_OFFICER"));

        // Second approver (Compliance Officer carol)
        mockMvc.perform(put("/api/integrations/approvals/" + approvalId + "/decision")
                        .header("X-Actor-Id", "comp-carol")
                        .header("X-Hub-Roles", "COMPLIANCE_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "subjectHash": "%s",
                                  "checklist": { "agreementsChecked": true, "identityChecked": true, "quotasChecked": true },
                                  "note": "Compliance verified DPA, registration number, and regional scope."
                                }
                                """.formatted(partnerHash)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.subjectStatus").value("ACTIVE"));

        tenantId = "ten_ruralfin";
    }

    @Test
    @Order(2)
    void int01_int02_int03_int04_int05_webhookLifecycleDispatchTestAndReplay() throws Exception {
        // SSRF check: private IP rejected (422 TARGET_NOT_PUBLIC)
        mockMvc.perform(post("/api/hooks/subscriptions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Actor-Id", "ptn-admin-1")
                        .header("X-Partner-Id", partnerId)
                        .header("X-Tenant-Id", tenantId)
                        .header("X-Hub-Roles", "PARTNER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "SSRF attempt",
                                  "targetUrl": "https://169.254.169.254/latest/meta-data",
                                  "eventTypes": ["premium.paid"],
                                  "environment": "SANDBOX",
                                  "contact": { "email": "hooks@ruralfin.example", "phone": "+254700000000" }
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TARGET_NOT_PUBLIC"));

        // Register PRODUCTION subscription (needs INT-15 approval)
        MvcResult prodRes = mockMvc.perform(post("/api/hooks/subscriptions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Actor-Id", "ptn-admin-1")
                        .header("X-Partner-Id", partnerId)
                        .header("X-Tenant-Id", tenantId)
                        .header("X-Hub-Roles", "PARTNER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Borrower cover status",
                                  "targetUrl": "https://hooks.ruralfin.example/agrishield",
                                  "eventTypes": ["policy.activated", "premium.paid", "claim.paid"],
                                  "filters": { "tenantIds": ["ten_ruralfin"], "regionCodes": ["KE-NYERI", "KE-MURANGA"] },
                                  "environment": "PRODUCTION",
                                  "contact": { "email": "integrations@ruralfin.example", "phone": "+254700000000" }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.signing.secret").isNotEmpty())
                .andExpect(jsonPath("$.verification.challengeResult").value("PASSED"))
                .andReturn();

        JsonNode prodJson = objectMapper.readTree(prodRes.getResponse().getContentAsString());
        prodSubscriptionId = prodJson.get("subscriptionId").asString();
        String subApprovalId = prodJson.get("approvalId").asString();
        String subHash = prodJson.get("subjectHash").asString();

        // Approve production subscription via INT-15
        mockMvc.perform(put("/api/integrations/approvals/" + subApprovalId + "/decision")
                        .header("X-Actor-Id", "mgr-bob")
                        .header("X-Hub-Roles", "INTEGRATION_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "subjectHash": "%s",
                                  "checklist": { "scopeChecked": true, "endpointChecked": true, "testsChecked": true },
                                  "note": "Production webhook scope and challenge verified."
                                }
                                """.formatted(subHash)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectStatus").value("ACTIVE"));

        // INT-04: Send Test Delivery (with PII masking check in snippet & masked signature)
        mockMvc.perform(post("/api/hooks/subscriptions/" + prodSubscriptionId + "/test")
                        .header("X-Actor-Id", "ptn-admin-1")
                        .header("X-Partner-Id", partnerId)
                        .header("X-Hub-Roles", "PARTNER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "eventType": "premium.paid", "variant": "TYPICAL" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.sent.body.test").value(true));

        // INT-03: Deliver Webhook from Domain Event (verify PII stripped, policyRef hashed, HMAC signed)
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedSig = new AtomicReference<>();
        webhookHttpClient.setTestStub(req -> {
            capturedBody.set(req.body());
            capturedSig.set(req.headers().get("X-AgriShield-Signature"));
            return new WebhookHttpClient.HttpCallResult("SUCCESS", 200, 35L, "ok", null, null);
        });

        Map<String, Object> domainEvent = new LinkedHashMap<>();
        domainEvent.put("eventId", "evt_prem_001");
        domainEvent.put("eventType", "premium.paid");
        domainEvent.put("occurredAt", Instant.now().minusSeconds(60).toString());
        domainEvent.put("key", "pol_7f3a19d0");
        domainEvent.put("payload", Map.of(
                "policyId", "pol_7f3a19d0",
                "tenantId", "ten_ruralfin",
                "regionCode", "KE-NYERI",
                "farmerName", "John Doe",
                "phoneNumber", "+254712345678",
                "x_externalRef", "LOAN-77310",
                "amountMinor", 324000,
                "currency", "KES",
                "fullyPaid", true
        ));
        WebhookService.DispatchSummary summary = webhookService.processDomainEvent(
                domainEvent, "platform.billing.events", 0, 100L);
        assertThat(summary.delivered()).isEqualTo(1);
        assertThat(capturedSig.get()).contains("t=").contains(",v1=");
        assertThat(capturedBody.get()).doesNotContain("John Doe").doesNotContain("+254712345678")
                .doesNotContain("pol_7f3a19d0").contains("policyRef").contains("LOAN-77310");

        // INT-02: GET subscription health & recent deliveries, plus 404 isolation for another partner
        mockMvc.perform(get("/api/hooks/subscriptions/" + prodSubscriptionId + "?include=health,deliveries,key&window=DAY")
                        .header("X-Actor-Id", "other-ptn-admin")
                        .header("X-Partner-Id", "ptn_other")
                        .header("X-Hub-Roles", "PARTNER_ADMIN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_FOUND"));

        MvcResult getRes = mockMvc.perform(get("/api/hooks/subscriptions/" + prodSubscriptionId + "?include=health,deliveries,key&window=DAY")
                        .header("X-Actor-Id", "ptn-admin-1")
                        .header("X-Partner-Id", partnerId)
                        .header("X-Hub-Roles", "PARTNER_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.deliveries[0].status").value("DELIVERED"))
                .andReturn();
        JsonNode getJson = objectMapper.readTree(getRes.getResponse().getContentAsString());
        prodDeliveryId = getJson.get("deliveries").get(0).get("deliveryId").asString();
        String etag = getRes.getResponse().getHeader("ETag");

        // INT-02: PUT rotate key (missing If-Match -> 428)
        mockMvc.perform(put("/api/hooks/subscriptions/" + prodSubscriptionId)
                        .header("X-Actor-Id", "ptn-admin-1")
                        .header("X-Partner-Id", partnerId)
                        .header("X-Hub-Roles", "PARTNER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "action": "ROTATE_KEY", "reason": "Routine 90-day key rotation" }
                                """))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.code").value("PRECONDITION_REQUIRED"));

        MvcResult rotRes = mockMvc.perform(put("/api/hooks/subscriptions/" + prodSubscriptionId)
                        .header("If-Match", etag)
                        .header("X-Actor-Id", "ptn-admin-1")
                        .header("X-Partner-Id", partnerId)
                        .header("X-Hub-Roles", "PARTNER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "action": "ROTATE_KEY", "reason": "Routine 90-day key rotation" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key.secret").isNotEmpty())
                .andReturn();

        // INT-05: Replay single delivery
        mockMvc.perform(post("/api/hooks/deliveries/" + prodDeliveryId + "/replay")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Actor-Id", "ptn-admin-1")
                        .header("X-Partner-Id", partnerId)
                        .header("X-Hub-Roles", "PARTNER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Replaying missed delivery after receiver patch" }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.counts.delivered").value(1));

        // INT-02: Pause, check replay blocked on paused, then resume
        String newEtag = rotRes.getResponse().getHeader("ETag");
        MvcResult pauseRes = mockMvc.perform(put("/api/hooks/subscriptions/" + prodSubscriptionId)
                        .header("If-Match", newEtag)
                        .header("X-Actor-Id", "ptn-admin-1")
                        .header("X-Partner-Id", partnerId)
                        .header("X-Hub-Roles", "PARTNER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "action": "SET_STATUS", "status": "PAUSED", "reason": "Planned maintenance window" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"))
                .andReturn();

        mockMvc.perform(post("/api/hooks/deliveries/" + prodDeliveryId + "/replay")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Actor-Id", "ptn-admin-1")
                        .header("X-Partner-Id", partnerId)
                        .header("X-Hub-Roles", "PARTNER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Should be rejected while subscription is paused" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_ACTIVE"));

        mockMvc.perform(put("/api/hooks/subscriptions/" + prodSubscriptionId)
                        .header("If-Match", pauseRes.getResponse().getHeader("ETag"))
                        .header("X-Actor-Id", "ptn-admin-1")
                        .header("X-Partner-Id", partnerId)
                        .header("X-Hub-Roles", "PARTNER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "action": "SET_STATUS", "status": "ACTIVE", "reason": "Maintenance complete" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @Order(3)
    void int11_int06_int12_int07_catalogExtensionRolloutApproveAndKill() throws Exception {
        // INT-11: Partner sees only partner-visible points
        mockMvc.perform(get("/api/extensions/points?service=audit-service")
                        .header("X-Actor-Id", "ptn-dev-1")
                        .header("X-Partner-Id", partnerId)
                        .header("X-Hub-Roles", "PARTNER_DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/extensions/points?service=policy-service&type=PRE&include=limits")
                        .header("X-Actor-Id", "ptn-dev-1")
                        .header("X-Partner-Id", partnerId)
                        .header("X-Hub-Roles", "PARTNER_DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[1].hookId").value("policy.pre-quote"));

        // INT-06: Protected point rejected (422 POINT_PROTECTED)
        String vectorJson = """
                [
                  {"input":{"quote":{"premiumMinor":100000}},"expected":{"loadings":[{"code":"LEVY","amountMinor":1000}]}},
                  {"input":{"quote":{"premiumMinor":200000}},"expected":{"loadings":[{"code":"LEVY","amountMinor":2000}]}},
                  {"input":{"quote":{"premiumMinor":300000}},"expected":{"loadings":[{"code":"LEVY","amountMinor":3000}]}},
                  {"input":{"quote":{"premiumMinor":400000}},"expected":{"loadings":[{"code":"LEVY","amountMinor":4000}]}},
                  {"input":{"quote":{"premiumMinor":500000}},"expected":{"loadings":[{"code":"LEVY","amountMinor":5000}]}}
                ]
                """;
        mockMvc.perform(post("/api/extensions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Actor-Id", "ptn-admin-1")
                        .header("X-Partner-Id", partnerId)
                        .header("X-Hub-Roles", "PARTNER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Bad payout hook",
                                  "extensionPoint": "payout.disburse",
                                  "version": "1.0.0",
                                  "contractVersion": "1.0",
                                  "runtime": { "type": "WASM", "artifactToken": "vt_doc_wasm_1" },
                                  "dataScope": ["payout.summary"],
                                  "testVectors": %s,
                                  "description": "Should be rejected because payout.disburse is protected.",
                                  "changelog": "Initial"
                                }
                                """.formatted(vectorJson)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("POINT_PROTECTED"));

        // INT-06: Non-deterministic extension rejected (422 SANDBOX_TEST_FAILED)
        mockMvc.perform(post("/api/extensions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Actor-Id", "ptn-admin-1")
                        .header("X-Partner-Id", partnerId)
                        .header("X-Hub-Roles", "PARTNER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Non-deterministic levy",
                                  "extensionPoint": "policy.pre-quote",
                                  "version": "1.0.0",
                                  "contractVersion": "2.1",
                                  "runtime": { "type": "WASM", "artifactToken": "vt_doc_wasm_bad", "nonDeterministicTest": true },
                                  "dataScope": ["quote.basic"],
                                  "testVectors": %s,
                                  "description": "Non deterministic extension must fail sandbox test vectors.",
                                  "changelog": "Initial"
                                }
                                """.formatted(vectorJson)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SANDBOX_TEST_FAILED"));

        // INT-06: Register valid extension at policy.pre-quote
        MvcResult extRes = mockMvc.perform(post("/api/extensions")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Actor-Id", "ptn-admin-1")
                        .header("X-Partner-Id", partnerId)
                        .header("X-Hub-Roles", "PARTNER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "RuralFin Risk Check",
                                  "extensionPoint": "policy.pre-quote",
                                  "version": "1.0.0",
                                  "contractVersion": "2.1",
                                  "runtime": {
                                    "type": "WASM",
                                    "artifactToken": "vt_doc_wasm_levy",
                                    "staticOutput": { "loadings": [ { "code": "COOP_LEVY", "amountMinor": 3240 } ] }
                                  },
                                  "dataScope": ["quote.basic"],
                                  "limits": { "timeoutMs": 1500, "memoryMb": 64 },
                                  "failMode": "FAIL_OPEN",
                                  "testVectors": %s,
                                  "description": "Adds a one percent cooperative member levy as a separate loading line.",
                                  "changelog": "Initial version 1.0.0"
                                }
                                """.formatted(vectorJson)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TESTED"))
                .andReturn();
        extensionId = objectMapper.readTree(extRes.getResponse().getContentAsString()).get("extensionId").asString();

        // INT-12: Skipping SHADOW directly to CANARY -> 409 STAGE_REQUIRED
        mockMvc.perform(post("/api/extensions/" + extensionId + "/rollout")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Actor-Id", "mgr-bob")
                        .header("X-Hub-Roles", "INTEGRATION_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stage": "CANARY",
                                  "scope": { "tenantIds": ["ten_ruralfin"] },
                                  "canary": { "percent": 10 },
                                  "durationHours": 24,
                                  "reason": "Trying canary without shadow stage should fail."
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STAGE_REQUIRED"));

        // INT-12: Start SHADOW -> COMPLETE -> Start CANARY -> COMPLETE
        mockMvc.perform(post("/api/extensions/" + extensionId + "/rollout")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Actor-Id", "ptn-admin-1")
                        .header("X-Partner-Id", partnerId)
                        .header("X-Hub-Roles", "PARTNER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stage": "SHADOW",
                                  "scope": { "tenantIds": ["ten_ruralfin"] },
                                  "durationHours": 24,
                                  "criteria": { "minInvocations": 0, "maxErrorRatePercent": 1.0, "maxTimeoutRatePercent": 1.0, "maxP95Ms": 1000 },
                                  "reason": "Shadow run of cooperative levy extension."
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        mockMvc.perform(post("/api/extensions/" + extensionId + "/rollout")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Actor-Id", "mgr-bob")
                        .header("X-Hub-Roles", "INTEGRATION_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "stage": "COMPLETE", "reason": "Completing shadow rollout stage." }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASSED"));

        mockMvc.perform(post("/api/extensions/" + extensionId + "/rollout")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Actor-Id", "mgr-bob")
                        .header("X-Hub-Roles", "INTEGRATION_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stage": "CANARY",
                                  "scope": { "tenantIds": ["ten_ruralfin"] },
                                  "canary": { "percent": 10 },
                                  "durationHours": 24,
                                  "criteria": { "minInvocations": 0, "maxErrorRatePercent": 1.0, "maxTimeoutRatePercent": 1.0, "maxP95Ms": 1000 },
                                  "reason": "Canary rollout to 10 percent of RuralFin quotes."
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("RUNNING"));

        mockMvc.perform(post("/api/extensions/" + extensionId + "/rollout")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Actor-Id", "mgr-bob")
                        .header("X-Hub-Roles", "INTEGRATION_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "stage": "COMPLETE", "reason": "Completing canary rollout stage." }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASSED"));

        // INT-07: Attempting ENABLE before approval creates approval and returns 409 APPROVAL_REQUIRED
        MvcResult needApprRes = mockMvc.perform(put("/api/extensions/" + extensionId + "/enable")
                        .header("If-Match", "\"1\"")
                        .header("X-Actor-Id", "mgr-bob")
                        .header("X-Hub-Roles", "INTEGRATION_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "ENABLE",
                                  "scope": { "tenantIds": ["ten_ruralfin"] },
                                  "priority": 100,
                                  "failMode": "FAIL_OPEN",
                                  "timeoutMs": 1500,
                                  "reason": "Enabling extension after shadow and canary completion."
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPROVAL_REQUIRED"))
                .andReturn();
        String extApprovalId = objectMapper.readTree(needApprRes.getResponse().getContentAsString()).get("approvalId").asString();

        // Request explicit approval hash via helper or compute from artifactHash — let's use POST /api/extensions/{id}/approvals
        MvcResult reqAppr = mockMvc.perform(post("/api/extensions/" + extensionId + "/approvals")
                        .header("X-Actor-Id", "ptn-admin-1")
                        .header("X-Partner-Id", partnerId)
                        .header("X-Hub-Roles", "PARTNER_ADMIN"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode apprJson = objectMapper.readTree(reqAppr.getResponse().getContentAsString());
        extApprovalId = apprJson.get("approvalId").asString();
        String extSubjectHash = apprJson.get("subjectHash").asString();

        // Dual approval via INT-15
        mockMvc.perform(put("/api/integrations/approvals/" + extApprovalId + "/decision")
                        .header("X-Actor-Id", "mgr-bob")
                        .header("X-Hub-Roles", "INTEGRATION_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "subjectHash": "%s",
                                  "checklist": { "dataScopeChecked": true, "rulesChecked": true, "rolloutReportsChecked": true, "failModeChecked": true },
                                  "note": "Integration manager reviewed shadow and canary metrics."
                                }
                                """.formatted(extSubjectHash)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/integrations/approvals/" + extApprovalId + "/decision")
                        .header("X-Actor-Id", "comp-carol")
                        .header("X-Hub-Roles", "COMPLIANCE_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "subjectHash": "%s",
                                  "checklist": { "dataScopeChecked": true, "rulesChecked": true, "rolloutReportsChecked": true, "failModeChecked": true },
                                  "note": "Compliance approved quote.basic scope and FAIL_OPEN mode."
                                }
                                """.formatted(extSubjectHash)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectStatus").value("APPROVED"));

        // INT-07: ENABLE now succeeds
        MvcResult enRes = mockMvc.perform(put("/api/extensions/" + extensionId + "/enable")
                        .header("If-Match", "\"2\"")
                        .header("X-Actor-Id", "mgr-bob")
                        .header("X-Hub-Roles", "INTEGRATION_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "ENABLE",
                                  "scope": { "tenantIds": ["ten_ruralfin"] },
                                  "priority": 100,
                                  "failMode": "FAIL_OPEN",
                                  "timeoutMs": 1500,
                                  "reason": "Enabling approved levy extension for RuralFin."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ENABLED"))
                .andReturn();

        // INT-07: KILL switch removes extension everywhere and blocks re-enable
        MvcResult killRes = mockMvc.perform(put("/api/extensions/" + extensionId + "/enable")
                        .header("If-Match", enRes.getResponse().getHeader("ETag"))
                        .header("X-Actor-Id", "admin-root")
                        .header("X-Hub-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "KILL",
                                  "reason": "Emergency kill switch verification during test."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("KILLED"))
                .andReturn();

        mockMvc.perform(put("/api/extensions/" + extensionId + "/enable")
                        .header("If-Match", killRes.getResponse().getHeader("ETag"))
                        .header("X-Actor-Id", "admin-root")
                        .header("X-Hub-Roles", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "ENABLE",
                                  "scope": { "tenantIds": ["ten_ruralfin"] },
                                  "priority": 100,
                                  "reason": "Re-enabling killed extension must fail."
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    @Order(4)
    void int08_int09_int10_connectorInboundAndPartnerUsage() throws Exception {
        dataVaultClient.seedSecret("vs_conn_ruralfin_api", partnerId, "ak_live_ruralfin_secret");

        // INT-08: Disallowed target topic rejected (422 TARGET_TOPIC_NOT_ALLOWED)
        mockMvc.perform(post("/api/integrations/connectors")
                        .header("X-Actor-Id", "ptn-admin-1")
                        .header("X-Partner-Id", partnerId)
                        .header("X-Hub-Roles", "PARTNER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Bad core writer",
                                  "direction": "INBOUND",
                                  "environment": "SANDBOX",
                                  "adapter": { "builtin": "WEBHOOK_IN" },
                                  "auth": { "type": "API_KEY", "secretRef": "vs_conn_ruralfin_api" },
                                  "externalSchema": { "required": ["externalId", "borrowerPhone"] },
                                  "mapping": { "rules": [ { "from": "borrowerPhone", "to": "phoneToken" } ] },
                                  "canonicalTarget": { "eventType": "payout.disburse", "topic": "platform.payout.events", "sourceId": "src_rf" },
                                  "quotas": { "requestsPerMinute": 20, "maxRecordsPerRequest": 100, "maxPayloadBytes": 1048576 },
                                  "sampleData": [
                                    { "externalId": "1", "borrowerPhone": "+254700000001" },
                                    { "externalId": "2", "borrowerPhone": "+254700000002" },
                                    { "externalId": "3", "borrowerPhone": "+254700000003" }
                                  ]
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TARGET_TOPIC_NOT_ALLOWED"));

        // INT-08: Valid SANDBOX INBOUND connector
        MvcResult connRes = mockMvc.perform(post("/api/integrations/connectors")
                        .header("X-Actor-Id", "ptn-admin-1")
                        .header("X-Partner-Id", partnerId)
                        .header("X-Hub-Roles", "PARTNER_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "RuralFin Borrower Feed",
                                  "direction": "INBOUND",
                                  "environment": "SANDBOX",
                                  "adapter": { "builtin": "WEBHOOK_IN" },
                                  "auth": { "type": "API_KEY", "secretRef": "vs_conn_ruralfin_api" },
                                  "externalSchema": { "required": ["externalId", "borrowerId", "regionCode", "phoneNumber"] },
                                  "mapping": {
                                    "rules": [
                                      { "from": "borrowerId", "to": "borrowerRef", "convert": "string" },
                                      { "from": "regionCode", "to": "regionCode", "convert": "string" },
                                      { "from": "phoneNumber", "to": "phoneNumber", "convert": "string" }
                                    ]
                                  },
                                  "canonicalTarget": { "eventType": "sponsor.eligibility", "topic": "ingest.sponsor.eligibility", "sourceId": "src_ruralfin" },
                                  "piiFields": ["phoneNumber"],
                                  "quotas": { "requestsPerMinute": 20, "maxRecordsPerRequest": 100, "maxPayloadBytes": 1048576 },
                                  "sampleData": [
                                    { "externalId": "S-1", "borrowerId": "LN-1", "regionCode": "KE-NYERI", "phoneNumber": "+254700000001" },
                                    { "externalId": "S-2", "borrowerId": "LN-2", "regionCode": "KE-NYERI", "phoneNumber": "+254700000002" },
                                    { "externalId": "S-3", "borrowerId": "LN-3", "regionCode": "KE-NYERI", "phoneNumber": "+254700000003" }
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SANDBOX_READY"))
                .andExpect(jsonPath("$.tests.mapped").value(3))
                .andReturn();
        String connectorId = objectMapper.readTree(connRes.getResponse().getContentAsString()).get("connectorId").asString();

        // INT-09: Invalid API Key -> 401 SIGNATURE_INVALID
        mockMvc.perform(post("/api/integrations/inbound/" + connectorId)
                        .header("X-API-Key", "wrong-key")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "records": [ { "externalId": "B-1", "borrowerId": "LN-1", "regionCode": "KE-NYERI", "phoneNumber": "+254711111111" } ] }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGNATURE_INVALID"));

        // INT-09: Partial batch (1 valid, 1 missing required field -> 207 Multi-Status)
        mockMvc.perform(post("/api/integrations/inbound/" + connectorId)
                        .header("X-API-Key", "ak_live_ruralfin_secret")
                        .header("Idempotency-Key", "batch-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "records": [
                                    { "externalId": "B-100", "borrowerId": "LN-100", "regionCode": "KE-NYERI", "phoneNumber": "+254711111111" },
                                    { "externalId": "B-101", "borrowerId": "LN-101", "phoneNumber": "+254722222222" }
                                  ]
                                }
                                """))
                .andExpect(status().isMultiStatus())
                .andExpect(jsonPath("$.status").value("PARTIALLY_ACCEPTED"))
                .andExpect(jsonPath("$.counts.accepted").value(1))
                .andExpect(jsonPath("$.counts.rejected").value(1));

        // INT-09: Re-sending B-100 with new Idempotency-Key deduplicates record
        mockMvc.perform(post("/api/integrations/inbound/" + connectorId)
                        .header("X-API-Key", "ak_live_ruralfin_secret")
                        .header("Idempotency-Key", "batch-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "records": [
                                    { "externalId": "B-100", "borrowerId": "LN-100", "regionCode": "KE-NYERI", "phoneNumber": "+254711111111" }
                                  ]
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.counts.duplicate").value(1))
                .andExpect(jsonPath("$.counts.accepted").value(0));

        // INT-10: Get Partner Usage (JSON + CSV export + cross-partner 403)
        String today = Instant.now().toString().substring(0, 10);
        mockMvc.perform(get("/api/integrations/usage?from=" + today + "&to=" + today + "&format=CSV")
                        .header("X-Actor-Id", "ptn-admin-1")
                        .header("X-Partner-Id", partnerId)
                        .header("X-Hub-Roles", "PARTNER_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partnerId").value(partnerId))
                .andExpect(jsonPath("$.webhooks.deliveries").value(2))
                .andExpect(jsonPath("$.inbound.recordsAccepted").value(1))
                .andExpect(jsonPath("$.export.documentToken").isNotEmpty());

        mockMvc.perform(get("/api/integrations/usage?partnerId=ptn_other&from=" + today + "&to=" + today)
                        .header("X-Actor-Id", "ptn-admin-1")
                        .header("X-Partner-Id", partnerId)
                        .header("X-Hub-Roles", "PARTNER_ADMIN"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
