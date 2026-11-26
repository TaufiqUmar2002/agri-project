package com.umar;

import com.umar.claims.client.ClaimsExternalClients;
import com.umar.claims.domain.ClaimEntity;
import com.umar.claims.repository.ClaimsOutboxEventRepository;
import com.umar.claims.security.ClaimsIdentityFilter;
import com.umar.claims.service.ClaimsSagaService;
import com.umar.enums.auth.StepUpActionClass;
import com.umar.enums.claims.ClaimStatus;
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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = ClaimsServiceApplication.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ClaimsServiceEndToEndTest {

    @Autowired private WebApplicationContext wac;
    @Autowired private ClaimsIdentityFilter identityFilter;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ClaimsExternalClients clients;
    @Autowired private ClaimsSagaService sagaService;
    @Autowired private ClaimsOutboxEventRepository outboxRepository;

    private MockMvc mockMvc;
    private static final String FARMER_1 = "11111111-1111-1111-1111-111111111111";
    private static final String FARMER_2 = "22222222-2222-2222-2222-222222222222";
    private static String claimId1;
    private static String claimId2;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).addFilters(identityFilter).build();
    }

    @Test
    @Order(1)
    void clm01_clm02_clm04_clm08_autoApprovePayDisputeAndResolveSupplementary() throws Exception {
        clients.registerPolicyTerms("pol_c1", new ClaimsExternalClients.PolicyTermsView(
                true, 100_000L, 0L, clients.sha256("pol_c1:t"), "KES", FARMER_1, "plt_c1", "KE-NYERI"));
        clients.registerTriggerDecision("dec_c1", new ClaimsExternalClients.TriggerStatusView(true, 60.0, "T1"));
        clients.markAgentSoldPolicy("agt-conflict", "pol_c1");

        // CLM-01 Auto-approved <= 100,000 minor
        ClaimEntity clm = sagaService.onThresholdBreached(
                "evt_c1", "dec_c1", "pol_c1", "rul_1", 1, "2026-10-W1", "T1", 60.0, Instant.now());
        claimId1 = clm.getClaimId();
        assertThat(clm.getStatus()).isEqualTo(ClaimStatus.APPROVED);

        // CLM-11 Payout initiated & completed -> PAID
        sagaService.onPayoutEvent("evt_pi1", "payout.initiated", claimId1, "pay_1", 60_000L, null, null);
        ClaimEntity paid = sagaService.onPayoutEvent("evt_pc1", "payout.completed", claimId1, "pay_1", 60_000L, null, null);
        assertThat(paid.getStatus()).isEqualTo(ClaimStatus.PAID);

        // CLM-02 Farmer views own claim; non-owner gets 404
        mockMvc.perform(get("/api/claims/" + claimId1)
                        .header("X-Actor-Id", FARMER_1)
                        .header("X-Auth-Roles", "FARMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
        mockMvc.perform(get("/api/claims/" + claimId1)
                        .header("X-Actor-Id", FARMER_2)
                        .header("X-Auth-Roles", "FARMER"))
                .andExpect(status().isNotFound());

        // CLM-04 Create Dispute
        MvcResult dspRes = mockMvc.perform(post("/api/claims/" + claimId1 + "/dispute")
                        .header("X-Actor-Id", FARMER_1)
                        .header("X-Auth-Roles", "FARMER")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reasonCode": "CALCULATION_ERROR",
                                  "explanation": "Sum insured tier T1 calculation should include second season addendum.",
                                  "photoTokens": ["vt_ph_dispute_1"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.claimStatus").value("DISPUTED"))
                .andReturn();
        String disputeId = objectMapper.readTree(dspRes.getResponse().getContentAsString()).get("disputeId").asString();

        // CLM-08 Evidence not reviewed -> 422
        mockMvc.perform(put("/api/claims/" + claimId1 + "/disputes/" + disputeId + "/resolution")
                        .header("X-Actor-Id", "rev-indep")
                        .header("X-Auth-Roles", "CLAIMS_REVIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "outcome": "UPHELD",
                                  "explanation": "Recalculated addendum; supplementary payout warranted.",
                                  "evidenceReviewed": false,
                                  "supplementary": true,
                                  "supplementaryAmountMinor": 15000
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EVIDENCE_NOT_REVIEWED"));

        // First approval -> PENDING_APPROVAL, then Compliance approves supplementary claim
        MvcResult r1 = mockMvc.perform(put("/api/claims/" + claimId1 + "/disputes/" + disputeId + "/resolution")
                        .header("X-Actor-Id", "rev-indep")
                        .header("X-Auth-Roles", "CLAIMS_REVIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "outcome": "UPHELD",
                                  "explanation": "Recalculated addendum; supplementary payout warranted.",
                                  "evidenceReviewed": true,
                                  "supplementary": true,
                                  "supplementaryAmountMinor": 15000
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn();
        String proposalHash = objectMapper.readTree(r1.getResponse().getContentAsString()).get("proposalHash").asString();

        mockMvc.perform(put("/api/claims/" + claimId1 + "/disputes/" + disputeId + "/resolution")
                        .header("X-Actor-Id", "comp-1")
                        .header("X-Auth-Roles", "COMPLIANCE_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "outcome": "UPHELD",
                                  "explanation": "Recalculated addendum; supplementary payout warranted.",
                                  "evidenceReviewed": true,
                                  "supplementary": true,
                                  "supplementaryAmountMinor": 15000,
                                  "proposalHash": "%s"
                                }
                                """.formatted(proposalHash)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.supplementaryClaimId").isNotEmpty());
    }

    @Test
    @Order(2)
    void clm03_clm05_clm07_clm06_clm09_clm12_clm13_confirmationDualApproveRetryAndReconciliation() throws Exception {
        clients.registerPolicyTerms("pol_c2", new ClaimsExternalClients.PolicyTermsView(
                true, 500_000L, 0L, clients.sha256("pol_c2:t"), "KES", FARMER_2, "plt_c2", "KE-NYERI"));
        clients.registerTriggerDecision("dec_c2", new ClaimsExternalClients.TriggerStatusView(true, 60.0, "T1"));
        clients.setAutomatedPayoutConsent(FARMER_2, false); // Requires farmer confirmation

        ClaimEntity held = sagaService.onThresholdBreached(
                "evt_c2", "dec_c2", "pol_c2", "rul_1", 1, "2026-10-W1", "T1", 60.0, Instant.now());
        claimId2 = held.getClaimId();
        assertThat(held.getStatus()).isEqualTo(ClaimStatus.ON_HOLD);

        // Agent delegation rejected on confirm-payout -> 403 DELEGATION_NOT_ALLOWED
        mockMvc.perform(post("/api/claims/" + claimId2 + "/confirm-payout")
                        .header("X-Actor-Id", FARMER_2)
                        .header("X-Auth-Roles", "AGENT")
                        .header("X-Delegation-Id", UUID.randomUUID().toString())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stepUpToken": "stp_1",
                                  "acknowledgedAmountMinor": 300000,
                                  "channel": "APP"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("DELEGATION_NOT_ALLOWED"));

        // Farmer confirms -> moves to VALIDATED (since 300,000 > 100,000)
        clients.registerStepUpToken("stp_confirm_1", FARMER_2, StepUpActionClass.CLAIM_CONFIRM);
        MvcResult confRes = mockMvc.perform(post("/api/claims/" + claimId2 + "/confirm-payout")
                        .header("X-Actor-Id", FARMER_2)
                        .header("X-Auth-Roles", "FARMER")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stepUpToken": "stp_confirm_1",
                                  "acknowledgedAmountMinor": 300000,
                                  "channel": "APP"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andReturn();

        String claimHash = objectMapper.readTree(mockMvc.perform(get("/api/claims/" + claimId2)
                        .header("X-Actor-Id", "rev-1").header("X-Auth-Roles", "CLAIMS_REVIEWER"))
                .andReturn().getResponse().getContentAsString()).get("claimHash").asString();

        // CLM-03 Dual approval required (>= 200,000 minor)
        MvcResult a1 = mockMvc.perform(post("/api/claims/" + claimId2 + "/approve")
                        .header("X-Actor-Id", "rev-1")
                        .header("X-Auth-Roles", "CLAIMS_REVIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "claimHash": "%s",
                                  "note": "First independent review checked policy and trigger breach."
                                }
                                """.formatted(claimHash)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andReturn();
        String hashAfter1 = objectMapper.readTree(mockMvc.perform(get("/api/claims/" + claimId2)
                        .header("X-Actor-Id", "rev-2").header("X-Auth-Roles", "CLAIMS_REVIEWER"))
                .andReturn().getResponse().getContentAsString()).get("claimHash").asString();

        mockMvc.perform(post("/api/claims/" + claimId2 + "/approve")
                        .header("X-Actor-Id", "rev-2")
                        .header("X-Auth-Roles", "CLAIMS_REVIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "claimHash": "%s",
                                  "note": "Second independent review approved large claim payout."
                                }
                                """.formatted(hashAfter1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Fail payout and test CLM-05 Retry
        sagaService.onPayoutEvent("evt_pf2", "payout.failed", claimId2, "pay_2", 300_000L, "TIMEOUT", "SUPPORT_RETRY");
        mockMvc.perform(post("/api/claims/" + claimId2 + "/retry")
                        .header("X-Actor-Id", "sup-1")
                        .header("X-Auth-Roles", "SUPPORT")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "route": "SAME_WALLET",
                                  "useCurrentDestination": false,
                                  "reason": "Retrying after transient mobile money gateway timeout."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // CLM-06 Reconciliation & CLM-09 List
        mockMvc.perform(get("/api/claims?status=APPROVED&farmerId=" + FARMER_2)
                        .header("X-Actor-Id", "sup-1")
                        .header("X-Auth-Roles", "SUPPORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].claimId").value(claimId2));

        mockMvc.perform(get("/api/claims/reports/reconciliation?from=" + LocalDate.now() + "&to=" + LocalDate.now())
                        .header("X-Actor-Id", "fin-1")
                        .header("X-Auth-Roles", "FINANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.claimsPaid").value(1));

        // CLM-13 No-payout record on policy expiry
        assertThat(sagaService.onPolicyExpired("evt_exp1", "pol_unclaimed")).isNotNull();
        assertThat(outboxRepository.findAll().stream().anyMatch(e -> "claim.closed".equals(e.getEventType()))).isTrue();
    }
}
