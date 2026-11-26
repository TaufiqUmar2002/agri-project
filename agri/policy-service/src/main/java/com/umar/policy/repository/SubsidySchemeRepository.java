package com.umar.policy.repository;

import com.umar.policy.entity.SubsidySchemeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SubsidySchemeRepository extends JpaRepository<SubsidySchemeEntity, String> {
    List<SubsidySchemeEntity> findByActiveTrue();

    @Modifying
    @Query("update SubsidySchemeEntity s set s.reservedMinor = s.reservedMinor + :amount " +
           "where s.schemeCode = :code and s.active = true and (s.budgetMinor - s.reservedMinor) >= :amount")
    int reserveBudget(@Param("code") String code, @Param("amount") long amount);

    @Modifying
    @Query("update SubsidySchemeEntity s set s.reservedMinor = case when s.reservedMinor >= :amount then s.reservedMinor - :amount else 0 end " +
           "where s.schemeCode = :code")
    int releaseBudget(@Param("code") String code, @Param("amount") long amount);
}
