package com.umar.config;

import com.umar.converter.MaskingRule;
import com.umar.converter.ValidationRule;
import com.umar.entity.dataProtection.DataClass;
import com.umar.entity.dataProtection.RetentionClass;
import com.umar.enums.vault.DetokenizeMode;
import com.umar.enums.vault.LegalBasis;
import com.umar.enums.vault.RETENTION_GROUP;
import com.umar.enums.vault.ValueKind;
import com.umar.repository.dataProtection.DataClassRepository;
import com.umar.repository.dataProtection.RetentionClassRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Seeds the Section 2.5 / 2.7 Data Class and Retention Class registry idempotently.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class SeedRegistryBootstrap implements CommandLineRunner {

    private final DataClassRepository dataClassRepository;
    private final RetentionClassRepository retentionClassRepository;

    @Override
    public void run(String... args) {
        log.info("Initializing Vault Cryptographic Seed Registry...");
        seedRetentionClasses();
        seedDataClasses();
        log.info("Vault Cryptographic Seed Registry initialization complete.");
    }

    private void seedRetentionClasses() {
        ensureRetention("ERASABLE", "ERASABLE", 0, 0, LegalBasis.CONSENT);
        ensureRetention("SHORT_90D", "EVIDENCE", 90, 90, LegalBasis.LEGITIMATE_INTEREST);
        ensureRetention("STANDARD_7Y", "KYC_RETAIN", 2555, 2555, LegalBasis.LEGAL_OBLIGATION);
        ensureRetention("KYC_RETAIN", "KYC_RETAIN", 2555, 2555, LegalBasis.LEGAL_OBLIGATION);
        ensureRetention("FINANCIAL_RETAIN", "FINANCIAL_RETAIN", 2555, 2555, LegalBasis.LEGAL_OBLIGATION);
        ensureRetention("EVIDENCE_RETAIN", "EVIDENCE_RETAIN", 2555, 2555, LegalBasis.LEGAL_OBLIGATION);
        ensureRetention("LEGAL_10Y", "LEGAL_RETAIN", 3650, 2555, LegalBasis.LEGAL_OBLIGATION);
    }

    private void ensureRetention(String name, String group, int periodDays, int minimumDays, LegalBasis basis) {
        if (retentionClassRepository.findByName(name).isEmpty()) {
            retentionClassRepository.save(RetentionClass.builder()
                    .id(UUID.randomUUID())
                    .name(name)
                    .group(group)
                    .periodDays(periodDays)
                    .minimumDays(minimumDays)
                    .legalBasis(basis)
                    .status("ACTIVE")
                    .build());
        }
    }

    private void seedDataClasses() {
        // Value classes (Section 2.5)
        ensureClass("PHONE", ValueKind.VALUE, RETENTION_GROUP.ERASABLE, DetokenizeMode.YES, true, true,
                new ValidationRule("REGEX", "^\\+[1-9]\\d{1,14}$", 16), new MaskingRule("SHOW_LAST", 3),
                "CONFIDENTIAL", "ERASABLE",
                List.of("ACCOUNT_REGISTRATION", "PHONE_LOOKUP", "GROUP_INVITATION", "DATA_EXPORT", "GENERAL"));

        ensureClass("ID_NUMBER", ValueKind.VALUE, RETENTION_GROUP.KYC_RETAIN, DetokenizeMode.MASKED_ONLY, true, false,
                new ValidationRule("REGEX", "^[A-Za-z0-9\\-]{5,30}$", 30), new MaskingRule("SHOW_LAST", 3),
                "RESTRICTED", "STANDARD_7Y",
                List.of("KYC_SUBMISSION", "KYC_REVIEW", "DATA_EXPORT", "GENERAL"));

        ensureClass("LEGAL_NAME", ValueKind.VALUE, RETENTION_GROUP.KYC_RETAIN, DetokenizeMode.NAME_MATCH_ONLY, false, false,
                new ValidationRule("TEXT", null, 200), new MaskingRule("MASK_ALL", 0),
                "CONFIDENTIAL", "STANDARD_7Y",
                List.of("WALLET_NAME_MATCH", "DATA_EXPORT", "GENERAL"));

        ensureClass("WALLET_NUMBER", ValueKind.VALUE, RETENTION_GROUP.FINANCIAL_RETAIN, DetokenizeMode.YES, true, true,
                new ValidationRule("REGEX", "^(\\+)?\\d{7,15}$", 20), new MaskingRule("SHOW_LAST", 3),
                "RESTRICTED", "FINANCIAL_RETAIN",
                List.of("WALLET_REGISTRATION", "WALLET_LOOKUP", "PAYOUT_DISBURSEMENT", "DATA_EXPORT", "GENERAL"));

        ensureClass("PAYER_PHONE", ValueKind.VALUE, RETENTION_GROUP.FINANCIAL_RETAIN, DetokenizeMode.YES, true, false,
                new ValidationRule("REGEX", "^\\+[1-9]\\d{1,14}$", 16), new MaskingRule("SHOW_LAST", 3),
                "CONFIDENTIAL", "FINANCIAL_RETAIN",
                List.of("PREMIUM_COLLECTION", "PREMIUM_REFUND", "GENERAL"));

        ensureClass("SPONSOR_BANK_ACCOUNT", ValueKind.VALUE, RETENTION_GROUP.FINANCIAL_RETAIN, DetokenizeMode.YES, false, false,
                new ValidationRule("REGEX", "^[A-Za-z0-9\\-\\s]{6,34}$", 34), new MaskingRule("SHOW_LAST", 4),
                "RESTRICTED", "FINANCIAL_RETAIN",
                List.of("PREMIUM_REFUND", "GENERAL"));

        // Document classes (Section 2.5)
        ensureDocClass("KYC_DOCUMENT", RETENTION_GROUP.KYC_RETAIN, DetokenizeMode.VIEW_REVIEWERS_ONLY, "STANDARD_7Y",
                List.of("KYC_SUBMISSION", "KYC_REVIEW", "GENERAL"));
        ensureDocClass("SELFIE", RETENTION_GROUP.KYC_RETAIN, DetokenizeMode.VIEW_REVIEWERS_ONLY, "STANDARD_7Y",
                List.of("KYC_SUBMISSION", "KYC_REVIEW", "GENERAL"));
        ensureDocClass("DISPUTE_EVIDENCE", RETENTION_GROUP.EVIDENCE_RETAIN, DetokenizeMode.VIEW_REVIEWERS_ONLY, "STANDARD_7Y",
                List.of("CLAIM_DISPUTE", "GENERAL"));
        ensureDocClass("GPS_TRACK", RETENTION_GROUP.EVIDENCE, DetokenizeMode.VIEW_REVIEWERS_ONLY, "SHORT_90D",
                List.of("PLOT_EVIDENCE", "PLOT_REVIEW", "GENERAL"));
        ensureDocClass("LAND_EVIDENCE", RETENTION_GROUP.EVIDENCE_RETAIN, DetokenizeMode.VIEW_REVIEWERS_ONLY, "STANDARD_7Y",
                List.of("PLOT_EVIDENCE", "PLOT_REVIEW", "GENERAL"));
        ensureDocClass("POLICY_SUMMARY", RETENTION_GROUP.FINANCIAL_RETAIN, DetokenizeMode.YES, "LEGAL_10Y",
                List.of("POLICY_RECORD", "GENERAL"));
        ensureDocClass("ACCEPTANCE_EVIDENCE", RETENTION_GROUP.FINANCIAL_RETAIN, DetokenizeMode.YES, "LEGAL_10Y",
                List.of("POLICY_RECORD", "GENERAL"));
        ensureDocClass("STATEMENT_FILE", RETENTION_GROUP.FINANCIAL_RETAIN, DetokenizeMode.YES, "FINANCIAL_RETAIN",
                List.of("RECONCILIATION", "PAYMENT_RECORD", "CLAIM_RECONCILIATION", "GENERAL"));
        ensureDocClass("PAYMENT_EVIDENCE", RETENTION_GROUP.FINANCIAL_RETAIN, DetokenizeMode.YES, "FINANCIAL_RETAIN",
                List.of("RECONCILIATION", "PAYMENT_RECORD", "CLAIM_RECONCILIATION", "GENERAL"));
        ensureDocClass("RECONCILIATION_EXPORT", RETENTION_GROUP.FINANCIAL_RETAIN, DetokenizeMode.YES, "FINANCIAL_RETAIN",
                List.of("RECONCILIATION", "PAYMENT_RECORD", "CLAIM_RECONCILIATION", "GENERAL"));
        ensureDocClass("BORDEREAU_FILE", RETENTION_GROUP.FINANCIAL_RETAIN, DetokenizeMode.YES, "FINANCIAL_RETAIN",
                List.of("REINSURANCE_REPORTING", "LEDGER_REPORTING", "PERIOD_CLOSE", "REINSURANCE_TREATY", "LEDGER_POSTING", "POOL_SETUP", "GENERAL"));
        ensureDocClass("LEDGER_EXPORT", RETENTION_GROUP.FINANCIAL_RETAIN, DetokenizeMode.YES, "FINANCIAL_RETAIN",
                List.of("LEDGER_REPORTING", "PERIOD_CLOSE", "LEDGER_POSTING", "GENERAL"));
        ensureDocClass("CLOSE_PACKAGE", RETENTION_GROUP.FINANCIAL_RETAIN, DetokenizeMode.YES, "FINANCIAL_RETAIN",
                List.of("PERIOD_CLOSE", "GENERAL"));
        ensureDocClass("TREATY_WORDING", RETENTION_GROUP.FINANCIAL_RETAIN, DetokenizeMode.YES, "LEGAL_10Y",
                List.of("REINSURANCE_TREATY", "GENERAL"));
        ensureDocClass("JOURNAL_EVIDENCE", RETENTION_GROUP.FINANCIAL_RETAIN, DetokenizeMode.YES, "FINANCIAL_RETAIN",
                List.of("LEDGER_POSTING", "GENERAL"));
        ensureDocClass("POOL_EVIDENCE", RETENTION_GROUP.FINANCIAL_RETAIN, DetokenizeMode.YES, "FINANCIAL_RETAIN",
                List.of("POOL_SETUP", "GENERAL"));
        ensureDocClass("EVIDENCE_BUNDLE", RETENTION_GROUP.EVIDENCE_RETAIN, DetokenizeMode.VIEW_REVIEWERS_ONLY, "STANDARD_7Y",
                List.of("REGULATORY_EVIDENCE", "RULE_VALIDATION", "GENERAL"));
        ensureDocClass("SIMULATION_REPORT", RETENTION_GROUP.EVIDENCE_RETAIN, DetokenizeMode.YES, "STANDARD_7Y",
                List.of("REGULATORY_EVIDENCE", "RULE_VALIDATION", "GENERAL"));
        ensureDocClass("PARTNER_AGREEMENT", RETENTION_GROUP.EVIDENCE_RETAIN, DetokenizeMode.YES, "STANDARD_7Y",
                List.of("PARTNER_ONBOARDING", "EXTENSION_REGISTRY", "PARTNER_REPORTING", "GENERAL"));
        ensureDocClass("EXTENSION_ARTIFACT", RETENTION_GROUP.EVIDENCE_RETAIN, DetokenizeMode.YES, "STANDARD_7Y",
                List.of("EXTENSION_REGISTRY", "GENERAL"));
        ensureDocClass("USAGE_EXPORT", RETENTION_GROUP.EVIDENCE_RETAIN, DetokenizeMode.YES, "STANDARD_7Y",
                List.of("PARTNER_REPORTING", "GENERAL"));
    }

    private void ensureDocClass(String name, RETENTION_GROUP group, DetokenizeMode detokenizeMode,
                                String defaultRetentionClass, List<String> purposes) {
        ensureClass(name, ValueKind.DOCUMENT, group, detokenizeMode, false, false,
                new ValidationRule("DOCUMENT", null, 50_000_000), new MaskingRule("WATERMARK", 0),
                "RESTRICTED", defaultRetentionClass, purposes);
    }

    private void ensureClass(String name, ValueKind kind, RETENTION_GROUP group, DetokenizeMode detokenizeMode,
                             boolean hasLookupHash, boolean fpeAllowed, ValidationRule validation,
                             MaskingRule masking, String classification, String defaultRetentionClass,
                             List<String> purposes) {
        if (dataClassRepository.findByNameAndStatus(name, "ACTIVE").isPresent()) {
            return;
        }
        dataClassRepository.save(DataClass.builder()
                .id(UUID.randomUUID())
                .name(name)
                .valueKind(kind)
                .retentionGroup(group)
                .detokenizable(detokenizeMode)
                .validation(validation)
                .maskingRules(masking)
                .classification(classification)
                .hasLookupHash(hasLookupHash)
                .blindIndex(Boolean.toString(hasLookupHash))
                .fpe(Boolean.toString(fpeAllowed))
                .retentionClassDefault(defaultRetentionClass)
                .maxValueSize(kind == ValueKind.DOCUMENT ? 52_428_800 : 512)
                .allowedPurposes(purposes)
                .version(1)
                .status("ACTIVE")
                .build());
    }
}
