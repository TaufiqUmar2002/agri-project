package com.umar.repository.dataProtection;

import com.umar.entity.dataProtection.DataClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DataClassRepository extends JpaRepository<DataClass, UUID> {

    Optional<DataClass> findByNameAndStatus(String name, String status);

    Optional<DataClass> findFirstByNameOrderByVersionDesc(String name);

    List<DataClass> findByStatus(String status);
}
