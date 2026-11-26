package com.umar.payload.request.integration;

import com.umar.enums.integration.DataScopeLevel;
import com.umar.enums.integration.DeliveryOrdering;
import com.umar.enums.integration.HubEnvironment;
import com.umar.enums.integration.WebhookFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class RegisterSubscriptionRequest {
    private String partnerId;
    @NotBlank @Size(min = 3, max = 80)
    private String name;
    @NotBlank @Size(max = 500)
    private String targetUrl;
    @NotEmpty @Size(min = 1, max = 20)
    private List<@NotBlank String> eventTypes;
    private SubscriptionFilters filters;
    private WebhookFormat format = WebhookFormat.JSON;
    private DataScopeLevel dataScope;
    private DeliveryOrdering ordering = DeliveryOrdering.NONE;
    @Min(1)
    private Integer rateLimitPerSecond;
    @Min(1) @Max(100)
    private Integer batchSize = 1;
    @NotNull
    private HubEnvironment environment;
    @NotNull @Valid
    private ContactSpec contact;

    @Data
    public static class SubscriptionFilters {
        private List<String> regionCodes;
        private List<String> productIds;
        private List<String> tenantIds;
        private List<String> cropCodes;
    }

    @Data
    public static class ContactSpec {
        @NotBlank @Email private String email;
        @NotBlank private String phone;
    }
}
