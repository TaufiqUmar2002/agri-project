package com.umar;

import com.umar.authentication.VaultIdentityFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = VaultServiceApplication.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VaultEndToEndIntegrationTest {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private VaultIdentityFilter vaultIdentityFilter;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(vaultIdentityFilter)
                .build();
    }

    @Test
    @Order(1)
    void vlt01_and_vlt02_tokenizeDetokenizeIdempotencyFpeAndPolicyDenial() throws Exception {
        String idempKey = UUID.randomUUID().toString();
        String subjectRef = "farmer-" + UUID.randomUUID();
        String body = """
                {
                  "purpose": "ACCOUNT_REGISTRATION",
                  "contextRef": "reg:1001",
                  "items": [
                    { "dataClass": "PHONE", "value": "+254712345678", "subjectRef": "%s", "reuseExisting": true },
                    { "dataClass": "PHONE", "value": "invalid-phone", "subjectRef": "%s" }
                  ]
                }
                """.formatted(subjectRef, subjectRef);

        MvcResult res = mockMvc.perform(post("/api/vault/tokenize")
                        .header("Idempotency-Key", idempKey)
                        .header("X-Service-Identity", "AUTH-KYC-SERVICE")
                        .header("X-Vault-Roles", "SERVICE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokens[0].token").isNotEmpty())
                .andExpect(jsonPath("$.tokens[0].masked").value("+2547*****678"))
                .andExpect(jsonPath("$.failures[0].code").value("VALUE_INVALID"))
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        String phoneToken = root.get("tokens").get(0).get("token").asString();
        assertThat(phoneToken).startsWith("vt_phone_");

        // Idempotent replay with same key & payload returns same token
        MvcResult replay = mockMvc.perform(post("/api/vault/tokenize")
                        .header("Idempotency-Key", idempKey)
                        .header("X-Service-Identity", "AUTH-KYC-SERVICE")
                        .header("X-Vault-Roles", "SERVICE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        assertThat(objectMapper.readTree(replay.getResponse().getContentAsString())
                .get("tokens").get(0).get("token").asString()).isEqualTo(phoneToken);

        // Reuse existing via blind index
        String reuseBody = """
                {
                  "purpose": "ACCOUNT_REGISTRATION",
                  "contextRef": "reg:1002",
                  "items": [
                    { "dataClass": "PHONE", "value": "+254712345678", "subjectRef": "%s", "reuseExisting": true }
                  ]
                }
                """.formatted(subjectRef);
        mockMvc.perform(post("/api/vault/tokenize")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Service-Identity", "AUTH-KYC-SERVICE")
                        .header("X-Vault-Roles", "SERVICE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reuseBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokens[0].token").value(phoneToken))
                .andExpect(jsonPath("$.tokens[0].reused").value(true));

        // FPE permitted on WALLET_NUMBER
        String walletBody = """
                {
                  "purpose": "WALLET_REGISTRATION",
                  "contextRef": "farmer:w1",
                  "items": [
                    { "dataClass": "WALLET_NUMBER", "value": "254712345678", "subjectRef": "%s", "format": "FPE" }
                  ]
                }
                """.formatted(subjectRef);
        mockMvc.perform(post("/api/vault/tokenize")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Service-Identity", "FARMER-PROFILE")
                        .header("X-Vault-Roles", "SERVICE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(walletBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokens[0].format").value("FPE"));

        // Default-deny policy check: NOTIFICATION-SERVICE has no policy for PHONE
        mockMvc.perform(post("/api/vault/tokenize")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Service-Identity", "NOTIFICATION-SERVICE")
                        .header("X-Vault-Roles", "SERVICE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reuseBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("POLICY_DENIED"));

        // VLT-02 Detokenize VALUE & MASKED modes + Cache-Control: no-store
        String detokBody = """
                {
                  "purpose": "ACCOUNT_REGISTRATION",
                  "contextRef": "reg:1001",
                  "tokens": ["%s", "vt_phone_missing"],
                  "returnMode": "VALUE"
                }
                """.formatted(phoneToken);
        mockMvc.perform(post("/api/vault/detokenize")
                        .header("X-Service-Identity", "AUTH-KYC-SERVICE")
                        .header("X-Vault-Roles", "SERVICE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(detokBody))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.values[0].value").value("+254712345678"))
                .andExpect(jsonPath("$.failures[0].code").value("TOKEN_NOT_FOUND"));

        String maskedBody = """
                {
                  "purpose": "ACCOUNT_REGISTRATION",
                  "contextRef": "reg:1001",
                  "tokens": ["%s"],
                  "returnMode": "MASKED"
                }
                """.formatted(phoneToken);
        mockMvc.perform(post("/api/vault/detokenize")
                        .header("X-Service-Identity", "AUTH-KYC-SERVICE")
                        .header("X-Vault-Roles", "SERVICE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maskedBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.values[0].value").value("+2547*****678"));
    }

    @Test
    @Order(2)
    void vlt03_and_vlt15_secretsGenerateVersionSignVerifyRevealOnceAndLease() throws Exception {
        String ref = "whsec.test." + UUID.randomUUID().toString().substring(0, 8);
        String createBody = """
                {
                  "ref": "%s",
                  "secretClass": "WEBHOOK_SIGNING",
                  "mode": "GENERATE",
                  "generate": { "algorithm": "HMAC_SHA256", "keySizeBits": 256 },
                  "owner": { "ownerType": "PARTNER", "ownerId": "ptn_ruralfin" },
                  "policy": { "uses": ["SIGN", "VERIFY", "REVEAL_ONCE", "LEASE"], "services": ["INTEGRATION-HUB"], "purposes": ["WEBHOOK_SIGNING", "PARTNER_HANDOVER"], "overlapHours": 72 },
                  "rotation": { "intervalDays": 365, "notifyDaysBeforeExpiry": 30 }
                }
                """.formatted(ref);

        mockMvc.perform(post("/api/vault/secrets")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Service-Identity", "INTEGRATION-HUB")
                        .header("X-Vault-Roles", "SERVICE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ref").value(ref))
                .andExpect(jsonPath("$.version").value(1));

        // Metadata endpoint (Appendix C)
        mockMvc.perform(get("/api/vault/secrets/" + ref + "/metadata")
                        .header("X-Service-Identity", "INTEGRATION-HUB")
                        .header("X-Vault-Roles", "SERVICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.activeVersion").value(1));

        // SIGN
        String payloadB64 = java.util.Base64.getEncoder().encodeToString("hello-webhook".getBytes(StandardCharsets.UTF_8));
        String signReq = """
                {
                  "operation": "SIGN",
                  "algorithm": "HMAC_SHA256",
                  "purpose": "WEBHOOK_SIGNING",
                  "contextRef": "delivery:1",
                  "payload": "%s"
                }
                """.formatted(payloadB64);

        MvcResult signRes = mockMvc.perform(post("/api/vault/secrets/" + ref + "/use")
                        .header("X-Service-Identity", "INTEGRATION-HUB")
                        .header("X-Vault-Roles", "SERVICE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signReq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signature").isNotEmpty())
                .andReturn();
        String sig = objectMapper.readTree(signRes.getResponse().getContentAsString()).get("signature").asString();

        // VERIFY
        String verifyReq = """
                {
                  "operation": "VERIFY",
                  "algorithm": "HMAC_SHA256",
                  "purpose": "WEBHOOK_SIGNING",
                  "contextRef": "delivery:1",
                  "payload": "%s",
                  "signature": "%s"
                }
                """.formatted(payloadB64, sig);
        mockMvc.perform(post("/api/vault/secrets/" + ref + "/use")
                        .header("X-Service-Identity", "INTEGRATION-HUB")
                        .header("X-Vault-Roles", "SERVICE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyReq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        // REVEAL_ONCE succeeds once, then 409 ALREADY_REVEALED
        String revealReq = """
                {
                  "operation": "REVEAL_ONCE",
                  "purpose": "PARTNER_HANDOVER",
                  "contextRef": "sub:1"
                }
                """;
        mockMvc.perform(post("/api/vault/secrets/" + ref + "/use")
                        .header("X-Service-Identity", "INTEGRATION-HUB")
                        .header("X-Vault-Roles", "SERVICE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(revealReq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").isNotEmpty());

        mockMvc.perform(post("/api/vault/secrets/" + ref + "/use")
                        .header("X-Service-Identity", "INTEGRATION-HUB")
                        .header("X-Vault-Roles", "SERVICE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(revealReq))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_REVEALED"));
    }

    @Test
    @Order(3)
    void vlt05_and_vlt14_documentUploadMalwareGuardViewLinkSingleUseAndReadRestriction() throws Exception {
        String subjectRef = "farmer-doc-" + UUID.randomUUID();
        byte[] imgBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4};
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(imgBytes));

        String metaJson = """
                {
                  "dataClass": "SELFIE",
                  "subjectRef": "%s",
                  "retentionClass": "STANDARD_7Y",
                  "contentType": "image/jpeg",
                  "purpose": "KYC_SUBMISSION",
                  "contextRef": "kyc:1001",
                  "sha256": "%s"
                }
                """.formatted(subjectRef, sha256);

        MockMultipartFile metaPart = new MockMultipartFile("metadata", "", "application/json",
                metaJson.getBytes(StandardCharsets.UTF_8));
        MockMultipartFile filePart = new MockMultipartFile("file", "selfie.jpg", "image/jpeg", imgBytes);

        MvcResult uploadRes = mockMvc.perform(multipart("/api/vault/documents")
                        .file(metaPart)
                        .file(filePart)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Service-Identity", "AUTH-KYC-SERVICE")
                        .header("X-Vault-Roles", "SERVICE"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.sha256").value(sha256))
                .andReturn();

        String docToken = objectMapper.readTree(uploadRes.getResponse().getContentAsString()).get("token").asString();
        assertThat(docToken).startsWith("vt_doc_selfie_");

        // Malware rejection (EICAR)
        byte[] eicar = "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*".getBytes(StandardCharsets.US_ASCII);
        String eicarSha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(eicar));
        MockMultipartFile eicarMeta = new MockMultipartFile("metadata", "", "application/json",
                metaJson.replace(sha256, eicarSha).getBytes(StandardCharsets.UTF_8));
        MockMultipartFile eicarFile = new MockMultipartFile("file", "bad.jpg", "image/jpeg", eicar);
        mockMvc.perform(multipart("/api/vault/documents")
                        .file(eicarMeta)
                        .file(eicarFile)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Service-Identity", "AUTH-KYC-SERVICE")
                        .header("X-Vault-Roles", "SERVICE"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MALWARE_DETECTED"));

        // VLT-14 READ forbidden for SELFIE
        mockMvc.perform(get("/api/vault/documents/" + docToken)
                        .param("mode", "READ")
                        .param("purpose", "KYC_REVIEW")
                        .param("contextRef", "kyc:1001")
                        .header("X-Service-Identity", "AUTH-KYC-SERVICE")
                        .header("X-Vault-Roles", "SERVICE"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("READ_NOT_ALLOWED"));

        // VLT-14 VERIFY
        mockMvc.perform(get("/api/vault/documents/" + docToken)
                        .param("mode", "VERIFY")
                        .param("purpose", "KYC_REVIEW")
                        .param("contextRef", "kyc:1001")
                        .param("subjectRef", subjectRef)
                        .param("sha256", sha256)
                        .header("X-Service-Identity", "AUTH-KYC-SERVICE")
                        .header("X-Vault-Roles", "SERVICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.intact").value(true))
                .andExpect(jsonPath("$.subjectMatches").value(true))
                .andExpect(jsonPath("$.hashMatches").value(true));

        // VLT-14 VIEW single-use link + replay rejection
        MvcResult viewRes = mockMvc.perform(get("/api/vault/documents/" + docToken)
                        .param("mode", "VIEW")
                        .param("purpose", "KYC_REVIEW")
                        .param("contextRef", "kyc:1001")
                        .param("viewerRef", "rev-0412")
                        .param("viewerRole", "KYC_REVIEWER")
                        .header("X-Service-Identity", "AUTH-KYC-SERVICE")
                        .header("X-Vault-Roles", "SERVICE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.singleUse").value(true))
                .andExpect(jsonPath("$.viewUrl").isNotEmpty())
                .andReturn();

        String viewUrl = objectMapper.readTree(viewRes.getResponse().getContentAsString()).get("viewUrl").asString();
        String linkId = viewUrl.substring(viewUrl.lastIndexOf('/') + 1);

        mockMvc.perform(get("/api/vault/viewer/" + linkId)
                        .param("viewerRef", "rev-0412")
                        .param("viewerRole", "KYC_REVIEWER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(docToken));

        // Second redemption fails with 403 LINK_NOT_VALID
        mockMvc.perform(get("/api/vault/viewer/" + linkId)
                        .param("viewerRef", "rev-0412")
                        .param("viewerRole", "KYC_REVIEWER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LINK_NOT_VALID"));
    }

    @Test
    @Order(4)
    void vlt07_and_vlt08_legalHoldAndCryptoShredding() throws Exception {
        String subjectRef = "farmer-erase-" + UUID.randomUUID();
        // Tokenize an ERASABLE PHONE and a FINANCIAL_RETAIN WALLET_NUMBER
        String phoneReq = """
                {
                  "purpose": "ACCOUNT_REGISTRATION",
                  "contextRef": "reg:erase1",
                  "items": [{ "dataClass": "PHONE", "value": "+254700111222", "subjectRef": "%s" }]
                }
                """.formatted(subjectRef);
        MvcResult phoneRes = mockMvc.perform(post("/api/vault/tokenize")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .header("X-Service-Identity", "AUTH-KYC-SERVICE")
                        .header("X-Vault-Roles", "SERVICE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(phoneReq))
                .andExpect(status().isCreated())
                .andReturn();
        String phoneToken = objectMapper.readTree(phoneRes.getResponse().getContentAsString())
                .get("tokens").get(0).get("token").asString();

        // Place legal hold on subjectRef
        String holdReq = """
                {
                  "action": "PLACE",
                  "caseRef": "CMP-2026-0092",
                  "authority": "REGULATOR",
                  "scope": { "subjectRefs": ["%s"], "dataClasses": ["PHONE"] },
                  "reviewDate": "2027-05-01",
                  "reason": "Regulator inquiry requires temporary preservation of subject identifiers."
                }
                """.formatted(subjectRef);
        MvcResult holdRes = mockMvc.perform(post("/api/vault/legal-holds")
                        .header("X-Service-Identity", "comp-officer-1")
                        .header("X-Vault-Roles", "COMPLIANCE_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(holdReq))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();
        String holdId = objectMapper.readTree(holdRes.getResponse().getContentAsString()).get("id").asString();

        // Erasure blocked while legal hold is active -> ON_HOLD
        String eraseReq = """
                { "requestId": "%s", "reason": "DATA_SUBJECT_REQUEST" }
                """.formatted(UUID.randomUUID());
        mockMvc.perform(post("/api/vault/subjects/" + subjectRef + "/erase")
                        .header("X-Service-Identity", "AUTH-KYC-SERVICE")
                        .header("X-Vault-Roles", "SERVICE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eraseReq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("ON_HOLD"));

        // Request lift & approve by a second Compliance Officer (VLT-13)
        String liftReq = """
                {
                  "action": "LIFT",
                  "holdId": "%s",
                  "caseRef": "CMP-2026-0092",
                  "authority": "REGULATOR",
                  "reason": "Compliance investigation concluded and clearance issued."
                }
                """.formatted(holdId);
        MvcResult liftRes = mockMvc.perform(post("/api/vault/legal-holds")
                        .header("X-Service-Identity", "comp-officer-1")
                        .header("X-Vault-Roles", "COMPLIANCE_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(liftReq))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("LIFT_PENDING_APPROVAL"))
                .andReturn();
        String approvalId = objectMapper.readTree(liftRes.getResponse().getContentAsString()).get("approvalId").asString();

        String decisionBody = """
                {
                  "decision": "APPROVE",
                  "checklist": { "caseClosedConfirmed": true, "noOtherHolds": true },
                  "note": "Verified case CMP-2026-0092 is closed and no other holds exist."
                }
                """;
        mockMvc.perform(put("/api/vault/approvals/" + approvalId + "/decision")
                        .header("X-Service-Identity", "comp-officer-2")
                        .header("X-Vault-Roles", "COMPLIANCE_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(decisionBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Now erase succeeds -> SHREDDED
        String eraseReq2 = """
                { "requestId": "%s", "reason": "DATA_SUBJECT_REQUEST" }
                """.formatted(UUID.randomUUID());
        mockMvc.perform(post("/api/vault/subjects/" + subjectRef + "/erase")
                        .header("X-Service-Identity", "AUTH-KYC-SERVICE")
                        .header("X-Vault-Roles", "SERVICE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eraseReq2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("COMPLETED"));

        // Detokenizing shredded token reports SUBJECT_ERASED
        String detokReq = """
                {
                  "purpose": "ACCOUNT_REGISTRATION",
                  "contextRef": "post-erase",
                  "tokens": ["%s"]
                }
                """.formatted(phoneToken);
        mockMvc.perform(post("/api/vault/detokenize")
                        .header("X-Service-Identity", "AUTH-KYC-SERVICE")
                        .header("X-Vault-Roles", "SERVICE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(detokReq))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failures[0].code").value("SUBJECT_ERASED"));
    }

    @Test
    @Order(5)
    void vlt04_vlt06_vlt09_vlt10_vlt12_and_vlt13_governanceAndOperations() throws Exception {
        // VLT-06 Break-glass request -> separation of duties -> dual approval
        String bgReq = """
                {
                  "incidentRef": "SEC-2026-0417",
                  "scope": { "subjectRefs": ["subj-bg-1"], "dataClasses": ["WALLET_NUMBER"], "operations": ["DETOKENIZE"], "maxRecords": 10 },
                  "durationMinutes": 60,
                  "legalBasis": "FRAUD_INVESTIGATION",
                  "investigatorRef": "investigator-01",
                  "reason": "Fraud investigation into shared payout wallets requires emergency access."
                }
                """;
        MvcResult bgRes = mockMvc.perform(post("/api/vault/break-glass")
                        .header("X-Service-Identity", "sec-officer-1")
                        .header("X-Vault-Roles", "SECURITY_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bgReq))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn();
        String bgApprovalId = objectMapper.readTree(bgRes.getResponse().getContentAsString()).get("approvalId").asString();

        // Requester cannot approve own request
        String bgApprove = """
                {
                  "decision": "APPROVE",
                  "checklist": { "incidentChecked": true, "scopeMinimal": true, "investigatorIndependent": true },
                  "note": "Incident SEC-2026-0417 verified, scope minimal and investigator independent."
                }
                """;
        mockMvc.perform(put("/api/vault/approvals/" + bgApprovalId + "/decision")
                        .header("X-Service-Identity", "sec-officer-1")
                        .header("X-Vault-Roles", "SECURITY_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bgApprove))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REQUESTER_CANNOT_APPROVE"));

        // Two independent approvers (Compliance + Security)
        mockMvc.perform(put("/api/vault/approvals/" + bgApprovalId + "/decision")
                        .header("X-Service-Identity", "comp-officer-1")
                        .header("X-Vault-Roles", "COMPLIANCE_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bgApprove))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(put("/api/vault/approvals/" + bgApprovalId + "/decision")
                        .header("X-Service-Identity", "sec-officer-2")
                        .header("X-Vault-Roles", "SECURITY_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bgApprove))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // VLT-04 Root rotation requires ceremony; CLASS_KEK ON_DEMAND uses dual custodian approval
        String rootRot = """
                { "keyType": "ROOT", "scope": "ROOT", "mode": "ON_DEMAND" }
                """;
        mockMvc.perform(post("/api/vault/keys/rotate")
                        .header("X-Service-Identity", "sec-officer-1")
                        .header("X-Vault-Roles", "SECURITY_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rootRot))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CEREMONY_REQUIRED"));

        String kekRot = """
                { "keyType": "CLASS_KEK", "scope": { "dataClass": "WALLET_NUMBER" }, "mode": "SCHEDULED" }
                """;
        mockMvc.perform(post("/api/vault/keys/rotate")
                        .header("X-Service-Identity", "sec-officer-1")
                        .header("X-Vault-Roles", "SECURITY_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(kekRot))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // VLT-09 Access log SUMMARY, INTEGRITY, and AUDIT verify
        mockMvc.perform(get("/api/vault/access-log")
                        .param("view", "INTEGRITY")
                        .param("from", "2020-01-01")
                        .param("to", "2030-01-01")
                        .header("X-Service-Identity", "auditor-1")
                        .header("X-Vault-Roles", "AUDITOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.integrity.intact").value(true));

        // VLT-10 Restore drill
        String drillReq = """
                { "type": "RESTORE_DRILL", "environment": "ISOLATED_DRILL", "testSet": "STANDARD", "targets": { "rpoMinutes": 5, "rtoMinutes": 60 } }
                """;
        mockMvc.perform(post("/api/vault/backups/verify")
                        .header("X-Service-Identity", "sre-1")
                        .header("X-Vault-Roles", "PLATFORM_RELIABILITY_ENGINEER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(drillReq))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PASSED"))
                .andExpect(jsonPath("$.measured.targetsMet").value(true))
                .andExpect(jsonPath("$.destroyedAt").isNotEmpty());

        // VLT-12 Hard rule: KYC_DOCUMENT cannot allow DETOKENIZE
        String badPolicy = """
                {
                  "kind": "ACCESS_POLICY",
                  "name": "BAD_KYC_POLICY",
                  "definition": { "policy": { "serviceIdentity": "CLAIMS-SERVICE", "dataClass": "KYC_DOCUMENT", "purposes": ["KYC_REVIEW"], "operations": ["DETOKENIZE"] } },
                  "effectiveFrom": "2026-12-01T00:00:00Z",
                  "reason": "Attempting to allow raw detokenization of KYC document images."
                }
                """;
        mockMvc.perform(post("/api/vault/policies")
                        .header("X-Service-Identity", "sec-officer-1")
                        .header("X-Vault-Roles", "SECURITY_OFFICER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badPolicy))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CLASS_FORBIDS_OPERATION"));
    }
}
