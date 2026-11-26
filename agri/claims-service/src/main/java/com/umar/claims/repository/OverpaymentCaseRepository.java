package com.umar.claims.repository;

import com.umar.claims.domain.OverpaymentCaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OverpaymentCaseRepository extends JpaRepository<OverpaymentCaseEntity, String> {
}
