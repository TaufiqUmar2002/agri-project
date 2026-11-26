package com.umar;

import com.umar.riskpool.repository.RiskPoolOutboxEventRepository;
import com.umar.riskpool.security.RiskPoolIdentityFilter;
import com.umar.riskpool.service.RiskPoolLedgerService;
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

@SpringBootTest(classes = RiskPoolServiceApplication.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RiskPoolServiceEndToEndTest {

    @Autowired private WebApplicationContext wac;
    @Autowired private RiskPoolIdentityFilter identityFilter;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RiskPoolLedgerService ledgerService;
    @Autowired private RiskPoolOutboxEventRepository outboxRepository;

    private MockMvc mockMvc;
    private static String poolId;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).addFilters(identityFilter).build();
    }

    @Test
    @Order(1)
    void rpl05_rpl06_rpl10_createPoolAndTreatyWithDualApproval() throws Exception {
        MvcResult poolRes = mockMvc.perform(post("/api/pools")
                        .header("X-Actor-Id", "fin-author")
                        .header("X-Auth-Roles", "FINANCE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "poolCode": "KE_MAIZE_2026",
                                  "name": {"en": "Kenya Maize 2026"},
                                  "insurerRef": "ins_kenya_mutual",
                                  "countryCode": "KE",
                                  "currency": "KES",
                                  "mappings": [{"productIds": ["prd_1"], "regionCodes": ["KE-NYERI"], "perils": ["DROUGHT"], "seasons": ["2026-LR"]}],
                                  "accounting": {"earningMethod": "DAILY_PRO_RATA"},
                                  "limits": {"totalExposureCapMinor": 600000000, "topRegionShareLimit": 0.45},
                                  "effectiveFrom": "%s"
                                }
                                """.formatted(LocalDate.now().plusDays(10))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn();
        JsonNode pn = objectMapper.readTree(poolRes.getResponse().getContentAsString());
        poolId = pn.get("poolId").asString();
        String poolHash = pn.get("configHash").asString();
        String poolAprId = pn.get("approvalId").asString();

        // Maker cannot approve
        mockMvc.perform(put("/api/pools/approvals/" + poolAprId + "/decision")
                        .header("X-Actor-Id", "fin-author")
                        .header("X-Auth-Roles", "FINANCE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision": "APPROVE", "subjectHash": "%s", "note": "Self approval should fail immediately."}
                                """.formatted(poolHash)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MAKER_CANNOT_APPROVE"));

        // Second Finance + Compliance Officer activate pool
        mockMvc.perform(put("/api/pools/approvals/" + poolAprId + "/decision")
                        .header("X-Actor-Id", "fin-approver")
                        .header("X-Auth-Roles", "FINANCE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision": "APPROVE", "subjectHash": "%s", "note": "Verified pool mappings and limits."}
                                """.formatted(poolHash)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(put("/api/pools/approvals/" + poolAprId + "/decision")
                        .header("X-Actor-Id", "comp-1")
                        .header("X-Auth-Roles", "COMPLIANCE_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision": "APPROVE", "subjectHash": "%s", "note": "Compliance checked insurer approval and charter."}
                                """.formatted(poolHash)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectStatus").value("ACTIVE"));

        // RPL-06 Treaty placement invalid != 100 -> 422 PLACEMENT_INVALID
        mockMvc.perform(post("/api/pools/" + poolId + "/treaties")
                        .header("X-Actor-Id", "rei-mgr")
                        .header("X-Auth-Roles", "REINSURANCE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "treatyCode": "QS_BAD",
                                  "type": "QUOTA_SHARE",
                                  "placements": [{"reinsurerRef": "r1", "sharePercent": 70}],
                                  "period": {"basis": "RISKS_ATTACHING"},
                                  "scope": {"productIds": ["prd_1"]},
                                  "terms": {"cessionPercent": 25},
                                  "settlement": {"currency": "KES"},
                                  "wordingToken": "vt_doc_1",
                                  "effectiveFrom": "%s"
                                }
                                """.formatted(LocalDate.now().plusDays(10))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PLACEMENT_INVALID"));

        // Valid Quota Share treaty + dual approval
        MvcResult trtRes = mockMvc.perform(post("/api/pools/" + poolId + "/treaties")
                        .header("X-Actor-Id", "rei-mgr")
                        .header("X-Auth-Roles", "REINSURANCE_MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "treatyCode": "QS_2026",
                                  "type": "QUOTA_SHARE",
                                  "placements": [{"reinsurerRef": "r1", "sharePercent": 60}, {"reinsurerRef": "r2", "sharePercent": 40}],
                                  "period": {"basis": "RISKS_ATTACHING"},
                                  "scope": {"productIds": ["prd_1"]},
                                  "terms": {"cessionPercent": 25},
                                  "settlement": {"currency": "KES"},
                                  "wordingToken": "vt_doc_1",
                                  "effectiveFrom": "%s"
                                }
                                """.formatted(LocalDate.now().plusDays(10))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode tn = objectMapper.readTree(trtRes.getResponse().getContentAsString());
        String tHash = tn.get("treatyHash").asString();
        String tApr = tn.get("approvalId").asString();

        mockMvc.perform(put("/api/pools/approvals/" + tApr + "/decision")
                        .header("X-Actor-Id", "fin-approver").header("X-Auth-Roles", "FINANCE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision": "APPROVE", "subjectHash": "%s", "note": "Finance verified 25 percent cession."}
                                """.formatted(tHash)))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/pools/approvals/" + tApr + "/decision")
                        .header("X-Actor-Id", "comp-1").header("X-Auth-Roles", "COMPLIANCE_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision": "APPROVE", "subjectHash": "%s", "note": "Compliance signed off treaty wording."}
                                """.formatted(tHash)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjectStatus").value("ACTIVE"));
    }

    @Test
    @Order(2)
    void rpl01_rpl02_rpl03_rpl07_rpl08_rpl09_rpl11_eventsLedgerExposureBordereauxAndClose() throws Exception {
        ledgerService.onPolicyIssued("evt_pi_rp1", "pol_rp1", "prd_1", "KE-NYERI", "DROUGHT", "2026-LR",
                1_000_000L, 40_000L, 0L, "KES");
        ledgerService.onPolicyActivated("evt_pa_rp1", "pol_rp1", 1_000_000L, LocalDate.now(), LocalDate.now().plusDays(90));
        ledgerService.onPremiumPaid("evt_pp_rp1", "pol_rp1", 40_000L, "KES", "MOBILE_MONEY");
        ledgerService.onClaimOpened("evt_co_rp1", "clm_rp1", "pol_rp1", 200_000L, "KES");
        ledgerService.onPayoutCompleted("evt_pc_rp1", "clm_rp1", 200_000L, "KES");

        // RPL-08 Trial Balance & Integrity verify
        mockMvc.perform(get("/api/pools/" + poolId + "/ledger?view=TRIAL_BALANCE&verify=true")
                        .header("X-Actor-Id", "aud-1")
                        .header("X-Auth-Roles", "AUDITOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trialBalance.balanced").value(true))
                .andExpect(jsonPath("$.integrity.intact").value(true));

        // RPL-02 Exposure + stress scenario
        mockMvc.perform(get("/api/pools/" + poolId + "/exposure?stressPercent=50&regionCode=KE-NYERI")
                        .header("X-Actor-Id", "uw-1")
                        .header("X-Auth-Roles", "UNDERWRITER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.totalSumInsuredMinor").value(1000000));

        // RPL-03 Reinsurer viewer blocked on OPEN period -> 403
        mockMvc.perform(get("/api/pools/" + poolId + "/bordereaux?type=PREMIUM&period=2026-09")
                        .header("X-Actor-Id", "rei-v1")
                        .header("X-Auth-Roles", "REINSURER_VIEWER"))
                .andExpect(status().isForbidden());

        // RPL-07 Restricted automatic account blocked -> 422 ACCOUNT_RESTRICTED
        mockMvc.perform(post("/api/pools/" + poolId + "/journal")
                        .header("X-Actor-Id", "fin-1")
                        .header("X-Auth-Roles", "FINANCE")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "journalType": "EXPENSE",
                                  "valueDate": "%s",
                                  "currency": "KES",
                                  "lines": [{"account": "2010", "side": "DR", "amountMinor": 1000}, {"account": "1010", "side": "CR", "amountMinor": 1000}],
                                  "reasonCode": "PROVIDER_FEES",
                                  "evidenceTokens": ["vt_ev_1"],
                                  "note": "Should be rejected because 2010 is automatic only."
                                }
                                """.formatted(LocalDate.now())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ACCOUNT_RESTRICTED"));

        // RPL-09 Close past ended period
        String closedPeriod = LocalDate.now().minusMonths(1).toString().substring(0, 7);
        mockMvc.perform(post("/api/pools/" + poolId + "/periods/" + closedPeriod + "/close")
                        .header("X-Actor-Id", "fin-1")
                        .header("X-Auth-Roles", "FINANCE")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action": "CLOSE", "note": "Clean month end closing run without breaks."}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("CLOSING"));

        assertThat(outboxRepository.findAll().stream().anyMatch(e -> "ledger.posted".equals(e.getEventType()))).isTrue();
    }
}
