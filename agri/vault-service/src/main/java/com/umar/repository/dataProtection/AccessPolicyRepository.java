package com.umar.repository.dataProtection;


import com.umar.entity.dataProtection.AccessPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccessPolicyRepository extends JpaRepository<AccessPolicy, Long> {

    @Query("""
        SELECT COUNT(p)
        FROM AccessPolicy p 
        JOIN p.allowedPurposes pur
        JOIN p.operation op
        WHERE p.serviceIdentity = :caller
          AND p.dataClass = :dataClass
          AND pur = :purpose
          AND op = :operation
          AND p.status = true
    """)
    long countMatchingPolicies(
            @Param("caller") String caller,
            @Param("dataClass") String dataClass,
            @Param("purpose") String purpose,
            @Param("operation") String operation
    );

    default boolean isAllowed(String caller, String dataClass, String purpose, String operation) {
        return countMatchingPolicies(caller, dataClass, purpose, operation) > 0;
    }
}
