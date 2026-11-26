package com.umar;

import com.umar.enums.payout.PayoutStatus;
import com.umar.enums.payout.ProviderStatus;
import com.umar.payout.client.PayoutExternalClients;
import com.umar.payout.domain.PayoutInstructionEntity;
import com.umar.payout.domain.ProviderConnectionEntity;
import com.umar.payout.repository.PayoutOutboxEventRepository;
import com.umar.payout.repository.ProviderConnectionRepository;
import com.umar.payout.security.PayoutIdentityFilter;
import com.umar.payout.service.PayoutExecutionService;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = PayoutServiceApplication.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PayoutServiceEndToEndTest {

    @Autowired private WebApplicationContext wac;
    @Autowired private PayoutIdentityFilter identityFilter;
    @Autowired private PayoutExternalClients clients;
    @Autowired private PayoutExecutionService executionService;
    @Autowired private ProviderConnectionRepository connectionRepository;
    @Autowired private PayoutOutboxEventRepository outboxRepository;

    private MockMvc mockMvc;
    private static String payoutId1;
    private static String providerRef1;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).addFilters(identityFilter).build();
    }

    @Test
    @Order(1)
    void pay01_pay02_pay03_pay05_pay09_dispatchCallbackStatementAndSettlement() throws Exception {
        connectionRepository.save(ProviderConnectionEntity.builder()
                .connectionId("MPESA_PRIMARY").provider("mpesa").network("MPESA")
                .status(ProviderStatus.ENABLED).priorityOrder(1).lastProbeOk(true)
                .updatedAt(Instant.now()).versionNum(1).build());
        connectionRepository.save(ProviderConnectionEntity.builder()
                .connectionId("MPESA_SECONDARY").provider("mpesa").network("MPESA")
                .status(ProviderStatus.ENABLED).priorityOrder(2).lastProbeOk(true)
                .updatedAt(Instant.now()).versionNum(1).build());

        clients.registerInstruction("clm_p1", new PayoutExternalClients.ClaimInstructionView(
                true, 60_000L, "KES", "SAME_WALLET", false));
        clients.registerDestination("frm_p1", new PayoutExternalClients.DestinationView(
                true, "MPESA", "vt_w1", "MPESA ****4821", null));

        PayoutInstructionEntity pay = executionService.onClaimReadyForPayout(
                "evt_p1", "claim.approved", "clm_p1", 1, "frm_p1", "SAME_WALLET", null);
        payoutId1 = pay.getPayoutId();
        providerRef1 = pay.getProviderRef();
        assertThat(pay.getStatus()).isEqualTo(PayoutStatus.PENDING);

        // PAY-02 Support sees masked ref, Finance sees full ref
        mockMvc.perform(get("/api/payouts/" + payoutId1)
                        .header("X-Actor-Id", "sup-1")
                        .header("X-Auth-Roles", "SUPPORT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerRef").doesNotExist())
                .andExpect(jsonPath("$.providerRefMasked").isNotEmpty());

        mockMvc.perform(get("/api/payouts/" + payoutId1)
                        .header("X-Actor-Id", "fin-1")
                        .header("X-Auth-Roles", "FINANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerRef").value(providerRef1));

        // PAY-03 Forged callback -> 401
        String cbBody = """
                {"providerRef":"%s","status":"COMPLETED"}
                """.formatted(providerRef1);
        mockMvc.perform(post("/api/payouts/callback/mpesa")
                        .header("X-Signature", "bad_sig")
                        .header("X-Timestamp", Instant.now().toString())
                        .header("X-Nonce", "nonce-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cbBody))
                .andExpect(status().isUnauthorized());

        // PAY-03 Signed callback -> COMPLETED
        String sig = clients.signHmacSha256(clients.getProviderSecret("mpesa"), cbBody);
        mockMvc.perform(post("/api/payouts/callback/mpesa")
                        .header("X-Signature", sig)
                        .header("X-Timestamp", Instant.now().toString())
                        .header("X-Nonce", "nonce-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cbBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // PAY-09 Import statement & PAY-05 Settlements
        LocalDate stmtDate = LocalDate.now().minusDays(1);
        clients.putStatementFile("vt_stmt_1", List.of(
                new PayoutExternalClients.StatementLine(providerRef1, 60_000L, 120L, "KES", "COMPLETED")));
        mockMvc.perform(post("/api/payouts/statements")
                        .header("X-Actor-Id", "fin-1")
                        .header("X-Auth-Roles", "FINANCE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "connection": "MPESA_PRIMARY",
                                  "statementDate": "%s",
                                  "fileToken": "vt_stmt_1",
                                  "checksum": "chk_1"
                                }
                                """.formatted(stmtDate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.matched").value(1));

        mockMvc.perform(get("/api/payouts/settlements?from=" + stmtDate + "&to=" + stmtDate)
                        .header("X-Actor-Id", "fin-1")
                        .header("X-Auth-Roles", "FINANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.grossMinor").value(60000));
    }

    @Test
    @Order(2)
    void pay04_pay07_pay10_pay11_fallbackCancelProviderStatusAndList() throws Exception {
        clients.registerInstruction("clm_p2", new PayoutExternalClients.ClaimInstructionView(
                true, 45_000L, "KES", "SAME_WALLET", false));
        clients.setNextDispatchResult("MPESA_PRIMARY", new PayoutExternalClients.ProviderDispatchResult(
                false, "FAILED", "MP_FAIL_2", "GATEWAY_TIMEOUT", true));
        PayoutInstructionEntity p2 = executionService.onClaimReadyForPayout(
                "evt_p2", "claim.approved", "clm_p2", 1, "frm_p2", "SAME_WALLET", null);

        // Cash-out fallback blocked for wallet payout -> 422 ROUTE_NOT_AUTHORIZED
        mockMvc.perform(post("/api/payouts/" + p2.getPayoutId() + "/fallback")
                        .header("X-Actor-Id", "fin-1")
                        .header("X-Auth-Roles", "FINANCE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "USE_CASH_OUT",
                                  "agentAreaCode": "NYERI",
                                  "reason": "Switch failed wallet payout to cash-out agent."
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ROUTE_NOT_AUTHORIZED"));

        // Switch provider -> PENDING on MPESA_SECONDARY
        mockMvc.perform(post("/api/payouts/" + p2.getPayoutId() + "/fallback")
                        .header("X-Actor-Id", "fin-1")
                        .header("X-Auth-Roles", "FINANCE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "SWITCH_PROVIDER",
                                  "targetConnection": "MPESA_SECONDARY",
                                  "reason": "Primary M-Pesa aggregator degraded; routing via secondary."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connection").value("MPESA_SECONDARY"));

        // PAY-10 Pause provider -> PAUSED
        mockMvc.perform(put("/api/payouts/providers/mpesa/status")
                        .header("X-Actor-Id", "fin-1")
                        .header("X-Auth-Roles", "FINANCE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "PAUSED",
                                  "version": 1,
                                  "reason": "Scheduled maintenance window on primary M-Pesa link."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));

        // PAY-07 Cancel cash-out payout
        clients.registerInstruction("clm_p3", new PayoutExternalClients.ClaimInstructionView(
                true, 20_000L, "KES", "CASH_OUT_AGENT", false));
        PayoutInstructionEntity p3 = executionService.onClaimReadyForPayout(
                "evt_p3", "claim.approved", "clm_p3", 1, "frm_p3", "CASH_OUT_AGENT", null);
        executionService.onCancelRequested("evt_cancel_p3", "clm_p3");

        mockMvc.perform(get("/api/payouts/" + p3.getPayoutId())
                        .header("X-Actor-Id", "fin-1")
                        .header("X-Auth-Roles", "FINANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // PAY-11 List payouts
        mockMvc.perform(get("/api/payouts")
                        .header("X-Actor-Id", "fin-1")
                        .header("X-Auth-Roles", "FINANCE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));

        assertThat(outboxRepository.findAll().stream().anyMatch(e -> "payout.completed".equals(e.getEventType()))).isTrue();
    }
}
