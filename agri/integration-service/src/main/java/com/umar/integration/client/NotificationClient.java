package com.umar.integration.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class NotificationClient {

    private final List<AlertRecord> sentAlerts = new CopyOnWriteArrayList<>();

    public void notifyPartner(String partnerId, String subject, String detail) {
        log.info("Notification to partner {}: {} - {}", partnerId, subject, detail);
        sentAlerts.add(new AlertRecord(partnerId, subject, detail));
    }

    public void notifyStaff(String role, String subject, String detail) {
        log.info("Notification to staff {}: {} - {}", role, subject, detail);
        sentAlerts.add(new AlertRecord(role, subject, detail));
    }

    public List<AlertRecord> getSentAlerts() {
        return List.copyOf(sentAlerts);
    }

    public record AlertRecord(String recipient, String subject, String detail) {}
}
