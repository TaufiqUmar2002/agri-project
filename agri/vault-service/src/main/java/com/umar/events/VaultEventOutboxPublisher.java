package com.umar.events;

import com.umar.repository.events.VaultEventOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Periodically relays PENDING {@link VaultEventOutbox} events to Kafka topic
 * {@code platform.vault.events} (section 2.14, 5). Fails gracefully when Kafka
 * is disabled or unavailable so core vault operations are unaffected.
 */
@Slf4j
@Service
public class VaultEventOutboxPublisher {

    private static final String KAFKA_TOPIC = "platform.vault.events";

    private final VaultEventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${vault.kafka.outbox-enabled:false}")
    private boolean outboxKafkaEnabled;

    public VaultEventOutboxPublisher(
            VaultEventOutboxRepository outboxRepository,
            @Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${vault.kafka.outbox-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        if (!outboxKafkaEnabled || kafkaTemplate == null) {
            return;
        }
        List<VaultEventOutbox> pending = outboxRepository.findTop50ByStatusOrderByCreatedAtAsc("PENDING");
        for (VaultEventOutbox row : pending) {
            try {
                String envelopeJson = "{\"id\":\"" + row.getId()
                        + "\",\"eventType\":\"" + row.getEventType()
                        + "\",\"payload\":" + (row.getPayload() != null ? row.getPayload() : "{}") + "}";
                kafkaTemplate.send(KAFKA_TOPIC, row.getEventType(), envelopeJson).get(2, TimeUnit.SECONDS);
                row.setStatus("PUBLISHED");
                outboxRepository.save(row);
            } catch (Exception e) {
                log.debug("Kafka outbox publish deferred for {}: {}", row.getId(), e.getMessage());
                break;
            }
        }
    }
}
