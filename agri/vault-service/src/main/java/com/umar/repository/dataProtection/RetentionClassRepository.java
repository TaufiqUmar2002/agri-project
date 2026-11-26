package com.umar.repository.dataProtection;

import com.umar.entity.dataProtection.RetentionClass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RetentionClassRepository extends JpaRepository<RetentionClass, UUID> {

    Optional<RetentionClass> findByName(String name);

    Optional<RetentionClass> findByGroup(String group);
}
