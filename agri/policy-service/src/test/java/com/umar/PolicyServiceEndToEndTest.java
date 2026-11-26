package com.umar;

import com.umar.enums.auth.StepUpActionClass;
import com.umar.policy.client.PolicyExternalClients;
import com.umar.policy.entity.SubsidySchemeEntity;
import com.umar.policy.repository.SubsidySchemeRepository;
import com.umar.policy.security.PolicyIdentityFilter;
import com.umar.policy.service.PolicyLifecycleService;
import com.umar.policy.service.PolicyRateLimitService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = PolicyServiceApplication.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PolicyServiceEndToEndTest {

    @Autowired private WebApplicationContext wac;
    @Autowired private PolicyIdentityFilter identityFilter;
    private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PolicyExternalClients clients;
    @Autowired private PolicyLifecycleService lifecycleService;
    @Autowired private SubsidySchemeRepository subsidyRepository;
    @Autowired private PolicyRateLimitService rateLimitService;

    private static final UUID FARMER_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FARMER_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static String productId;
    private static String quoteId;
    private static String policyId1;
    private static String policyId2;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).addFilters(identityFilter).build();
        clients.reset();
        rateLimitService.reset();
        clients.setPlotOverride("pl_1", new PolicyExternalClients.PlotDetails(
                true, true, FARMER_1.toString(), "VALIDATED", "ACTIVE", 1.8, "HIGH",
                "KE-CENTRAL", "MAIZE", "RAINFED", LocalDate.now().plusDays(5), LocalDate.now().plusDays(125),
                true, false));
        clients.setPlotOverride("pl_2", new PolicyExternalClients.PlotDetails(
                true, true, FARMER_2.toString(), "VALIDATED", "ACTIVE", 2.0, "HIGH",
                "KE-CENTRAL", "MAIZE", "RAINFED", LocalDate.now().minusDays(60), LocalDate.now().plusDays(15),
                true, false));
        if (!subsidyRepository.existsById("KE_GOV_26")) {
            subsidyRepository.save(SubsidySchemeEntity.builder()
                    .schemeCode("KE_GOV_26")
                    .funder("Kenya Ministry of Agriculture")
                    .percentBps(3000)
                    .capMinor(500_000L)
                    .budgetMinor(50_000_000L)
                    .reservedMinor(0L)
                    .regionCodesCsv("KE-CENTRAL")
                    .cropCodesCsv("MAIZE")
                    .active(true)
                    .build());
        }
    }

    @Test
    @Order(1)
    void pol01_pol08_pol09_productGovernanceAndEligibility() throws Exception {
        String prodJson = """
                {
                  "productCode": "MAIZE_DROUGHT_LR",
                  "name": { "en": "Maize drought cover", "sw": "Bima ya ukame wa mahindi" },
                  "description": { "en": "Drought index cover for maize" },
                  "peril": "DROUGHT",
                  "index": {
                    "type": "NDVI_ANOMALY",
                    "source": "SATELLITE_NDVI",
                    "aggregation": "ANOMALY",
                    "baselineYears": 10,
                    "phases": [
                      { "name": "VEGETATIVE", "startOffsetDays": 20, "endOffsetDays": 60, "weight": 0.4 },
                      { "name": "FLOWERING", "startOffsetDays": 61, "endOffsetDays": 95, "weight": 0.6 }
                    ]
                  },
                  "trigger": {
                    "tiers": [
                      { "operator": "LT", "thresholdValue": -0.8, "payoutPercent": 25 },
                      { "operator": "LT", "thresholdValue": -1.2, "payoutPercent": 60 },
                      { "operator": "LT", "thresholdValue": -1.6, "payoutPercent": 100 }
                    ],
                    "corroboration": { "requiredSources": 2, "allowedSources": ["SATELLITE_NDVI", "RAINFALL"] }
                  },
                  "coverageWindow": { "anchor": "PLANTING_DATE", "startOffsetDays": 0, "endOffsetDays": 10, "minDays": 30, "maxDays": 150 },
                  "waitingPeriodDays": 7,
                  "enrollment": { "opensOn": "%s", "closesOn": "%s", "regionCodes": ["KE-CENTRAL"] },
                  "eligibility": { "cropCodes": ["MAIZE"], "regionCodes": ["KE-CENTRAL"], "minKycTier": "TIER_1" },
                  "sumInsured": { "mode": "PER_HECTARE", "minMinor": 2000000, "maxMinor": 5000000, "stepMinor": 500000, "currency": "KES" },
                  "pricing": { "rateCardRef": "RC_KE_2026_V1", "minPremiumMinor": 10000 },
                  "payment": { "payByDays": 7, "instalmentsAllowed": true },
                  "cancellation": { "coolingOffDays": 14, "refundPercentInCoolingOff": 100, "adminFeeMinor": 5000, "refundAfterCoverStart": "NONE" },
                  "renewal": { "reminderDaysBefore": [30, 14, 3], "priceChangeCapPercent": 15.0 },
                  "backtestRef": "sim_2026_10_0087",
                  "effectiveFrom": "%s",
                  "submitForApproval": true
                }
                """.formatted(LocalDate.now().minusDays(1), LocalDate.now().plusDays(60), LocalDate.now());

        MvcResult created = mockMvc.perform(post("/api/products")
                        .header("X-Actor-Id", "uw-author")
                        .header("X-Auth-Roles", "UNDERWRITER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(prodJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_APPROVAL"))
                .andReturn();
        productId = objectMapper.readTree(created.getResponse().getContentAsString()).get("productId").asString();

        // Author cannot approve -> 403 AUTHOR_CANNOT_APPROVE
        mockMvc.perform(put("/api/products/" + productId + "/versions/1/decision")
                        .header("X-Actor-Id", "uw-author")
                        .header("X-Auth-Roles", "UNDERWRITER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "checklist": { "backtestReviewed": true, "thresholdsReviewed": true, "pricingReviewed": true },
                                  "note": "Reviewing my own product draft is forbidden."
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTHOR_CANNOT_APPROVE"));

        // Second Underwriter approves
        mockMvc.perform(put("/api/products/" + productId + "/versions/1/decision")
                        .header("X-Actor-Id", "uw-second")
                        .header("X-Auth-Roles", "UNDERWRITER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "checklist": { "backtestReviewed": true, "thresholdsReviewed": true, "pricingReviewed": true },
                                  "note": "Actuarial loss ratio within target band and tiers verified."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_APPROVAL"));

        // Compliance Officer approves -> PUBLISHED (effectiveFrom is today)
        mockMvc.perform(put("/api/products/" + productId + "/versions/1/decision")
                        .header("X-Actor-Id", "co-officer")
                        .header("X-Auth-Roles", "COMPLIANCE_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "checklist": { "wordingReviewed": true, "refundRulesReviewed": true, "consentsReviewed": true },
                                  "note": "Wording, cooling-off and core consents match insurer policy."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // POL-09 List Eligible Products
        mockMvc.perform(get("/api/products/eligible")
                        .param("plotId", "pl_1")
                        .header("X-Actor-Id", FARMER_1.toString())
                        .header("X-Auth-Roles", "FARMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("ELIGIBLE"))
                .andExpect(jsonPath("$.nextAction").value("REQUEST_QUOTE"));
    }

    @Test
    @Order(2)
    void pol02_pol03_pol04_pol05_quoteFallbackIssueActivateAndView() throws Exception {
        // Underwriting AI down -> FALLBACK_TARIFF
        clients.setUnderwritingAiAvailable(false);
        MvcResult qRes = mockMvc.perform(post("/api/quotes")
                        .header("X-Actor-Id", FARMER_1.toString())
                        .header("X-Auth-Roles", "FARMER")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "plotId": "pl_1",
                                  "productId": "%s",
                                  "coverageLevel": "STANDARD",
                                  "channel": "APP",
                                  "language": "sw"
                                }
                                """.formatted(productId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scoreSource").value("FALLBACK_TARIFF"))
                .andReturn();
        quoteId = objectMapper.readTree(qRes.getResponse().getContentAsString()).get("quoteId").asString();
        clients.setUnderwritingAiAvailable(true);

        // POL-03 Issue Policy
        MvcResult pRes = mockMvc.perform(post("/api/policies")
                        .header("X-Actor-Id", FARMER_1.toString())
                        .header("X-Auth-Roles", "FARMER")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quoteId": "%s",
                                  "acceptance": { "termsVersion": "5", "channel": "APP", "evidence": { "type": "TAP_CONFIRM" } },
                                  "payerType": "FARMER"
                                }
                                """.formatted(quoteId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andReturn();
        policyId1 = objectMapper.readTree(pRes.getResponse().getContentAsString()).get("policyId").asString();

        // Simulate payment confirmation 8 days ago so waiting period is elapsed
        lifecycleService.onPremiumPaid(policyId1, Instant.now().minus(8, ChronoUnit.DAYS), true);

        // POL-04 Activate Policy
        mockMvc.perform(post("/api/policies/" + policyId1 + "/activate")
                        .header("X-Service-Identity", "activation-worker")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trigger\":\"SCHEDULED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.nextAction").value("NONE"));

        // POL-05 Owner view succeeds; non-owner gets 404
        mockMvc.perform(get("/api/policies/" + policyId1 + "?include=summary,premium,payout")
                        .header("X-Actor-Id", FARMER_1.toString())
                        .header("X-Auth-Roles", "FARMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.payout.state").value("NONE"));

        mockMvc.perform(get("/api/policies/" + policyId1)
                        .header("X-Actor-Id", FARMER_2.toString())
                        .header("X-Auth-Roles", "FARMER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POLICY_NOT_FOUND"));
    }

    @Test
    @Order(3)
    void pol06_pol07_cancelCoolingOffStepUpClaimBlockAndRenewals() throws Exception {
        // Issue policy 2 for FARMER_2 on pl_2
        MvcResult q2 = mockMvc.perform(post("/api/quotes")
                        .header("X-Actor-Id", FARMER_2.toString())
                        .header("X-Auth-Roles", "FARMER")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "plotId": "pl_2",
                                  "productId": "%s",
                                  "coverageLevel": "BASIC",
                                  "channel": "APP"
                                }
                                """.formatted(productId)))
                .andExpect(status().isOk())
                .andReturn();
        String quoteId2 = objectMapper.readTree(q2.getResponse().getContentAsString()).get("quoteId").asString();

        MvcResult p2 = mockMvc.perform(post("/api/policies")
                        .header("X-Actor-Id", FARMER_2.toString())
                        .header("X-Auth-Roles", "FARMER")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quoteId": "%s",
                                  "acceptance": { "termsVersion": "5", "channel": "APP", "evidence": { "type": "TAP_CONFIRM" } }
                                }
                                """.formatted(quoteId2)))
                .andExpect(status().isCreated())
                .andReturn();
        policyId2 = objectMapper.readTree(p2.getResponse().getContentAsString()).get("policyId").asString();

        lifecycleService.onPremiumPaid(policyId2, Instant.now().minus(1, ChronoUnit.DAYS), true);

        // Cancel paid policy without step-up -> 401 STEP_UP_REQUIRED
        mockMvc.perform(post("/api/policies/" + policyId2 + "/cancel")
                        .header("X-Actor-Id", FARMER_2.toString())
                        .header("X-Auth-Roles", "FARMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"FARMER_REQUEST\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"));

        // With valid step-up token -> 200 FULL_LESS_FEE refund & subsidy release
        clients.registerStepUp("su_valid_cancel", FARMER_2, StepUpActionClass.POLICY_CANCEL);
        mockMvc.perform(post("/api/policies/" + policyId2 + "/cancel")
                        .header("X-Actor-Id", FARMER_2.toString())
                        .header("X-Auth-Roles", "FARMER")
                        .header("X-Step-Up-Token", "su_valid_cancel")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"FARMER_REQUEST\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.refund.basis").value("FULL_LESS_FEE"));

        // Claim on policy 1 blocks cancellation -> 409 CLAIM_IN_PROGRESS
        lifecycleService.onClaimOpened(policyId1);
        clients.registerStepUp("su_valid_cancel_1", FARMER_1, StepUpActionClass.POLICY_CANCEL);
        mockMvc.perform(post("/api/policies/" + policyId1 + "/cancel")
                        .header("X-Actor-Id", FARMER_1.toString())
                        .header("X-Auth-Roles", "FARMER")
                        .header("X-Step-Up-Token", "su_valid_cancel_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"FARMER_REQUEST\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLAIM_IN_PROGRESS"));

        // POL-07 Upcoming renewals
        mockMvc.perform(get("/api/policies/renewals?daysAhead=120")
                        .header("X-Actor-Id", FARMER_1.toString())
                        .header("X-Auth-Roles", "FARMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
