package com.umar.iot.repository;

import com.umar.iot.domain.IotRejectedReadingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface IotRejectedReadingRepository extends JpaRepository<IotRejectedReadingEntity, String> {
    List<IotRejectedReadingEntity> findByDeviceIdAndReceivedAtBetweenOrderByReceivedAtDesc(String deviceId, Instant from, Instant to);
}
