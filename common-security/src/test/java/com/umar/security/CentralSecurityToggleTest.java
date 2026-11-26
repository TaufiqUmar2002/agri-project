package com.umar.security;

import com.umar.CommonSecurityApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = CommonSecurityApplication.class)
@ActiveProfiles("test")
class CentralSecurityToggleTest {

    @Autowired private WebApplicationContext wac;
    @Autowired private CentralSecurityFilter securityFilter;
    @Autowired private SecurityToggleService toggleService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).addFilters(securityFilter).build();
    }

    @Test
    void togglesModuleSecurityViaDbConfigDynamically() throws Exception {
        assertThat(toggleService.isSecurityEnabled("sample-module")).isTrue();

        // Turn OFF security via DB row update / controller endpoint
        mockMvc.perform(put("/api/security/modules/sample-module")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"securityEnabled\":false,\"updatedBy\":\"ops_1\",\"notes\":\"Maintenance bypass\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.SECURITY_ENABLED").value(false));

        assertThat(toggleService.isSecurityEnabled("sample-module")).isFalse();

        // Subsequent request carries X-Security-Mode: BYPASSED
        mockMvc.perform(get("/api/security/modules/sample-module"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Security-Mode", "BYPASSED"));

        // Re-enable via direct DB update
        jdbcTemplate.update("UPDATE security_module_config SET security_enabled = TRUE WHERE module_name = 'sample-module'");
        assertThat(toggleService.isSecurityEnabled("sample-module")).isTrue();
    }
}
