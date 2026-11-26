package com.umar.iot.repository;

import com.umar.iot.domain.IotIdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IotIdempotencyRepository extends JpaRepository<IotIdempotencyEntity, String> {
}
