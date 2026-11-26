package com.umar.config;

import com.umar.entity.dataProtection.AccessPolicy;
import com.umar.repository.dataProtection.AccessPolicyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Seeds development and platform access policies idempotently.
 * Default deny still applies to any unlisted (caller, class, purpose, operation).
 */
@Slf4j
@Component
@Order(2)
public class AccessPolicySeedBootstrap implements CommandLineRunner {

    private final AccessPolicyRepository accessPolicyRepository;

    public AccessPolicySeedBootstrap(AccessPolicyRepository accessPolicyRepository) {
        this.accessPolicyRepository = accessPolicyRepository;
    }

    @Override
    public void run(String... args) {
        log.info("Ensuring access policies exist...");

        String devCaller = "TEMP-SERVICE";
        List<String> dataClasses = List.of(
                "PHONE", "PAYER_PHONE", "ID_NUMBER", "WALLET_NUMBER", "LEGAL_NAME", "SPONSOR_BANK_ACCOUNT",
                "KYC_DOCUMENT", "SELFIE", "DISPUTE_EVIDENCE", "GPS_TRACK", "LAND_EVIDENCE",
                "POLICY_SUMMARY", "ACCEPTANCE_EVIDENCE", "STATEMENT_FILE", "BORDEREAU_FILE",
                "DOCUMENT", "SECRET", "DATA_CLASS", "GOVERNANCE");
        List<String> purposes = List.of(
                "ACCOUNT_REGISTRATION", "PHONE_LOOKUP", "GROUP_INVITATION",
                "KYC_SUBMISSION", "KYC_REVIEW", "WALLET_REGISTRATION", "WALLET_LOOKUP",
                "WALLET_NAME_MATCH", "PAYOUT_DISBURSEMENT", "PREMIUM_COLLECTION", "PREMIUM_REFUND",
                "CLAIM_DISPUTE", "PLOT_EVIDENCE", "PLOT_REVIEW", "POLICY_RECORD",
                "RECONCILIATION", "REINSURANCE_REPORTING", "WEBHOOK_SIGNING", "TOKEN_SIGNING",
                "CALLBACK_VERIFICATION", "PARTNER_HANDOVER", "DATA_EXPORT", "GENERAL");
        List<String> operations = List.of(
                "TOKENIZE", "DETOKENIZE", "HASH", "SIGN",
                "STORE_DOCUMENT", "DOWNLOAD_DOCUMENT", "LIST_DOCUMENTS", "GET_DOCUMENT_METADATA",
                "VIEW_DOCUMENT", "READ_DOCUMENT", "VERIFY_DOCUMENT", "METADATA_DOCUMENT", "DELETE_DOCUMENT",
                "CREATE_SECRET", "USE_SECRET", "SECRET_METADATA",
                "CREATE_POLICY", "LIST_POLICIES", "REQUEST_APPROVAL", "APPROVAL_DECISION", "LIST_APPROVALS",
                "CREATE_LEGAL_HOLD", "LIFT_LEGAL_HOLD",
                "ERASE_SUBJECT", "APPROVE_ERASURE_EXCEPTION", "SUBJECT_INVENTORY",
                "BREAK_GLASS", "USE_BREAK_GLASS", "ACCESS_LOG", "VERIFY_BACKUP", "ROTATE_KEY", "DEFINE_DATA_CLASS");

        int created = 0;
        int updated = 0;
        for (String dataClass : dataClasses) {
            int[] res = upsertPolicy(devCaller, dataClass, purposes, operations, "600", "100");
            created += res[0];
            updated += res[1];
        }

        // Seed standard platform mTLS service policies (section 2.5 / 4.x)
        upsertPolicy("AUTH-KYC-SERVICE", "PHONE",
                List.of("ACCOUNT_REGISTRATION", "PHONE_LOOKUP", "DATA_EXPORT"),
                List.of("TOKENIZE", "HASH", "DETOKENIZE"), "1000", "100");
        upsertPolicy("AUTH-KYC-SERVICE", "ID_NUMBER",
                List.of("KYC_SUBMISSION", "KYC_REVIEW", "DATA_EXPORT"),
                List.of("TOKENIZE", "HASH", "DETOKENIZE"), "500", "100");
        upsertPolicy("AUTH-KYC-SERVICE", "KYC_DOCUMENT",
                List.of("KYC_SUBMISSION", "KYC_REVIEW"),
                List.of("STORE_DOCUMENT", "VIEW_DOCUMENT", "VERIFY_DOCUMENT", "METADATA_DOCUMENT"), "200", "10");
        upsertPolicy("AUTH-KYC-SERVICE", "SELFIE",
                List.of("KYC_SUBMISSION", "KYC_REVIEW"),
                List.of("STORE_DOCUMENT", "VIEW_DOCUMENT", "VERIFY_DOCUMENT", "METADATA_DOCUMENT"), "200", "10");
        upsertPolicy("AUTH-KYC-SERVICE", "GOVERNANCE",
                List.of("GENERAL"),
                List.of("ERASE_SUBJECT", "SUBJECT_INVENTORY"), "100", "50");
        upsertPolicy("AUTH-KYC-SERVICE", "SECRET",
                List.of("GENERAL", "TOKEN_SIGNING"),
                List.of("CREATE_SECRET", "USE_SECRET", "SIGN", "SECRET_METADATA"), "2000", "10");
        upsertPolicy("FARMER-PROFILE", "WALLET_NUMBER",
                List.of("WALLET_REGISTRATION", "WALLET_LOOKUP", "DATA_EXPORT"),
                List.of("TOKENIZE", "HASH", "DETOKENIZE"), "500", "100");
        upsertPolicy("FARMER-PROFILE", "LEGAL_NAME",
                List.of("WALLET_NAME_MATCH", "DATA_EXPORT"),
                List.of("TOKENIZE", "DETOKENIZE"), "500", "100");
        upsertPolicy("PAYOUT-SERVICE", "WALLET_NUMBER",
                List.of("PAYOUT_DISBURSEMENT"),
                List.of("DETOKENIZE"), "3000", "50");
        upsertPolicy("BILLING-SERVICE", "PAYER_PHONE",
                List.of("PREMIUM_COLLECTION", "PREMIUM_REFUND"),
                List.of("TOKENIZE", "DETOKENIZE", "HASH"), "1800", "50");
        upsertPolicy("BILLING-SERVICE", "STATEMENT_FILE",
                List.of("RECONCILIATION", "PAYMENT_RECORD"),
                List.of("STORE_DOCUMENT", "READ_DOCUMENT", "VERIFY_DOCUMENT", "METADATA_DOCUMENT"), "200", "10");
        upsertPolicy("INTEGRATION-HUB", "SECRET",
                List.of("GENERAL", "WEBHOOK_SIGNING", "PARTNER_HANDOVER", "CALLBACK_VERIFICATION"),
                List.of("CREATE_SECRET", "USE_SECRET", "SIGN", "SECRET_METADATA"), "5000", "10");

        log.info("Access policy seed complete: {} dev created, {} dev updated.", created, updated);
    }

    private int[] upsertPolicy(String caller, String dataClass, List<String> purposes,
                               List<String> operations, String rpm, String maxBatch) {
        AccessPolicy policy = accessPolicyRepository
                .findByServiceIdentityAndDataClass(caller, dataClass)
                .orElseGet(() -> {
                    AccessPolicy p = new AccessPolicy();
                    p.setServiceIdentity(caller);
                    p.setDataClass(dataClass);
                    p.setAllowedPurposes(new ArrayList<>());
                    p.setOperation(new ArrayList<>());
                    p.setRates(rpm);
                    p.setBatch(maxBatch);
                    p.setConditions("{}");
                    p.setVersions("1");
                    p.setPolicyStatus("ACTIVE");
                    p.setStatus(true);
                    return p;
                });

        if (policy.getAllowedPurposes() == null) {
            policy.setAllowedPurposes(new ArrayList<>());
        }
        if (policy.getOperation() == null) {
            policy.setOperation(new ArrayList<>());
        }

        boolean changed = mergeMissing(policy.getAllowedPurposes(), purposes)
                | mergeMissing(policy.getOperation(), operations);

        if (policy.getId() == null) {
            accessPolicyRepository.save(policy);
            return new int[]{1, 0};
        } else if (changed) {
            accessPolicyRepository.save(policy);
            return new int[]{0, 1};
        }
        return new int[]{0, 0};
    }

    private boolean mergeMissing(List<String> target, List<String> required) {
        if (target == null) {
            return false;
        }
        boolean changed = false;
        for (String value : required) {
            if (!target.contains(value)) {
                target.add(value);
                changed = true;
            }
        }
        return changed;
    }
}
