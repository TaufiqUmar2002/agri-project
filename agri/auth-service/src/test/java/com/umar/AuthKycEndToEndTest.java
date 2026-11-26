package com.umar;

import com.umar.auth.client.LocalDataVaultClient;
import com.umar.auth.client.LocalHookSdkClient;
import com.umar.auth.client.LocalNotificationClient;
import com.umar.auth.domain.entity.UserAccountEntity;
import com.umar.auth.repository.UserAccountRepository;
import com.umar.auth.security.AuthIdentityFilter;
import com.umar.auth.security.CryptoHashService;
import com.umar.enums.auth.AccountStatus;
import com.umar.enums.auth.AuthRole;
import com.umar.enums.auth.KycTier;
import com.umar.enums.auth.UserType;
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

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = AuthServiceApplication.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthKycEndToEndTest {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private AuthIdentityFilter identityFilter;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LocalNotificationClient notificationClient;

    @Autowired
    private LocalDataVaultClient dataVaultClient;

    @Autowired
    private LocalHookSdkClient hookSdkClient;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private CryptoHashService cryptoHashService;

    private MockMvc mockMvc;

    private static String farmerId;
    private static String farmerAccessToken;
    private static String farmerRefreshToken;
    private static String payoutConsentId;
    private static String borderlineKycId;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(identityFilter)
                .build();
        notificationClient.setUnavailable(false);
        dataVaultClient.setUnavailable(false);
    }

    @Test
    @Order(1)
    void aut01_and_aut08_registerAndVerifyOtpAndJwks() throws Exception {
        String blockedPhone = "+254799999999";
        String blockedHash = dataVaultClient.computeKeyedHash(blockedPhone, "PHONE_HASH");
        hookSdkClient.getBlockedPhoneHashes().add(blockedHash);

        String blockedBody = """
                {
                  "phoneNumber": "%s",
                  "userType": "FARMER",
                  "fullName": "Blocked User",
                  "preferredLanguage": "sw",
                  "channel": "APP"
                }
                """.formatted(blockedPhone);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockedBody))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REGISTRATION_NOT_ALLOWED"));

        String idemp = UUID.randomUUID().toString();
        String regBody = """
                {
                  "phoneNumber": "+254712345678",
                  "userType": "FARMER",
                  "fullName": "Amina Odhiambo",
                  "preferredLanguage": "sw",
                  "channel": "APP"
                }
                """;
        MvcResult regRes = mockMvc.perform(post("/api/auth/register")
                        .header("Idempotency-Key", idemp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.maskedPhone").value("+2547*****678"))
                .andReturn();

        String challengeId = objectMapper.readTree(regRes.getResponse().getContentAsString())
                .get("challengeId").asString();
        String otp = notificationClient.getSentMessages().getLast().payload();

        // Idempotent replay returns same challengeId
        mockMvc.perform(post("/api/auth/register")
                        .header("Idempotency-Key", idemp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.challengeId").value(challengeId));

        // Wrong OTP returns 400 INVALID_OTP with attemptsRemaining
        mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "challengeId": "%s", "otp": "000000", "deviceId": "dev-1" }
                                """.formatted(challengeId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OTP"))
                .andExpect(jsonPath("$.attemptsRemaining").value(4));

        // Correct OTP activates account and returns tokens
        MvcResult verifyRes = mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "challengeId": "%s", "otp": "%s", "deviceId": "dev-1" }
                                """.formatted(challengeId, otp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.kycTier").value("TIER_0"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        JsonNode verified = objectMapper.readTree(verifyRes.getResponse().getContentAsString());
        farmerId = verified.get("userId").asString();
        farmerAccessToken = verified.get("accessToken").asString();
        farmerRefreshToken = verified.get("refreshToken").asString();

        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"));
    }

    @Test
    @Order(2)
    void aut05_recordConsentsAndVersionCheck() throws Exception {
        String outdated = """
                {
                  "consents": [ { "purpose": "DATA_COLLECTION", "textVersion": "2" } ],
                  "language": "sw",
                  "channel": "APP",
                  "evidence": { "type": "TAP_CONFIRM" }
                }
                """;
        mockMvc.perform(post("/api/consents")
                        .header("Authorization", "Bearer " + farmerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(outdated))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSENT_VERSION_OUTDATED"));

        String valid = """
                {
                  "consents": [
                    { "purpose": "DATA_COLLECTION", "textVersion": "3" },
                    { "purpose": "SATELLITE_MONITORING", "textVersion": "3" },
                    { "purpose": "AUTOMATED_PAYOUT", "textVersion": "3" }
                  ],
                  "language": "sw",
                  "channel": "APP",
                  "evidence": { "type": "TAP_CONFIRM" }
                }
                """;
        MvcResult res = mockMvc.perform(post("/api/consents")
                        .header("Authorization", "Bearer " + farmerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.missingCoreConsents").isEmpty())
                .andExpect(jsonPath("$.nextStep").value("SUBMIT_KYC"))
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        payoutConsentId = root.get("consents").get(2).get("consentId").asString();

        // Duplicate returns 200 OK idempotently
        mockMvc.perform(post("/api/consents")
                        .header("Authorization", "Bearer " + farmerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid))
                .andExpect(status().isOk());
    }

    @Test
    @Order(3)
    void aut03_aut04_aut10_submitKycAndFourEyesManualReview() throws Exception {
        byte[] validImg = new byte[128];
        for (int i = 0; i < validImg.length; i++) validImg[i] = (byte) i;

        MockMultipartFile front = new MockMultipartFile("documentFront", "front.jpg", "image/jpeg", validImg);
        MockMultipartFile back = new MockMultipartFile("documentBack", "back.jpg", "image/jpeg", validImg);
        MockMultipartFile selfie = new MockMultipartFile("selfie", "selfie.jpg", "image/jpeg", validImg);

        // Borderline name triggers MANUAL_REVIEW (score 0.82)
        MvcResult subRes = mockMvc.perform(multipart("/api/kyc/submit")
                        .file(front).file(back).file(selfie)
                        .param("idType", "NATIONAL_ID")
                        .param("idNumber", "12345678")
                        .param("fullNameOnId", "AMINA BORDERLINE ODHIAMBO")
                        .param("dateOfBirth", "1989-04-12")
                        .param("targetTier", "TIER_2")
                        .header("Authorization", "Bearer " + farmerAccessToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn();

        borderlineKycId = objectMapper.readTree(subRes.getResponse().getContentAsString())
                .get("kycId").asString();

        // Farmer status view hides staff internals
        mockMvc.perform(get("/api/kyc/" + borderlineKycId + "/status")
                        .header("Authorization", "Bearer " + farmerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MANUAL_REVIEW"))
                .andExpect(jsonPath("$.staff").doesNotExist());

        // Support sees staff internals
        mockMvc.perform(get("/api/kyc/" + borderlineKycId + "/status")
                        .header("X-Actor-Id", UUID.randomUUID().toString())
                        .header("X-Auth-Roles", "SUPPORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staff.matchScore").value(0.82));

        // Missing justification < 20 chars -> 400 MISSING_JUSTIFICATION
        mockMvc.perform(put("/api/kyc/" + borderlineKycId + "/decision")
                        .header("X-Actor-Id", "rev-1")
                        .header("X-Auth-Roles", "KYC_REVIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "approvedTier": "TIER_2",
                                  "checklist": { "nameMatch": true, "photoMatch": true, "documentAuthentic": true, "duplicateResolved": true },
                                  "note": "too short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_JUSTIFICATION"));

        // First reviewer approval on TIER_2 moves to secondReviewPending=true
        String approveBody = """
                {
                  "decision": "APPROVE",
                  "approvedTier": "TIER_2",
                  "checklist": { "nameMatch": true, "photoMatch": true, "documentAuthentic": true, "duplicateResolved": true },
                  "note": "Compared with ID photo in low light; features and DOB match."
                }
                """;
        mockMvc.perform(put("/api/kyc/" + borderlineKycId + "/decision")
                        .header("X-Actor-Id", "rev-001")
                        .header("X-Auth-Roles", "KYC_REVIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secondReviewPending").value(true));

        // Same reviewer cannot complete four-eyes decision
        mockMvc.perform(put("/api/kyc/" + borderlineKycId + "/decision")
                        .header("X-Actor-Id", "rev-001")
                        .header("X-Auth-Roles", "KYC_REVIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SECOND_REVIEWER_REQUIRED"));

        // Second reviewer completes verification
        mockMvc.perform(put("/api/kyc/" + borderlineKycId + "/decision")
                        .header("X-Actor-Id", "rev-002")
                        .header("X-Auth-Roles", "KYC_REVIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.currentTier").value("TIER_2"));
    }

    @Test
    @Order(4)
    void aut09_refreshRotationAndReuseRevokesFamily() throws Exception {
        MvcResult refRes = mockMvc.perform(post("/api/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "refreshToken": "%s", "deviceId": "dev-1" }
                                """.formatted(farmerRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.kycTier").value("TIER_2"))
                .andReturn();

        String newRefresh = objectMapper.readTree(refRes.getResponse().getContentAsString())
                .get("refreshToken").asString();
        assertThat(newRefresh).isNotEqualTo(farmerRefreshToken);

        // Replaying old refresh token returns 401 TOKEN_REUSED and revokes family
        mockMvc.perform(post("/api/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "refreshToken": "%s", "deviceId": "dev-1" }
                                """.formatted(farmerRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REUSED"));

        // Subsequent use of rotated token is also revoked
        mockMvc.perform(post("/api/auth/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "refreshToken": "%s", "deviceId": "dev-1" }
                                """.formatted(newRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @Order(5)
    void aut11_and_aut07_stepUpWithdrawConsentAndDataRights() throws Exception {
        // Request step-up for consent change
        MvcResult stepRes = mockMvc.perform(post("/api/auth/step-up")
                        .header("Authorization", "Bearer " + farmerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "actionClass": "CONSENT_CHANGE", "deviceId": "dev-1" }
                                """))
                .andExpect(status().isAccepted())
                .andReturn();
        String chId = objectMapper.readTree(stepRes.getResponse().getContentAsString()).get("challengeId").asString();
        String otp = notificationClient.getSentMessages().getLast().payload();

        MvcResult verifyStep = mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "challengeId": "%s", "otp": "%s", "deviceId": "dev-1" }
                                """.formatted(chId, otp)))
                .andExpect(status().isOk())
                .andReturn();
        String stepToken = objectMapper.readTree(verifyStep.getResponse().getContentAsString())
                .get("stepUpToken").asString();

        mockMvc.perform(post("/api/consents/" + payoutConsentId + "/withdraw")
                        .header("Authorization", "Bearer " + farmerAccessToken)
                        .header("X-Step-Up-Token", stepToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "PRIVACY", "channel": "APP" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.effects[0]").value("PAYOUTS_REQUIRE_CONFIRMATION"));

        // Data export request requires fresh DATA_RIGHTS step-up
        MvcResult drStep = mockMvc.perform(post("/api/auth/step-up")
                        .header("Authorization", "Bearer " + farmerAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "actionClass": "DATA_RIGHTS", "deviceId": "dev-1" }
                                """))
                .andExpect(status().isAccepted())
                .andReturn();
        String drCh = objectMapper.readTree(drStep.getResponse().getContentAsString()).get("challengeId").asString();
        String drOtp = notificationClient.getSentMessages().getLast().payload();

        String drToken = objectMapper.readTree(mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "challengeId": "%s", "otp": "%s", "deviceId": "dev-1" }
                                """.formatted(drCh, drOtp)))
                .andReturn().getResponse().getContentAsString()).get("stepUpToken").asString();

        mockMvc.perform(post("/api/auth/data-requests")
                        .header("Authorization", "Bearer " + farmerAccessToken)
                        .header("X-Step-Up-Token", drToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "EXPORT", "categories": ["PROFILE", "KYC"], "deliveryChannel": "WHATSAPP" }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    @Order(6)
    void aut06_agentEnrollOnBehalfAndConfirm() throws Exception {
        UUID agentId = UUID.randomUUID();
        String agentHash = dataVaultClient.computeKeyedHash("+254733000111", "PHONE_HASH");
        userAccountRepository.save(UserAccountEntity.builder()
                .userId(agentId)
                .userType(UserType.AGENT)
                .phoneToken("vt_phone_agent1")
                .phoneHash(agentHash)
                .fullNameEnc(cryptoHashService.encryptField("Agent Joseph"))
                .status(AccountStatus.ACTIVE)
                .kycTier(KycTier.TIER_1)
                .rolesCsv(AuthRole.AGENT.name())
                .preferredLanguage("sw")
                .failedLoginCount(0)
                .lockoutCount(0)
                .createdAt(Instant.now())
                .build());

        MvcResult res = mockMvc.perform(post("/api/auth/agents/" + agentId + "/enroll-on-behalf")
                        .header("X-Actor-Id", agentId.toString())
                        .header("X-Auth-Roles", "AGENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "farmerPhoneNumber": "+254722000111",
                                  "farmerFullName": "Wanjiru Kamau",
                                  "preferredLanguage": "sw",
                                  "scopes": ["KYC_SUBMIT", "CONSENT_CAPTURE"],
                                  "cooperativeId": "coop-nyeri-04",
                                  "enrollmentLocation": { "lat": -0.4167, "lon": 36.95, "accuracyMeters": 18 }
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_FARMER_CONFIRMATION"))
                .andReturn();

        String chId = objectMapper.readTree(res.getResponse().getContentAsString()).get("challengeId").asString();
        String otp = notificationClient.getSentMessages().getLast().payload();

        mockMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "challengeId": "%s", "otp": "%s", "deviceId": "agent-dev" }
                                """.formatted(chId, otp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purpose").value("AGENT_DELEGATION"))
                .andExpect(jsonPath("$.delegationStatus").value("ACTIVE"));
    }
}
