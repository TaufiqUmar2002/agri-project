package com.umar.service;

import com.umar.entity.dataProtection.DataClass;
import com.umar.repository.dataProtection.DataClassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DataClassService{

    private final DataClassRepository repository;

    public DataClass getActiveDataClass(String className){
        return repository.findByNameAndStatus(className, "ACTIVE")
                .orElseThrow(() -> new RuntimeException("Data class not found: " + className));
    }



}
