package com.umar.payload.request.auth;

import com.umar.enums.auth.DelegationScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnrollOnBehalfRequest {

    @NotBlank(message = "farmerPhoneNumber is required")
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "farmerPhoneNumber must be in E.164 format")
    private String farmerPhoneNumber;

    @NotBlank(message = "farmerFullName is required")
    @Size(min = 2, max = 120, message = "farmerFullName must be 2 to 120 characters")
    private String farmerFullName;

    @NotBlank(message = "preferredLanguage is required")
    @Pattern(regexp = "^[a-z]{2}$", message = "preferredLanguage must be a 2-letter ISO 639-1 code")
    private String preferredLanguage;

    @NotEmpty(message = "scopes must not be empty")
    private List<DelegationScope> scopes;

    @Min(value = 1, message = "requestedValidityHours must be between 1 and 24")
    @Max(value = 24, message = "requestedValidityHours must be between 1 and 24")
    private Integer requestedValidityHours;

    private String cooperativeId;

    @Valid
    @NotNull(message = "enrollmentLocation is required")
    private EnrollmentLocation enrollmentLocation;

    @Size(max = 128, message = "deviceId cannot exceed 128 characters")
    private String deviceId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EnrollmentLocation {
        @NotNull(message = "lat is required")
        private Double lat;
        @NotNull(message = "lon is required")
        private Double lon;
        @NotNull(message = "accuracyMeters is required")
        @Max(value = 100, message = "accuracyMeters must be at most 100")
        private Double accuracyMeters;
    }
}
