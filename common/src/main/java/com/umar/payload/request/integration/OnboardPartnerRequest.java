package com.umar.payload.request.integration;

import com.umar.enums.integration.DataScopeLevel;
import com.umar.enums.integration.PartnerType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class OnboardPartnerRequest {
    @NotBlank @Size(min = 3, max = 200)
    private String legalName;
    @NotBlank @Size(min = 3, max = 200)
    private String displayName;
    @NotNull
    private PartnerType type;
    @NotBlank @Size(min = 2, max = 100)
    private String registrationNumber;
    @NotEmpty
    private List<@NotBlank String> countryCodes;
    @NotNull @Valid
    private ContactsSpec contacts;
    @NotEmpty
    private List<@NotBlank String> allowedEventTypes;
    @NotNull
    private DataScopeLevel dataScope;
    private String dataScopeJustification;
    @NotNull
    private VisibilitySpec visibility;
    private ConnectorRightsSpec connectorRights;
    private List<String> extensionPoints;
    @NotNull
    private QuotasSpec quotas;
    private NetworkSpec network;
    @NotEmpty
    private List<@NotBlank String> agreementTokens;

    @Data
    public static class ContactsSpec {
        @NotNull private ContactPerson technical;
        @NotNull private ContactPerson security;
        @NotNull private ContactPerson business;
    }

    @Data
    public static class ContactPerson {
        @NotBlank @Email private String email;
        @NotBlank private String phone;
    }

    @Data
    public static class VisibilitySpec {
        private List<String> regionCodes;
        private List<String> productIds;
        private List<String> tenantIds;
    }

    @Data
    public static class ConnectorRightsSpec {
        private boolean inbound;
        private boolean outbound;
        private List<String> ingestionTopics;
    }

    @Data
    public static class QuotasSpec {
        private Long webhookDeliveriesPerMonth;
        private Integer webhookRatePerSecond;
        private Long inboundRecordsPerMonth;
        private Long extensionInvocationsPerMonth;
        private Integer subscriptions;
        private Integer connectors;
    }

    @Data
    public static class NetworkSpec {
        private List<String> ipAllowList;
        private boolean mtlsRequired;
    }
}
