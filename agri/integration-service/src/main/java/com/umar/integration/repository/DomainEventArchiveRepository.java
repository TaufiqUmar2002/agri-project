package com.umar.integration.repository;

import com.umar.integration.domain.entity.DomainEventArchiveEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface DomainEventArchiveRepository extends JpaRepository<DomainEventArchiveEntity, String> {
    List<DomainEventArchiveEntity> findByOccurredAtBetweenOrderByOccurredAtAsc(Instant from, Instant to);
    List<DomainEventArchiveEntity> findByKafkaPartitionAndKafkaOffsetBetweenOrderByKafkaOffsetAsc(
            Integer kafkaPartition, Long fromOffset, Long toOffset);
}
