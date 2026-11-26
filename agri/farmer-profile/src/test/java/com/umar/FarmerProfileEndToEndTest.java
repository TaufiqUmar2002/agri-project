package com.umar;

import com.umar.enums.auth.KycTier;
import com.umar.enums.farmer.GroupStatus;
import com.umar.farmer.client.LocalAuthKycClient;
import com.umar.farmer.client.LocalDataVaultClient;
import com.umar.farmer.client.LocalRemoteSectionClient;
import com.umar.farmer.client.LocalWalletProviderClient;
import com.umar.farmer.domain.entity.CooperativeGroupEntity;
import com.umar.farmer.repository.CooperativeGroupRepository;
import com.umar.farmer.security.FarmerIdentityFilter;
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

import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = FarmerProfileApplication.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FarmerProfileEndToEndTest {

    @Autowired private WebApplicationContext wac;
    @Autowired private FarmerIdentityFilter identityFilter;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LocalAuthKycClient authKycClient;
    @Autowired private LocalDataVaultClient dataVaultClient;
    @Autowired private LocalWalletProviderClient walletProviderClient;
    @Autowired private LocalRemoteSectionClient remoteSectionClient;
    @Autowired private CooperativeGroupRepository groupRepository;

    private MockMvc mockMvc;
    private static UUID farmerUserId = UUID.randomUUID();
    private static String farmerId;
    private static String reviewWalletId;
    private static String groupId;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).addFilters(identityFilter).build();
        authKycClient.setUnavailable(false);
        dataVaultClient.setUnavailable(false);
        walletProviderClient.setUnavailable(false);
        remoteSectionClient.setPayoutsUnavailable(false);
    }

    @Test
    @Order(1)
    void far01_and_far02_createProfileAndPartialRemoteDegradation() throws Exception {
        UUID tier0User = UUID.randomUUID();
        authKycClient.registerIdentity(tier0User,
                new LocalAuthKycClient.IdentitySnapshot("ACTIVE", KycTier.TIER_0, Set.of("FARMER"), true));
        mockMvc.perform(post("/api/farmers")
                        .header("X-Actor-Id", tier0User.toString())
                        .header("X-Auth-Roles", "FARMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preferredLanguage": "sw",
                                  "preferredChannel": "SMS",
                                  "region": { "country": "KE", "district": "NYERI", "ward": "MWEIGA" },
                                  "farmSizeBand": "BAND_1_TO_2",
                                  "primaryCrops": ["MAIZE"]
                                }
                                """))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("KYC_REQUIRED"));

        authKycClient.registerIdentity(farmerUserId,
                new LocalAuthKycClient.IdentitySnapshot("ACTIVE", KycTier.TIER_1, Set.of("FARMER"), true));
        walletProviderClient.registerAccountHolder("+254712345678", "AMINA ODHIAMBO");

        String body = """
                {
                  "preferredLanguage": "sw",
                  "preferredChannel": "WHATSAPP",
                  "displayName": "Amina O.",
                  "region": { "country": "KE", "district": "NYERI", "ward": "MWEIGA", "village": "Kiawara" },
                  "farmSizeBand": "BAND_1_TO_2",
                  "primaryCrops": ["MAIZE", "BEANS"],
                  "wallet": { "provider": "MPESA", "walletNumber": "+254712345678", "accountHolderName": "AMINA ODHIAMBO" }
                }
                """;
        MvcResult res = mockMvc.perform(post("/api/farmers")
                        .header("X-Actor-Id", farmerUserId.toString())
                        .header("X-Auth-Roles", "FARMER")
                        .header("X-Step-Up-Token", "su_first")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.wallet.status").value("VERIFIED"))
                .andExpect(jsonPath("$.wallet.isPrimary").value(true))
                .andReturn();

        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        farmerId = root.get("farmerId").asString();

        // FAR-02 with degraded payouts section
        remoteSectionClient.setPayoutsUnavailable(true);
        mockMvc.perform(get("/api/farmers/" + farmerId + "?include=wallets,policies,payouts")
                        .header("X-Actor-Id", farmerUserId.toString())
                        .header("X-Auth-Roles", "FARMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.wallets[0].maskedNumber").value("+2547*****678"))
                .andExpect(jsonPath("$.payouts.status").value("UNAVAILABLE"));
    }

    @Test
    @Order(2)
    void far03_updateProfileWithOptimisticLockAndRejectNonEditable() throws Exception {
        mockMvc.perform(put("/api/farmers/" + farmerId)
                        .header("X-Actor-Id", farmerUserId.toString())
                        .header("X-Auth-Roles", "FARMER")
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "legalName": "Tampered Name" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FIELD_NOT_EDITABLE"));

        mockMvc.perform(put("/api/farmers/" + farmerId)
                        .header("X-Actor-Id", farmerUserId.toString())
                        .header("X-Auth-Roles", "FARMER")
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "preferredLanguage": "en", "preferredChannel": "SMS" }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.changedFields[0]").value("preferredLanguage"));

        // Stale ETag returns 412 VERSION_CONFLICT
        mockMvc.perform(put("/api/farmers/" + farmerId)
                        .header("X-Actor-Id", farmerUserId.toString())
                        .header("X-Auth-Roles", "FARMER")
                        .header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "preferredLanguage": "sw" }
                                """))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    @Order(3)
    void far04_and_far07_walletNameMatchBorderlineAndReview() throws Exception {
        walletProviderClient.registerAccountHolder("+254733000222", "A. ODHIAMBO");

        MvcResult wRes = mockMvc.perform(post("/api/farmers/" + farmerId + "/wallets")
                        .header("X-Actor-Id", farmerUserId.toString())
                        .header("X-Auth-Roles", "FARMER")
                        .header("X-Step-Up-Token", "su_w2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider": "MPESA",
                                  "walletNumber": "+254733000222",
                                  "accountHolderName": "AMINA ODHIAMBO",
                                  "makePrimary": true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andReturn();
        reviewWalletId = objectMapper.readTree(wRes.getResponse().getContentAsString()).get("walletId").asString();

        mockMvc.perform(put("/api/farmers/" + farmerId + "/wallets/" + reviewWalletId + "/review")
                        .header("X-Actor-Id", "fin-0187")
                        .header("X-Auth-Roles", "FINANCE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "APPROVE",
                                  "evidence": ["COOPERATIVE_CONFIRMATION", "PHONE_CALL_VERIFIED"],
                                  "note": "Wallet name shows initial A. only. Cooperative secretary confirmed ownership."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.secondReviewPending").value(false));
    }

    @Test
    @Order(4)
    void far05_far06_far08_groupsInviteAndSearch() throws Exception {
        UUID leaderId = UUID.randomUUID();
        authKycClient.registerIdentity(leaderId,
                new LocalAuthKycClient.IdentitySnapshot("ACTIVE", KycTier.TIER_1, Set.of("AGENT"), true));

        MvcResult grpRes = mockMvc.perform(post("/api/farmers/groups")
                        .header("X-Actor-Id", leaderId.toString())
                        .header("X-Auth-Roles", "AGENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Nyeri Maize Growers Cooperative",
                                  "groupType": "COOPERATIVE",
                                  "registrationNumber": "CS/2014/04418",
                                  "region": { "country": "KE", "district": "NYERI" },
                                  "maxMembers": 600
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                .andReturn();
        groupId = objectMapper.readTree(grpRes.getResponse().getContentAsString()).get("groupId").asString();

        CooperativeGroupEntity g = groupRepository.findById(UUID.fromString(groupId)).orElseThrow();
        g.setStatus(GroupStatus.ACTIVE);
        groupRepository.save(g);

        mockMvc.perform(post("/api/farmers/groups/" + groupId + "/members")
                        .header("X-Actor-Id", leaderId.toString())
                        .header("X-Auth-Roles", "AGENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "members": [
                                    { "phoneNumber": "+254722000111", "fullName": "Wanjiru Kamau", "preferredLanguage": "sw" },
                                    { "phoneNumber": "invalid", "fullName": "Bad Row" }
                                  ]
                                }
                                """))
                .andExpect(status().isMultiStatus())
                .andExpect(jsonPath("$.accepted").value(1))
                .andExpect(jsonPath("$.rejected").value(1));

        // Search without reason for phoneNumber fails with REASON_REQUIRED
        mockMvc.perform(get("/api/farmers/search?phoneNumber=%2B254712345678")
                        .header("X-Actor-Id", "sup-1")
                        .header("X-Auth-Roles", "SUPPORT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));

        mockMvc.perform(get("/api/farmers/search?region=NYERI&name=Ami")
                        .header("X-Actor-Id", "sup-1")
                        .header("X-Auth-Roles", "SUPPORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
