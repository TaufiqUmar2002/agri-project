package com.umar;

import com.umar.integration.ExtensionHookCoordinator;
import com.umar.integration.security.HubIdentityFilter;
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
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = IntegrationServiceApplication.class)
@ActiveProfiles("test")
class IntegrationBridgeEndToEndTest {

    @Autowired private WebApplicationContext wac;
    @Autowired private HubIdentityFilter hubIdentityFilter;
    @Autowired private ExtensionHookCoordinator extensionHookCoordinator;
    @Autowired private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).addFilters(hubIdentityFilter).build();
    }

    @Test
    void bridgeVaultEncryptDecryptAndExtensionHooksWork() throws Exception {
        // 1. Encrypt & decrypt via integration-service bridge
        MvcResult encRes = mockMvc.perform(post("/api/integrations/bridge/vault/encrypt")
                        .header("X-Service-Identity", "policy-service")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plaintext\":\"+254712345678\",\"dataClass\":\"PHONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ciphertext").isNotEmpty())
                .andReturn();
        String cipher = objectMapper.readTree(encRes.getResponse().getContentAsString()).get("ciphertext").asString();
        assertThat(cipher).startsWith("vlt:v1:");

        mockMvc.perform(post("/api/integrations/bridge/vault/decrypt")
                        .header("X-Service-Identity", "policy-service")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ciphertext\":\"" + cipher + "\",\"dataClass\":\"PHONE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plaintext").value("+254712345678"));

        // 2. Register & invoke extension hook via integration-service REST bridge
        mockMvc.perform(post("/api/extensions/points/register")
                        .header("X-Service-Identity", "policy-service")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceName\":\"policy-service\",\"points\":[{\"hookId\":\"policy.pre-quote\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REGISTERED"));

        mockMvc.perform(post("/api/extensions/invoke")
                        .header("X-Service-Identity", "policy-service")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hookId\":\"policy.pre-quote\",\"callerService\":\"policy-service\",\"input\":{\"amount\":1000}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true));

        assertThat(extensionHookCoordinator.getRegisteredDescriptors()).isNotEmpty();
    }
}
