package com.umar;

import com.umar.plot.client.LocalFarmerProfileClient;
import com.umar.plot.client.LocalLandCoverClient;
import com.umar.plot.security.PlotIdentityFilter;
import com.umar.plot.service.PlotService;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = PlotRegistryApplication.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlotRegistryEndToEndTest {

    @Autowired private WebApplicationContext wac;
    @Autowired private PlotIdentityFilter identityFilter;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private LocalFarmerProfileClient farmerProfileClient;
    @Autowired private LocalLandCoverClient landCoverClient;
    @Autowired private PlotService plotService;

    private MockMvc mockMvc;
    private static final UUID FARMER_ID = UUID.randomUUID();
    private static final UUID FARMER_2_ID = UUID.randomUUID();
    private static String plotId1;
    private static String heldPlotId;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).addFilters(identityFilter).build();
        farmerProfileClient.setUnavailable(false);
        farmerProfileClient.registerFarmer(FARMER_ID, "ACTIVE", FARMER_ID.toString());
        farmerProfileClient.registerFarmer(FARMER_2_ID, "ACTIVE", FARMER_2_ID.toString());
        landCoverClient.setAvailable(true);
        landCoverClient.setDefaultCroplandPercent(94.0);
    }

    @Test
    @Order(1)
    void plt01_plt06_plt03_registerValidateAndRecordSeasonWithLock() throws Exception {
        String nowIso = Instant.now().minus(5, ChronoUnit.MINUTES).toString();
        // Self-intersecting polygon rejected with INVALID_GEOMETRY
        String badPoly = """
                {
                  "farmerId": "%s",
                  "name": "Bad Field",
                  "captureMethod": "MAP_DRAW",
                  "geometry": { "type": "Polygon", "coordinates": [[[36.950,-0.417],[36.952,-0.415],[36.952,-0.417],[36.950,-0.415],[36.950,-0.417]]] },
                  "capturedAt": "%s"
                }
                """.formatted(FARMER_ID, nowIso);
        mockMvc.perform(post("/api/plots")
                        .header("X-Actor-Id", FARMER_ID.toString())
                        .header("X-Auth-Roles", "FARMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badPoly))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GEOMETRY"));

        String validBody = """
                {
                  "farmerId": "%s",
                  "name": "Lower field",
                  "captureMethod": "MAP_DRAW",
                  "geometry": { "type": "Polygon", "coordinates": [[[36.9500,-0.4170],[36.9520,-0.4170],[36.9520,-0.4150],[36.9500,-0.4150],[36.9500,-0.4170]]] },
                  "capturedAt": "%s",
                  "deviceId": "dev-1"
                }
                """.formatted(FARMER_ID, nowIso);

        MvcResult res = mockMvc.perform(post("/api/plots")
                        .header("X-Actor-Id", FARMER_ID.toString())
                        .header("X-Auth-Roles", "FARMER")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REGISTERED"))
                .andReturn();
        plotId1 = objectMapper.readTree(res.getResponse().getContentAsString()).get("plotId").asString();

        // PLT-06 Validate plot
        mockMvc.perform(post("/api/plots/" + plotId1 + "/validate")
                        .header("X-Service-Identity", "policy-service")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"post-registration\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("VALIDATED"));

        // PLT-03 Record season
        LocalDate plant = LocalDate.now().minusDays(5);
        LocalDate harvest = plant.plusDays(120);
        mockMvc.perform(put("/api/plots/" + plotId1 + "/season")
                        .header("X-Actor-Id", FARMER_ID.toString())
                        .header("X-Auth-Roles", "FARMER")
                        .header("If-Match", "\"1\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cropCode": "MAIZE",
                                  "plantingDate": "%s",
                                  "expectedHarvestDate": "%s",
                                  "irrigationType": "RAINFED"
                                }
                                """.formatted(plant, harvest)))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.season.cropCode").value("MAIZE"));

        // Activate policy -> locks season; changing planting date returns 409 SEASON_LOCKED
        plotService.onPolicyActivated(plotId1, "pol_1", Instant.now().minus(1, ChronoUnit.DAYS), Instant.now().plus(90, ChronoUnit.DAYS));
        mockMvc.perform(put("/api/plots/" + plotId1 + "/season")
                        .header("X-Actor-Id", FARMER_ID.toString())
                        .header("X-Auth-Roles", "FARMER")
                        .header("If-Match", "\"2\"")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cropCode": "MAIZE",
                                  "plantingDate": "%s",
                                  "expectedHarvestDate": "%s"
                                }
                                """.formatted(plant.plusDays(10), harvest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEASON_LOCKED"));
    }

    @Test
    @Order(2)
    void plt04_plt07_plt05_overlapDuplicateFourEyesReviewAndWithin() throws Exception {
        String nowIso = Instant.now().minus(5, ChronoUnit.MINUTES).toString();
        // Register duplicate polygon by farmer 2 -> HELD with DUPLICATE
        String dupBody = """
                {
                  "farmerId": "%s",
                  "name": "Duplicate Field",
                  "captureMethod": "MAP_DRAW",
                  "geometry": { "type": "Polygon", "coordinates": [[[36.9500,-0.4170],[36.9520,-0.4170],[36.9520,-0.4150],[36.9500,-0.4150],[36.9500,-0.4170]]] },
                  "capturedAt": "%s"
                }
                """.formatted(FARMER_2_ID, nowIso);
        MvcResult dupRes = mockMvc.perform(post("/api/plots")
                        .header("X-Actor-Id", FARMER_2_ID.toString())
                        .header("X-Auth-Roles", "FARMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dupBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("HELD"))
                .andReturn();
        heldPlotId = objectMapper.readTree(dupRes.getResponse().getContentAsString()).get("plotId").asString();

        // Farmer sees UNDER_REVIEW instead of HELD
        mockMvc.perform(get("/api/plots/" + heldPlotId)
                        .header("X-Actor-Id", FARMER_2_ID.toString())
                        .header("X-Auth-Roles", "FARMER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"));

        // Four-eyes review on DUPLICATE plot
        String confirmBody = """
                {
                  "decision": "CONFIRM_VALID",
                  "resolvedFlags": ["DUPLICATE"],
                  "evidence": ["IMAGERY_CHECKED", "COOPERATIVE_CONFIRMATION"],
                  "note": "Checked satellite imagery and cooperative confirmed lease boundary."
                }
                """;
        mockMvc.perform(put("/api/plots/" + heldPlotId + "/review")
                        .header("X-Actor-Id", "rev-a")
                        .header("X-Auth-Roles", "PLOT_REVIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secondReviewPending").value(true));

        mockMvc.perform(put("/api/plots/" + heldPlotId + "/review")
                        .header("X-Actor-Id", "rev-a")
                        .header("X-Auth-Roles", "PLOT_REVIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SECOND_REVIEWER_REQUIRED"));

        mockMvc.perform(put("/api/plots/" + heldPlotId + "/review")
                        .header("X-Actor-Id", "rev-b")
                        .header("X-Auth-Roles", "PLOT_REVIEWER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTERED"))
                .andExpect(jsonPath("$.secondReviewPending").value(false));

        // PLT-05 Find Plots Within Event Polygon returns active validated plot1
        mockMvc.perform(post("/api/plots/within")
                        .header("X-Service-Identity", "trigger-engine")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "polygon": { "type": "Polygon", "coordinates": [[[36.949,-0.418],[36.953,-0.418],[36.953,-0.414],[36.949,-0.414],[36.949,-0.418]]] },
                                  "matchMode": "INTERSECTS"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedCount").value(1))
                .andExpect(jsonPath("$.items[0].plotId").value(plotId1));
    }
}
