package com.umar.payload.request.farmer;

import com.umar.enums.farmer.FarmSizeBand;
import com.umar.enums.farmer.PreferredChannel;
import jakarta.validation.Valid;
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
public class CreateFarmerProfileRequest {

    @NotBlank(message = "preferredLanguage is required")
    @Pattern(regexp = "^[a-z]{2}$", message = "preferredLanguage must be a 2-letter ISO 639-1 code")
    private String preferredLanguage;

    @NotNull(message = "preferredChannel is required")
    private PreferredChannel preferredChannel;

    private QuietHoursDto quietHours;

    @Size(min = 1, max = 60, message = "displayName must be 1 to 60 characters")
    private String displayName;

    @Valid
    @NotNull(message = "region is required")
    private RegionDto region;

    @NotNull(message = "farmSizeBand is required")
    private FarmSizeBand farmSizeBand;

    @NotEmpty(message = "primaryCrops must contain 1 to 5 crops")
    @Size(min = 1, max = 5, message = "primaryCrops must contain 1 to 5 crops")
    private List<String> primaryCrops;

    private String groupInvitationId;

    @Valid
    private RegisterWalletRequest wallet;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuietHoursDto {
        private String start;
        private String end;
        private String timezone;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegionDto {
        @NotBlank(message = "country is required")
        private String country;
        @NotBlank(message = "district is required")
        private String district;
        @NotBlank(message = "ward is required")
        private String ward;
        @Size(max = 80)
        private String village;
    }
}
