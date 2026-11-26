package com.umar.iot.repository;

import com.umar.iot.domain.IotOutboxEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface IotOutboxEventRepository extends JpaRepository<IotOutboxEventEntity, UUID> {
}
