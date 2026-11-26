package com.umar.security;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Maps vault operations to the {@link VaultRole}s permitted to perform them,
 * derived from the section 2.11 role table.
 */
public final class VaultOperationRoles {

    private VaultOperationRoles() {
    }

    private static final Map<String, Set<VaultRole>> OPERATION_ROLES = Map.ofEntries(
            // Policy management (VLT-12)
            Map.entry("CREATE_POLICY", roles(VaultRole.SECURITY_OFFICER, VaultRole.COMPLIANCE_OFFICER)),
            Map.entry("LIST_POLICIES", roles(VaultRole.SECURITY_OFFICER, VaultRole.COMPLIANCE_OFFICER, VaultRole.AUDITOR, VaultRole.SERVICE)),
            // Break-glass (VLT-06)
            Map.entry("BREAK_GLASS", roles(VaultRole.SECURITY_OFFICER, VaultRole.COMPLIANCE_OFFICER)),
            Map.entry("APPROVE_BREAK_GLASS", roles(VaultRole.SECURITY_OFFICER, VaultRole.COMPLIANCE_OFFICER)),
            Map.entry("USE_BREAK_GLASS", roles(VaultRole.SECURITY_OFFICER, VaultRole.COMPLIANCE_OFFICER)),
            // Key management (VLT-04)
            Map.entry("ROTATE_KEY", roles(VaultRole.SECURITY_OFFICER, VaultRole.KEY_CUSTODIAN, VaultRole.SERVICE)),
            Map.entry("APPROVE_KEY_ACTION", roles(VaultRole.KEY_CUSTODIAN)),
            // Access log (VLT-09)
            Map.entry("ACCESS_LOG", roles(VaultRole.SECURITY_OFFICER, VaultRole.COMPLIANCE_OFFICER, VaultRole.AUDITOR, VaultRole.ADMINISTRATOR)),
            // Audit chain verification
            Map.entry("VERIFY_AUDIT", roles(VaultRole.AUDITOR, VaultRole.SECURITY_OFFICER, VaultRole.COMPLIANCE_OFFICER)),
            // Backup / drills (VLT-10)
            Map.entry("VERIFY_BACKUP", roles(VaultRole.PLATFORM_RELIABILITY_ENGINEER, VaultRole.SECURITY_OFFICER, VaultRole.SERVICE)),
            // Legal hold (VLT-08)
            Map.entry("CREATE_LEGAL_HOLD", roles(VaultRole.COMPLIANCE_OFFICER)),
            Map.entry("LIFT_LEGAL_HOLD", roles(VaultRole.COMPLIANCE_OFFICER)),
            // Erasure (VLT-07) - Auth & KYC data-rights orchestrator (SERVICE) or Compliance Officer
            Map.entry("ERASE_SUBJECT", roles(VaultRole.COMPLIANCE_OFFICER, VaultRole.SERVICE)),
            Map.entry("APPROVE_ERASURE_EXCEPTION", roles(VaultRole.COMPLIANCE_OFFICER)),
            Map.entry("SUBJECT_INVENTORY", roles(VaultRole.COMPLIANCE_OFFICER, VaultRole.SECURITY_OFFICER, VaultRole.SERVICE)),
            // Approvals (VLT-13)
            Map.entry("REQUEST_APPROVAL", roles(VaultRole.SECURITY_OFFICER, VaultRole.COMPLIANCE_OFFICER, VaultRole.KEY_CUSTODIAN)),
            Map.entry("APPROVAL_DECISION", roles(VaultRole.SECURITY_OFFICER, VaultRole.COMPLIANCE_OFFICER, VaultRole.KEY_CUSTODIAN)),
            Map.entry("LIST_APPROVALS", roles(VaultRole.SECURITY_OFFICER, VaultRole.COMPLIANCE_OFFICER, VaultRole.KEY_CUSTODIAN, VaultRole.AUDITOR)),
            // Data class definition (VLT-12)
            Map.entry("DEFINE_DATA_CLASS", roles(VaultRole.SECURITY_OFFICER, VaultRole.ADMINISTRATOR, VaultRole.SERVICE))
    );

    public static Set<VaultRole> allowedRoles(String operation) {
        return OPERATION_ROLES.getOrDefault(operation, EnumSet.noneOf(VaultRole.class));
    }

    public static boolean isRoleGated(String operation) {
        return OPERATION_ROLES.containsKey(operation);
    }

    private static Set<VaultRole> roles(VaultRole... roles) {
        return EnumSet.copyOf(java.util.Arrays.asList(roles));
    }
}
