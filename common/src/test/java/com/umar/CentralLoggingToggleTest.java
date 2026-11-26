package com.umar;

import com.umar.enums.logging.LogType;
import com.umar.logging.CentralLoggingFilter;
import com.umar.logging.CentralLoggingService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CommonApplication.class)
@ActiveProfiles("test")
class CentralLoggingToggleTest {

    @Autowired private WebApplicationContext wac;
    @Autowired private CentralLoggingFilter loggingFilter;
    @Autowired private CentralLoggingService loggingService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        loggingService.clearRecentEntries();
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).addFilters(loggingFilter).build();
    }

    @Test
    void togglesDifferentLogTypesViaPropertiesAndDb() throws Exception {
        // Property file default: debugEnabled=false, infoEnabled=true, exceptionEnabled=true
        assertThat(loggingService.isTypeEnabled(LogType.DEBUG)).isFalse();
        assertThat(loggingService.isTypeEnabled(LogType.INFO)).isTrue();
        assertThat(loggingService.isTypeEnabled(LogType.EXCEPTION)).isTrue();

        loggingService.debug("TestComponent", "Should be suppressed by default");
        loggingService.info("TestComponent", "Initial info message");
        assertThat(loggingService.getRecentEntries().stream().anyMatch(e -> e.type() == LogType.DEBUG)).isFalse();
        assertThat(loggingService.getRecentEntries().stream().anyMatch(e -> e.type() == LogType.INFO)).isTrue();

        // Turn DEBUG ON and INFO OFF dynamically via DB API
        mockMvc.perform(put("/api/logging/modules/common-test-module")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"debugEnabled\":true,\"infoEnabled\":false,\"exceptionEnabled\":false,\"updatedBy\":\"ops\"}"))
                .andExpect(status().isOk());

        assertThat(loggingService.isTypeEnabled(LogType.DEBUG)).isTrue();
        assertThat(loggingService.isTypeEnabled(LogType.INFO)).isFalse();
        assertThat(loggingService.isTypeEnabled(LogType.EXCEPTION)).isFalse();

        loggingService.clearRecentEntries();
        loggingService.debug("TestComponent", "Debug now enabled via DB");
        loggingService.info("TestComponent", "Info suppressed via DB");
        loggingService.exception("TestComponent", "TEST_ERR", new RuntimeException("Suppressed exception"));

        assertThat(loggingService.getRecentEntries().stream().anyMatch(e -> e.type() == LogType.DEBUG)).isTrue();
        assertThat(loggingService.getRecentEntries().stream().anyMatch(e -> e.type() == LogType.INFO)).isFalse();
        assertThat(loggingService.getRecentEntries().stream().anyMatch(e -> e.type() == LogType.EXCEPTION)).isFalse();

        // Direct SQL update re-enables INFO & EXCEPTION & Request/Response log
        jdbcTemplate.update("UPDATE logging_module_config SET info_enabled = TRUE, exception_enabled = TRUE WHERE module_name = 'common-test-module'");
        assertThat(loggingService.isTypeEnabled(LogType.INFO)).isTrue();
        assertThat(loggingService.isTypeEnabled(LogType.EXCEPTION)).isTrue();

        mockMvc.perform(get("/api/logging/modules/common-test-module"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.DEBUG_ENABLED").value(true));
    }
}
