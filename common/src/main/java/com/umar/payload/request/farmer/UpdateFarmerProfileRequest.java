package com.umar.payload.request.farmer;

import com.umar.enums.farmer.FarmSizeBand;
import com.umar.enums.farmer.PreferredChannel;
import jakarta.validation.Valid;
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
public class UpdateFarmerProfileRequest {

    @Pattern(regexp = "^[a-z]{2}$", message = "preferredLanguage must be a 2-letter ISO 639-1 code")
    private String preferredLanguage;

    private PreferredChannel preferredChannel;

    private CreateFarmerProfileRequest.QuietHoursDto quietHours;

    @Size(min = 1, max = 60, message = "displayName must be 1 to 60 characters")
    private String displayName;

    @Valid
    private CreateFarmerProfileRequest.RegionDto region;

    private FarmSizeBand farmSizeBand;

    @Size(min = 1, max = 5, message = "primaryCrops must contain 1 to 5 crops")
    private List<String> primaryCrops;

    private GroupInvitationResponse groupInvitationResponse;

    // Explicitly rejected if caller attempts to pass non-editable fields
    private String legalName;
    private String phone;
    private String phoneNumber;
    private Object wallet;
    private String status;
    private String kycTier;
    private String userId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupInvitationResponse {
        private String invitationId;
        private String response; // CONFIRM | DECLINE
    }
}
