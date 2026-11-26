package com.umar.auth.client;

import com.umar.auth.exception.AuthApiException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class LocalNotificationClient implements NotificationClient {

    @Setter
    private volatile boolean unavailable = false;

    @Getter
    private final List<SentMessage> sentMessages = new CopyOnWriteArrayList<>();

    @Override
    public String sendOtp(String phoneToken, String otpCode, String language, String channel) {
        if (unavailable) {
            throw new AuthApiException(HttpStatus.SERVICE_UNAVAILABLE, "OTP_DELIVERY_UNAVAILABLE",
                    "Unable to send the code right now. Please try again");
        }
        String effectiveChannel = "WHATSAPP".equalsIgnoreCase(channel) ? "WHATSAPP" : "SMS";
        sentMessages.add(new SentMessage(phoneToken, otpCode, language, effectiveChannel));
        log.debug("Dispatched OTP to phoneToken={} via {}", phoneToken, effectiveChannel);
        return effectiveChannel;
    }

    @Override
    public void sendSecurityAlert(String phoneToken, String templateCode, String language) {
        if (!unavailable) {
            sentMessages.add(new SentMessage(phoneToken, templateCode, language, "SMS"));
        }
    }

    public record SentMessage(String phoneToken, String payload, String language, String channel) {}
}
