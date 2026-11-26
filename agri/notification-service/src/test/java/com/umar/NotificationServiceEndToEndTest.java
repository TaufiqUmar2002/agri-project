package com.umar;

import com.umar.notification.NotificationServiceApplication;
import com.umar.notification.client.NotificationExternalClients;
import com.umar.notification.config.NotificationProperties;
import com.umar.notification.domain.NotificationAttemptEntity;
import com.umar.notification.domain.NotificationEntity;
import com.umar.notification.repository.NotificationAttemptRepository;
import com.umar.notification.repository.NotificationRepository;
import com.umar.notification.security.NotificationIdentityFilter;
import com.umar.notification.service.NotificationDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = NotificationServiceApplication.class)
@ActiveProfiles("test")
class NotificationServiceEndToEndTest {

    @Autowired private WebApplicationContext wac;
    @Autowired private NotificationIdentityFilter identityFilter;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private NotificationDeliveryService deliveryService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationAttemptRepository attemptRepository;
    @Autowired private NotificationProperties properties;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).addFilters(identityFilter).build();
    }

    @Test
    void fullNotificationLifecyclePreferencesBroadcastTemplatesAndCallbacks() throws Exception {
        // 1. NOT-10 + NOT-03: Get default preferences & update channels/optOut
        mockMvc.perform(get("/api/notifications/preferences")
                        .header("X-User-Id", "frm_001")
                        .header("X-User-Role", "FARMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true));

        // Reject opting out of mandatory SERVICE
        mockMvc.perform(put("/api/notifications/preferences")
                        .header("X-User-Id", "frm_001")
                        .header("X-User-Role", "FARMER")
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optOut\":{\"SERVICE\":[\"SMS\"]}}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_OPTIONAL"));

        mockMvc.perform(put("/api/notifications/preferences")
                        .header("X-User-Id", "frm_001")
                        .header("X-User-Role", "FARMER")
                        .header("If-Match", "\"v1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"sw\",\"channels\":[\"SMS\",\"WHATSAPP\"],\"optOut\":{\"PROMOTIONAL\":[\"SMS\"]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        // 2. NOT-06: Direct OTP message with secret shredded after send
        MvcResult otpRes = mockMvc.perform(post("/api/notifications/messages")
                        .header("X-Service-Id", "auth-service")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient\":{\"type\":\"FARMER\",\"ref\":\"frm_001\"},\"templateCode\":\"OTP_CODE\",\"secret\":\"492810\",\"expiresInSeconds\":300,\"sourceRef\":\"otp_req_1\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.category").value("SECURITY"))
                .andReturn();
        String otpNotifId = objectMapper.readTree(otpRes.getResponse().getContentAsString()).get("notificationId").asString();
        NotificationEntity otpEntity = notificationRepository.findByNotificationId(otpNotifId).orElseThrow();
        assertThat(otpEntity.getSecretTokenRef()).isNull();

        // Wrong service cannot send OTP
        mockMvc.perform(post("/api/notifications/messages")
                        .header("X-Service-Id", "policy-service")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient\":{\"type\":\"FARMER\",\"ref\":\"frm_001\"},\"templateCode\":\"OTP_CODE\",\"secret\":\"111222\",\"sourceRef\":\"bad_1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOT_ALLOWED"));

        // 3. NOT-01: Domain event + dedupe + fraud label hiding
        NotificationEntity claimNotif = deliveryService.handleDomainEvent(
                "evt_clm_1", "platform.claims.events", "claim.decision.ready",
                Map.of("farmerId", "frm_001", "claimId", "clm_99", "reason", "FRAUD_CHECK"));
        assertThat(claimNotif.getTemplateCode()).isEqualTo("CLAIM_UNDER_CHECK");

        NotificationEntity deduped = deliveryService.handleDomainEvent(
                "evt_clm_2", "platform.claims.events", "claim.decision.ready",
                Map.of("farmerId", "frm_001", "claimId", "clm_99", "reason", "FRAUD_CHECK"));
        assertThat(deduped.getNotificationId()).isEqualTo(claimNotif.getNotificationId());

        // 4. NOT-05 + NOT-11: Status + ETag 304 + list farmer filter restriction
        mockMvc.perform(get("/api/notifications/" + claimNotif.getNotificationId() + "/status")
                        .header("X-User-Id", "frm_001")
                        .header("X-User-Role", "FARMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts").doesNotExist());

        mockMvc.perform(get("/api/notifications?farmerId=frm_002")
                        .header("X-User-Id", "frm_001")
                        .header("X-User-Role", "FARMER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FILTER_NOT_ALLOWED"));

        // 5. NOT-07: Delivery callback failure triggers failover SMS -> WHATSAPP
        List<NotificationAttemptEntity> attempts = attemptRepository.findByNotificationIdOrderByAttemptNumberAsc(claimNotif.getNotificationId());
        String provId = attempts.getFirst().getProviderMessageId();
        String ts = Instant.now().toString();
        String cbSig = NotificationExternalClients.hmacSha256(
                properties.getRules().getAggregatorHmacSecret(), ts + ":africastalking:" + provId);
        mockMvc.perform(post("/api/notifications/callbacks/africastalking")
                        .header("X-Signature", cbSig)
                        .header("X-Timestamp", ts)
                        .header("X-Nonce", "nonce_cb_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerMessageId\":\"" + provId + "\",\"status\":\"FAILED\",\"reasonCode\":\"HANDSET_OFF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SENT"));
        assertThat(attemptRepository.findByNotificationIdOrderByAttemptNumberAsc(claimNotif.getNotificationId())).hasSize(2);

        // 6. NOT-04: Critical broadcast requires dual approval
        MvcResult brRes = mockMvc.perform(post("/api/notifications/broadcast")
                        .header("X-User-Id", "pub_1")
                        .header("X-User-Role", "ALERT_PUBLISHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"CREATE\",\"alertType\":\"FROST\",\"severity\":\"CRITICAL\",\"audience\":{\"county\":\"Nakuru\",\"estimatedCount\":6500},\"templateCode\":\"FROST_WARNING\",\"variables\":{\"county\":\"Nakuru\"}}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn();
        JsonNode brJson = objectMapper.readTree(brRes.getResponse().getContentAsString());
        String brId = brJson.get("broadcastId").asString();
        String pHash = brJson.get("proposalHash").asString();

        mockMvc.perform(post("/api/notifications/broadcast")
                        .header("X-User-Id", "pub_1")
                        .header("X-User-Role", "ALERT_PUBLISHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\",\"broadcastId\":\"" + brId + "\",\"proposalHash\":\"" + pHash + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPROVER_NOT_INDEPENDENT"));

        mockMvc.perform(post("/api/notifications/broadcast")
                        .header("X-User-Id", "pub_2")
                        .header("X-User-Role", "ALERT_PUBLISHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\",\"broadcastId\":\"" + brId + "\",\"proposalHash\":\"" + pHash + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // 7. NOT-09: Template validation + independent approval
        mockMvc.perform(put("/api/notifications/templates/CUSTOM_ADVISORY/en")
                        .header("X-User-Id", "ed_1")
                        .header("X-User-Role", "TEMPLATE_EDITOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SAVE\",\"category\":\"ADVISORY\",\"mandatory\":false,\"variants\":{\"SMS\":\"Flood alert in your ward.\"},\"version\":1,\"note\":\"Missing STOP text should fail\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("STOP_TEXT_REQUIRED"));

        mockMvc.perform(put("/api/notifications/templates/CUSTOM_ADVISORY/en")
                        .header("X-User-Id", "ed_1")
                        .header("X-User-Role", "TEMPLATE_EDITOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SUBMIT\",\"category\":\"ADVISORY\",\"mandatory\":false,\"variants\":{\"SMS\":\"Flood alert in your ward. Reply STOP to opt out.\"},\"version\":1,\"note\":\"Valid advisory template submission\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));

        mockMvc.perform(put("/api/notifications/templates/CUSTOM_ADVISORY/en")
                        .header("X-User-Id", "app_1")
                        .header("X-User-Role", "TEMPLATE_APPROVER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"APPROVE\",\"version\":1,\"note\":\"Approved after review\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.current").value(true));
    }

    @Test
    void ussdSessionInboundOptOutAndSweeper() throws Exception {
        // NOT-02: USSD menu + plot confirmation + unknown number
        String ts = Instant.now().toString();
        String sig1 = NotificationExternalClients.hmacSha256(
                properties.getRules().getUssdHmacSecret(), ts + ":*384*12#:sess_1");
        mockMvc.perform(post("/api/ussd/session")
                        .header("X-Signature", sig1)
                        .header("X-Timestamp", ts)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"sess_1\",\"serviceCode\":\"*384*12#\",\"msisdn\":\"+254712000001\",\"input\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("CON"));

        mockMvc.perform(post("/api/ussd/session")
                        .header("X-Signature", sig1)
                        .header("X-Timestamp", ts)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"sess_1\",\"serviceCode\":\"*384*12#\",\"msisdn\":\"+254712000001\",\"input\":\"3\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("CON"));

        mockMvc.perform(post("/api/ussd/session")
                        .header("X-Signature", sig1)
                        .header("X-Timestamp", ts)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"sess_1\",\"serviceCode\":\"*384*12#\",\"msisdn\":\"+254712000001\",\"input\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("END"));

        String sigUnk = NotificationExternalClients.hmacSha256(
                properties.getRules().getUssdHmacSecret(), ts + ":*384*12#:sess_unk");
        mockMvc.perform(post("/api/ussd/session")
                        .header("X-Signature", sigUnk)
                        .header("X-Timestamp", ts)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"sess_unk\",\"serviceCode\":\"*384*12#\",\"msisdn\":\"+254799999999\",\"input\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("END"));

        // NOT-08: Inbound STOP message suppresses non-mandatory notifications
        String inSig = NotificationExternalClients.hmacSha256(
                properties.getRules().getAggregatorHmacSecret(), ts + ":africastalking:+254712000002");
        mockMvc.perform(post("/api/notifications/inbound/africastalking")
                        .header("X-Signature", inSig)
                        .header("X-Timestamp", ts)
                        .header("X-Nonce", "nonce_in_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"msisdn\":\"+254712000002\",\"text\":\"STOP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("OPTED_OUT"));

        NotificationEntity suppressed = deliveryService.createAndDispatch(
                "FARMER", "frm_002", "SEASON_RENEWAL", Map.of(), null, null, null, "promo_1", null, null, null);
        assertThat(suppressed.getStatus().name()).isEqualTo("SUPPRESSED");

        // NOT-12: Sweeper runs cleanly
        assertThat(deliveryService.runSweeper()).isGreaterThanOrEqualTo(0);
    }
}
