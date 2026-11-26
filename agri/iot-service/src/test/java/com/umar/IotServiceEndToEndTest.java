package com.umar;

import com.umar.iot.client.IotExternalClients;
import com.umar.iot.repository.IotOutboxEventRepository;
import com.umar.iot.security.IotIdentityFilter;
import com.umar.iot.service.IotIngestionService;
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
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = IotServiceApplication.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IotServiceEndToEndTest {

    @Autowired private WebApplicationContext wac;
    @Autowired private IotIdentityFilter identityFilter;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private IotExternalClients clients;
    @Autowired private IotIngestionService ingestionService;
    @Autowired private IotOutboxEventRepository outboxRepository;

    private MockMvc mockMvc;
    private static String deviceId;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).addFilters(identityFilter).build();
    }

    @Test
    @Order(1)
    void iot01_iot02_iot03_iot04_iot10_registerIngestCalibrateAndRead() throws Exception {
        clients.registerPlot("plt_iot_1", new IotExternalClients.PlotView(true, "frm_iot_1", "KE-NYERI-01", 35.0, true));

        MvcResult regRes = mockMvc.perform(post("/api/devices")
                        .header("X-Actor-Id", "agt-1")
                        .header("X-Auth-Roles", "AGENT")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deviceType": "RAIN_GAUGE",
                                  "manufacturer": "Kestrel",
                                  "model": "TB-250",
                                  "serialNumber": "TB250-001",
                                  "protocol": "JSON_V1",
                                  "plotId": "plt_iot_1",
                                  "location": {"latitude": -0.41, "longitude": 36.95, "accuracyMeters": 5},
                                  "consentRef": "cns_sensor_ok"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enrolmentToken").isNotEmpty())
                .andReturn();
        deviceId = objectMapper.readTree(regRes.getResponse().getContentAsString()).get("deviceId").asString();

        // IOT-01 Valid reading -> PUBLISHED / FLAG(CALIBRATION_DEFAULT) & impossible reading -> REJECT
        Instant obsTime = Instant.now().minus(5, ChronoUnit.MINUTES);
        assertThat(ingestionService.ingestReading(deviceId, deviceId, obsTime, "rain.tips", 4.0, "tips", 85, -90))
                .startsWith("FLAG:");
        assertThat(ingestionService.ingestReading(deviceId, deviceId, obsTime.plusSeconds(60), "rain.mm", 500.0, "mm", 85, -90))
                .isEqualTo("REJECT:OUT_OF_PHYSICAL_RANGE");

        // IOT-03 Health view for farmer vs staff
        mockMvc.perform(get("/api/devices/" + deviceId + "/health")
                        .header("X-Actor-Id", "frm_iot_1")
                        .header("X-Auth-Roles", "FARMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectivity").value("ONLINE"))
                .andExpect(jsonPath("$.battery").doesNotExist());

        // IOT-04 Calibrate beyond soft limit -> PENDING_APPROVAL then independent second operator approves
        MvcResult calRes = mockMvc.perform(put("/api/devices/" + deviceId + "/calibrate")
                        .header("X-Actor-Id", "iot-op-1")
                        .header("X-Auth-Roles", "IOT_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "channels": [{"channel": "rain.mm", "offset": 0.0, "scale": 1.15, "unit": "mm"}],
                                  "effectiveFrom": "%s",
                                  "reason": "FIELD_CHECK",
                                  "reference": {"referenceValue": 11.5, "referenceUnit": "mm"},
                                  "note": "Checked bucket against manual rain gauge calibration reference."
                                }
                                """.formatted(Instant.now())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
                .andReturn();
        String calHash = objectMapper.readTree(calRes.getResponse().getContentAsString()).get("proposalHash").asString();

        mockMvc.perform(put("/api/devices/" + deviceId + "/calibrate")
                        .header("X-Actor-Id", "iot-op-2")
                        .header("X-Auth-Roles", "IOT_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approveProposal": "%s"}
                                """.formatted(calHash)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calibrationVersion").value(2));

        // IOT-10 Staff reads RAW + rejected
        mockMvc.perform(get("/api/devices/" + deviceId + "/readings?resolution=RAW&from="
                        + Instant.now().minus(1, ChronoUnit.HOURS) + "&to=" + Instant.now())
                        .header("X-Actor-Id", "iot-op-1")
                        .header("X-Auth-Roles", "IOT_OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.count").value(1))
                .andExpect(jsonPath("$.summary.rejected").value(1));
    }

    @Test
    @Order(2)
    void iot07_iot08_iot09_listBindingApprovalAndCredentialRevoke() throws Exception {
        // IOT-09 Moving device on insured plot requires second approver
        MvcResult bRes = mockMvc.perform(put("/api/devices/" + deviceId + "/binding")
                        .header("X-Actor-Id", "iot-op-1")
                        .header("X-Auth-Roles", "IOT_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action": "MOVE", "plotId": "plt_iot_2", "reasonCode": "MISBOUND", "note": "Relocating rain gauge to adjacent plot.", "version": 4}
                                """))
                .andExpect(status().isAccepted())
                .andReturn();
        String bHash = objectMapper.readTree(bRes.getResponse().getContentAsString()).get("proposalHash").asString();

        mockMvc.perform(put("/api/devices/" + deviceId + "/binding")
                        .header("X-Actor-Id", "iot-op-2")
                        .header("X-Auth-Roles", "IOT_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approveProposal": "%s"}
                                """.formatted(bHash)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.binding.plotId").value("plt_iot_2"));

        // IOT-08 Revoke credentials -> SUSPENDED
        mockMvc.perform(post("/api/devices/" + deviceId + "/credentials")
                        .header("X-Actor-Id", "iot-op-1")
                        .header("X-Auth-Roles", "IOT_OPERATOR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"action": "REVOKE", "reasonCode": "STOLEN", "note": "Reported stolen from farm field."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        mockMvc.perform(get("/api/devices")
                        .header("X-Actor-Id", "iot-op-1")
                        .header("X-Auth-Roles", "IOT_OPERATOR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.TOTAL").value(1));

        assertThat(outboxRepository.findAll().stream().anyMatch(e -> "observation.sensor".equals(e.getTopic()))).isTrue();
    }
}
