package com.umar.events;

import com.umar.repository.events.VaultEventOutboxRepository;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * {@link VaultEventListener} that persists every event to the durable outbox so
 * consumers can reliably receive it (the write-once log is the source of truth).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VaultEventOutboxListener implements VaultEventListener {

    private final VaultEventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void onEvent(VaultEvent event) {
        try {
            VaultEventOutbox record = new VaultEventOutbox();
            record.setId(event.id());
            record.setEventType(event.type().topic());
            record.setPayload(objectMapper.writeValueAsString(event.payload()));
            record.setCorrelationId(event.correlationId());
            record.setStatus("PENDING");
            outboxRepository.save(record);
        } catch (Exception e) {
            log.error("Failed to persist vault event {} to outbox", event.type(), e);
        }
    }
}
