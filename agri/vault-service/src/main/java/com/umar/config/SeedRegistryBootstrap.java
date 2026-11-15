package com.umar.config;



import com.umar.converter.MaskingRule;
import com.umar.converter.ValidationRule;
import com.umar.entity.dataProtection.DataClass;
import com.umar.enums.vault.DetokenizeMode;
import com.umar.enums.vault.RETENTION_GROUP;
import com.umar.enums.vault.ValueKind;
import com.umar.repository.dataProtection.DataClassRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.xml.crypto.Data;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class SeedRegistryBootstrap implements CommandLineRunner {

    private final DataClassRepository dataClassRepository;

    public SeedRegistryBootstrap(DataClassRepository dataClassRepository) {
        this.dataClassRepository = dataClassRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("-----------------Starting Vault Cryptographic Seed Registry initialization-----------------");
        if (dataClassRepository.count() == 0) {
            dataClassRepository.save(
                    DataClass.builder()
                            .id(UUID.randomUUID())
                            .name("PHONE")
                            .valueKind(ValueKind.VALUE)
                            .retentionGroup(RETENTION_GROUP.ERASABLE)
                            .detokenizable(DetokenizeMode.YES)
                            .validation(new ValidationRule("REGEX", "^[0-9]{10}$",2))
                            .maskingRules(new MaskingRule("REPLACE", 4))
                            .hasLookupHash(Boolean.TRUE)
                            .allowedPurposes(List.of("ACCOUNT_REGISTRATION", "PHONE_LOOKUP", "GROUP_INVITATION", "DATA_EXPORT"))
                            .version(1)
                            .status("ACTIVE")
                            .build());
            dataClassRepository.save(
                    DataClass.builder()
                            .id(UUID.randomUUID())
                            .name("ID_NUMBER")
                            .valueKind(ValueKind.VALUE)
                            .retentionGroup(RETENTION_GROUP.KYC_RETAIN)
                            .detokenizable(DetokenizeMode.MASKED_ONLY)
                            .validation(new ValidationRule("REGEX", "^[0-9]{10}$",2))
                            .hasLookupHash(Boolean.TRUE)
                            .maskingRules(new MaskingRule("REPLACE", 4))
                            .allowedPurposes(List.of("KYC_SUBMISSION", "KYC_REVIEW", "DATA_EXPORT"))
                            .status("ACTIVE")
                            .version(1).build());
            dataClassRepository.save(
                    DataClass.builder()
                            .id(UUID.randomUUID())
                            .name("KYC_DOCUMENT")
                            .valueKind(ValueKind.DOCUMENT)
                            .retentionGroup(RETENTION_GROUP.KYC_RETAIN)
                            .maskingRules(new MaskingRule("REPLACE", 4))
                            .detokenizable( DetokenizeMode.VIEW_REVIEWERS_ONLY)
                            .hasLookupHash(Boolean.FALSE)
                            .validation(new ValidationRule("REGEX", "^[0-9]{10}$",2))
                            .allowedPurposes(List.of("KYC_SUBMISSION", "KYC_REVIEW"))
                            .status("ACTIVE")
                            .version(1)
                            .build());
            log.info("Vault Cryptographic Seed Registry successfully initialized.");
        }
    }
}
