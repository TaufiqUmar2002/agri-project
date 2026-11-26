package com.umar.iot.repository;

import com.umar.iot.domain.IotReadingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface IotReadingRepository extends JpaRepository<IotReadingEntity, String> {
    List<IotReadingEntity> findByDeviceIdAndObservedAtBetweenOrderByObservedAtAsc(String deviceId, Instant from, Instant to);
}
