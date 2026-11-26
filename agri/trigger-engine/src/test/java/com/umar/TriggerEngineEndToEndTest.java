package com.umar;

import com.umar.trigger.client.TriggerExternalClients;
import com.umar.trigger.repository.PolicyBindingRepository;
import com.umar.trigger.repository.TriggerOutboxEventRepository;
import com.umar.trigger.security.TriggerIdentityFilter;
import com.umar.trigger.service.TriggerEvaluationService;
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

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TriggerEngineApplication.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TriggerEngineEndToEndTest {

    @Autowired private WebApplicationContext wac;
    @Autowired private TriggerIdentityFilter identityFilter;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TriggerExternalClients clients;
    @Autowired private TriggerEvaluationService evaluationService;
    @Autowired private PolicyBindingRepository bindingRepository;
    @Autowired private TriggerOutboxEventRepository outboxRepository;

    private MockMvc mockMvc;
    private static final UUID FARMER_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FARMER_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static String ruleId;
    private static String definitionHash;
    private static String decisionId;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).addFilters(identityFilter).build();
    }

    @Test
    @Order(1)
    void trg01_trg04_trg06_trg08_createSimulateAndApproveRule() throws Exception {
        String prodHash = clients.sha256("prd_maize:1:DROUGHT");
        clients.registerProductContractHash("prd_maize", 1, prodHash);

        // TRG-01 Contract mismatch -> 422 CONTRACT_MISMATCH
        mockMvc.perform(post("/api/triggers/rules")
                        .header("X-Actor-Id", "uw-author")
                        .header("X-Auth-Roles", "UNDERWRITER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ruleCode": "KE_DROUGHT_V1",
                                  "productId": "prd_maize",
                                  "productVersion": 1,
                                  "contractualDefinitionHash": "wrong_hash",
                                  "peril": "DROUGHT",
                                  "regionCodes": ["KE-NYERI"],
                                  "tiers": [{"tierId": "T1", "threshold": 60.0, "payoutPercent": 50}],
                                  "backtestRef": "sim_pre",
                                  "effectiveFrom": "%s",
                                  "submitForApproval": true
                                }
                                """.formatted(LocalDate.now().plusDays(7))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONTRACT_MISMATCH"));

        // TRG-01 Valid rule creation
        MvcResult createRes = mockMvc.perform(post("/api/triggers/rules")
                        .header("X-Actor-Id", "uw-author")
                        .header("X-Auth-Roles", "UNDERWRITER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ruleCode": "KE_DROUGHT_V1",
                                  "productId": "prd_maize",
                                  "productVersion": 1,
                                  "contractualDefinitionHash": "%s",
                                  "peril": "DROUGHT",
                                  "regionCodes": ["KE-NYERI"],
                                  "tiers": [
                                    {"tierId": "T1", "threshold": 70.0, "payoutPercent": 30},
                                    {"tierId": "T2", "threshold": 50.0, "payoutPercent": 60}
                                  ],
                                  "backtestRef": "sim_pre_01",
                                  "effectiveFrom": "%s",
                                  "submitForApproval": true
                                }
                                """.formatted(prodHash, LocalDate.now().plusDays(7))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn();
        JsonNode rn = objectMapper.readTree(createRes.getResponse().getContentAsString());
        ruleId = rn.get("ruleId").asString();
        definitionHash = rn.get("definitionHash").asString();

        // TRG-04 & TRG-08 Simulation + ETag 304
        MvcResult simRes = mockMvc.perform(post("/api/triggers/simulate")
                        .header("X-Actor-Id", "uw-author")
                        .header("X-Auth-Roles", "UNDERWRITER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "ruleId": "%s",
                                  "ruleVersion": 1,
                                  "regionCodes": ["KE-NYERI"],
                                  "fromDate": "2016-01-01",
                                  "toDate": "2026-01-01",
                                  "seasons": 10,
                                  "outputFormat": "SUMMARY"
                                }
                                """.formatted(ruleId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seasonsCovered").value(10))
                .andReturn();
        String simId = objectMapper.readTree(simRes.getResponse().getContentAsString()).get("simulationId").asString();
        mockMvc.perform(get("/api/triggers/simulations/" + simId)
                        .header("X-Actor-Id", "uw-author")
                        .header("X-Auth-Roles", "UNDERWRITER")
                        .header("If-None-Match", "\"1\""))
                .andExpect(status().isNotModified());

        // TRG-06 Author cannot approve -> 403 AUTHOR_CANNOT_APPROVE
        mockMvc.perform(put("/api/triggers/rules/" + ruleId + "/versions/1/decision")
                        .header("X-Actor-Id", "uw-author")
                        .header("X-Auth-Roles", "UNDERWRITER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "definitionHash": "%s",
                                  "comment": "Attempted self-approval by rule author."
                                }
                                """.formatted(definitionHash)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTHOR_CANNOT_APPROVE"));

        // Second Underwriter + Compliance Officer -> ACTIVE
        mockMvc.perform(put("/api/triggers/rules/" + ruleId + "/versions/1/decision")
                        .header("X-Actor-Id", "uw-peer")
                        .header("X-Auth-Roles", "UNDERWRITER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "definitionHash": "%s",
                                  "comment": "Verified tiers, basis risk and historical burning cost."
                                }
                                """.formatted(definitionHash)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

        mockMvc.perform(put("/api/triggers/rules/" + ruleId + "/versions/1/decision")
                        .header("X-Actor-Id", "comp-1")
                        .header("X-Auth-Roles", "COMPLIANCE_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "definitionHash": "%s",
                                  "comment": "Confirmed governance, terms matching and backtest sign-off."
                                }
                                """.formatted(definitionHash)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @Order(2)
    void trg02_trg03_trg05_trg07_evaluateCorroborationConflictAndTwoPersonReview() throws Exception {
        evaluationService.onPolicyActivated("evt_pol_t1", "pol_t1", FARMER_1.toString(), "plt_t1",
                "KE-NYERI", "prd_maize", ruleId, 1, 100_000L, "KES");
        clients.setRegionRainfall("KE-NYERI", 42.0);
        clients.setForceCorroborationConflict(true);

        MvcResult evalRes = mockMvc.perform(post("/api/triggers/evaluate")
                        .header("X-Service-Identity", "trigger-scheduler")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "evaluationType": "SCHEDULED",
                                  "ruleId": "%s",
                                  "regionCodes": ["KE-NYERI"],
                                  "windowStart": "2026-10-01",
                                  "windowEnd": "2026-10-31",
                                  "windowId": "2026-10-W1"
                                }
                                """.formatted(ruleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.breachedCount").value(1))
                .andExpect(jsonPath("$.reviewRequiredCount").value(1))
                .andReturn();
        decisionId = objectMapper.readTree(evalRes.getResponse().getContentAsString())
                .get("sampleDecisions").get(0).get("decisionId").asString();

        // TRG-03 Farmer reads own explanation (no internal evidence array), non-owner gets 404
        mockMvc.perform(get("/api/triggers/decisions/" + decisionId)
                        .header("X-Actor-Id", FARMER_1.toString())
                        .header("X-Auth-Roles", "FARMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("BREACH"))
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.evidence").doesNotExist());

        mockMvc.perform(get("/api/triggers/decisions/" + decisionId)
                        .header("X-Actor-Id", FARMER_2.toString())
                        .header("X-Auth-Roles", "FARMER"))
                .andExpect(status().isNotFound());

        // TRG-05 Events summary list
        mockMvc.perform(get("/api/triggers/events?peril=DROUGHT")
                        .header("X-Actor-Id", "uw-peer")
                        .header("X-Auth-Roles", "UNDERWRITER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events[0].breachedPolicies").value(1));

        // TRG-07 Two-person review on conflicted decision -> first moves to PENDING_SECOND_REVIEW, second finalizes FINAL BREACH
        mockMvc.perform(put("/api/triggers/decisions/" + decisionId + "/review")
                        .header("X-Actor-Id", "rev-1")
                        .header("X-Auth-Roles", "TRIGGER_REVIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "CONFIRM_BREACH",
                                  "note": "Checked nearby Nyeri stations; drought index confirmed below 50mm."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_SECOND_REVIEW"));

        mockMvc.perform(put("/api/triggers/decisions/" + decisionId + "/review")
                        .header("X-Actor-Id", "rev-2")
                        .header("X-Auth-Roles", "TRIGGER_REVIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "CONFIRM_BREACH",
                                  "note": "Independent confirmation completed; corroboration conflict explained by gauge outage."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINAL"))
                .andExpect(jsonPath("$.outcome").value("BREACH"));

        assertThat(outboxRepository.findAll().stream().anyMatch(e -> "threshold.breached".equals(e.getEventType()))).isTrue();
    }
}
