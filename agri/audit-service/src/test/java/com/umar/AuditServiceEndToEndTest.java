package com.umar;

import com.umar.audit.AuditServiceApplication;
import com.umar.audit.domain.AuditRecordEntity;
import com.umar.audit.repository.AuditRecordRepository;
import com.umar.audit.repository.TelemetryBatchRepository;
import com.umar.audit.security.AuditIdentityFilter;
import com.umar.audit.service.AuditIntakeAndKeeperService;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = AuditServiceApplication.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuditServiceEndToEndTest {

    @Autowired private WebApplicationContext wac;
    @Autowired private AuditIdentityFilter identityFilter;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuditIntakeAndKeeperService intakeService;
    @Autowired private AuditRecordRepository recordRepository;
    @Autowired private TelemetryBatchRepository telemetryRepository;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).addFilters(identityFilter).build();
    }

    @Test
    @Order(1)
    void auditIntakeTraceReportsModelsSearchAndHolds() throws Exception {
        // AUD-01: Ingest domain events + PII redaction + telemetry batch + keeper checkpoint
        AuditRecordEntity piiRec = intakeService.ingestDomainEvent(
                "evt_aud_1", "platform.policy.events", "policy-service", "policy.issued",
                Map.of("policyId", "pol_100", "claimId", "clm_100", "plotId", "plt_100",
                        "farmerId", "frm_001", "msisdn", "+254712000001"));
        assertThat(piiRec.isRedacted()).isTrue();

        intakeService.ingestDomainEvent("evt_aud_2", "platform.trigger.events", "trigger-engine",
                "trigger.evaluation.executed",
                Map.of("claimId", "clm_100", "policyId", "pol_100", "indexValue", 42.5));

        intakeService.ingestDomainEvent("evt_aud_3", "platform.claims.events", "claims-service",
                "claim.decision.ready",
                Map.of("claimId", "clm_100", "policyId", "pol_100", "decision", "APPROVED"));

        intakeService.ingestDomainEvent("evt_aud_4", "platform.iot.events", "iot-service",
                "iot.reading.ingested",
                Map.of("deviceId", "dev_001", "tempC", 18.2));

        assertThat(intakeService.runAuditKeeper()).isGreaterThanOrEqualTo(2);
        assertThat(telemetryRepository.findBySealedFalse()).isEmpty();

        // AUD-02: Trace decision (compliance allowed, admin denied)
        mockMvc.perform(get("/api/audit/decisions/clm_100")
                        .header("X-User-Id", "adm_1")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/audit/decisions/clm_100?format=PACK")
                        .header("X-User-Id", "comp_1")
                        .header("X-User-Role", "COMPLIANCE_OFFICER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimId").value("clm_100"))
                .andExpect(jsonPath("$.integrity.status").value("VERIFIED"))
                .andExpect(jsonPath("$.packToken").isNotEmpty());

        // AUD-03 + AUD-08: Create report, block self-approve, dual-approve, regulator visibility
        MvcResult repRes = mockMvc.perform(post("/api/audit/reports/regulatory")
                        .header("X-User-Id", "comp_1")
                        .header("X-User-Role", "COMPLIANCE_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportType\":\"CLAIMS_REGISTER\",\"mode\":\"ONE_OFF\",\"format\":\"PDF\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();
        JsonNode repJson = objectMapper.readTree(repRes.getResponse().getContentAsString());
        String reportId = repJson.get("reportId").asString();
        String reportHash = repJson.get("reportHash").asString();

        // Regulator sees no DRAFT
        mockMvc.perform(get("/api/audit/reports")
                        .header("X-User-Id", "reg_1")
                        .header("X-User-Role", "REGULATOR_VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());

        // Self-approve denied
        mockMvc.perform(post("/api/audit/reports/regulatory")
                        .header("X-User-Id", "comp_1")
                        .header("X-User-Role", "COMPLIANCE_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportType\":\"CLAIMS_REGISTER\",\"mode\":\"APPROVE\",\"format\":\"PDF\",\"reportId\":\"" + reportId + "\",\"reportHash\":\"" + reportHash + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPROVER_NOT_INDEPENDENT"));

        // Second compliance officer approves -> FINAL
        mockMvc.perform(post("/api/audit/reports/regulatory")
                        .header("X-User-Id", "comp_2")
                        .header("X-User-Role", "COMPLIANCE_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportType\":\"CLAIMS_REGISTER\",\"mode\":\"APPROVE\",\"format\":\"PDF\",\"reportId\":\"" + reportId + "\",\"reportHash\":\"" + reportHash + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINAL"));

        mockMvc.perform(get("/api/audit/reports?download=true&reportId=" + reportId)
                        .header("X-User-Id", "reg_1")
                        .header("X-User-Role", "REGULATOR_VIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.download.token").isNotEmpty());

        // AUD-04 + AUD-05: Model register & Audit records search guard
        mockMvc.perform(get("/api/audit/models?asOf=2099-01-01T00:00:00Z")
                        .header("X-User-Id", "comp_1")
                        .header("X-User-Role", "COMPLIANCE_OFFICER"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("AS_OF_INVALID"));

        mockMvc.perform(get("/api/audit/models")
                        .header("X-User-Id", "adm_1")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].governanceNotes").doesNotExist());

        mockMvc.perform(get("/api/audit/records")
                        .header("X-User-Id", "aud_1")
                        .header("X-User-Role", "AUDITOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SEARCH_TOO_BROAD"));

        mockMvc.perform(get("/api/audit/records?farmerId=frm_001")
                        .header("X-User-Id", "aud_1")
                        .header("X-User-Role", "AUDITOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isNotEmpty());

        // AUD-11: Legal hold place & dual-control release
        MvcResult hldRes = mockMvc.perform(post("/api/audit/holds")
                        .header("X-User-Id", "comp_1")
                        .header("X-User-Role", "COMPLIANCE_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"PLACE\",\"caseRef\":\"CASE-2026-01\",\"reasonCode\":\"LITIGATION\",\"scope\":{\"claimId\":\"clm_100\"},\"note\":\"Preserve all claim 100 evidence\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        String holdId = objectMapper.readTree(hldRes.getResponse().getContentAsString()).get("holdId").asString();

        MvcResult relRes = mockMvc.perform(post("/api/audit/holds")
                        .header("X-User-Id", "comp_1")
                        .header("X-User-Role", "COMPLIANCE_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"RELEASE\",\"holdId\":\"" + holdId + "\",\"note\":\"Litigation settled request release\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_RELEASE"))
                .andReturn();
        String hHash = objectMapper.readTree(relRes.getResponse().getContentAsString()).get("proposalHash").asString();

        mockMvc.perform(post("/api/audit/holds")
                        .header("X-User-Id", "comp_2")
                        .header("X-User-Role", "COMPLIANCE_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"RELEASE\",\"holdId\":\"" + holdId + "\",\"approveProposal\":\"" + hHash + "\",\"note\":\"Approved independent release\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
    }

    @Test
    @Order(2)
    void integrityVerificationAndDualControlFindingDisposition() throws Exception {
        // Seed an unapproved payout -> CRITICAL finding PAYOUT_WITHOUT_APPROVAL
        intakeService.ingestDomainEvent("evt_int_1", "platform.payout.events", "payout-service",
                "payout.completed",
                Map.of("claimId", "clm_200", "policyId", "pol_200", "amount", 5000));

        // AUD-09 + AUD-10: List findings & test dual-control CRITICAL finding disposition
        MvcResult fndRes = mockMvc.perform(get("/api/audit/findings?status=OPEN")
                        .header("X-User-Id", "comp_1")
                        .header("X-User-Role", "COMPLIANCE_OFFICER"))
                .andExpect(status().isOk())
                .andReturn();
        String findingId = null;
        for (JsonNode item : objectMapper.readTree(fndRes.getResponse().getContentAsString()).get("items")) {
            if ("PAYOUT_WITHOUT_APPROVAL".equals(item.get("ruleCode").asString())) {
                findingId = item.get("findingId").asString();
                break;
            }
        }
        assertThat(findingId).isNotNull();

        // Missing evidence
        mockMvc.perform(put("/api/audit/findings/" + findingId + "/disposition")
                        .header("X-User-Id", "comp_1")
                        .header("X-User-Role", "COMPLIANCE_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"RESOLVE\",\"explanation\":\"Fixed\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("EVIDENCE_REQUIRED"));

        MvcResult dispRes = mockMvc.perform(put("/api/audit/findings/" + findingId + "/disposition")
                        .header("X-User-Id", "comp_1")
                        .header("X-User-Role", "COMPLIANCE_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"RESOLVE\",\"explanation\":\"Reconciled\",\"evidenceRefs\":[\"evd_fin_1\"]}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.approvalStatus").value("PENDING_APPROVAL"))
                .andReturn();
        String propHash = objectMapper.readTree(dispRes.getResponse().getContentAsString()).get("proposalHash").asString();

        mockMvc.perform(put("/api/audit/findings/" + findingId + "/disposition")
                        .header("X-User-Id", "comp_2")
                        .header("X-User-Role", "COMPLIANCE_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approveProposal\":\"" + propHash + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        // AUD-06 + AUD-07: Integrity verification PASSED, then tamper record hash -> FAILED alert
        mockMvc.perform(post("/api/audit/integrity/verifications")
                        .header("X-User-Id", "sec_1")
                        .header("X-User-Role", "SECURITY_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"CHAIN\",\"chainId\":\"REG7\",\"mode\":\"FULL\",\"reason\":\"PERIODIC\",\"note\":\"Scheduled chain integrity audit\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PASSED"));

        // Tamper REG7 record hash to simulate tampering
        AuditRecordEntity tampered = recordRepository.findByChainIdOrderBySeqAsc("REG7").getFirst();
        tampered.setRecordHash("0000tampered");
        recordRepository.save(tampered);

        mockMvc.perform(post("/api/audit/integrity/verifications")
                        .header("X-User-Id", "sec_1")
                        .header("X-User-Role", "SECURITY_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"CHAIN\",\"chainId\":\"REG7\",\"mode\":\"FULL\",\"reason\":\"INCIDENT\",\"note\":\"Verify suspected tampering\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.mismatchCount", org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/audit/integrity")
                        .header("X-User-Id", "sec_1")
                        .header("X-User-Role", "SECURITY_OFFICER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overall").value("DEGRADED"));
    }

    @Test
    @Order(3)
    void vaultEncryptionAtRestAndDbSecurityToggleAndExtensionHooks() throws Exception {
        // 1. Verify raw sensitive payload is encrypted in DB (not stored in plaintext)
        String rawDbPayload = jdbcTemplate.queryForObject(
                "SELECT payload_json FROM audit_records WHERE claim_id = 'clm_100' FETCH FIRST 1 ROWS ONLY",
                String.class);
        assertThat(rawDbPayload).startsWith("vlt:v1:");
        assertThat(rawDbPayload).doesNotContain("pol_100");

        // 2. Verify security bypass when toggled OFF in DB via common-security
        mockMvc.perform(get("/api/audit/decisions/clm_100"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/security/modules/audit-service")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"securityEnabled\":false,\"updatedBy\":\"sec_ops\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/audit/decisions/clm_100"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Security-Mode", "BYPASSED"))
                .andExpect(jsonPath("$.claimId").value("clm_100"));

        // Re-enable DB security toggle
        mockMvc.perform(put("/api/security/modules/audit-service")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"securityEnabled\":true,\"updatedBy\":\"sec_ops\"}"))
                .andExpect(status().isOk());
    }
}
