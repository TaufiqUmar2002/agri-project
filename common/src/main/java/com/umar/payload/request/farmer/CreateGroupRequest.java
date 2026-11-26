package com.umar.payload.request.farmer;

import com.umar.enums.farmer.GroupType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateGroupRequest {

    @NotBlank(message = "name is required")
    @Size(min = 3, max = 120, message = "name must be 3 to 120 characters")
    private String name;

    @NotNull(message = "groupType is required")
    private GroupType groupType;

    @Size(min = 4, max = 40, message = "registrationNumber must be 4 to 40 characters")
    private String registrationNumber;

    @Valid
    @NotNull(message = "region is required")
    private GroupRegion region;

    private String leaderUserId;
    private String sponsorOrgId;

    @Min(1)
    @Max(2000)
    private Integer maxMembers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupRegion {
        @NotBlank(message = "country is required")
        private String country;
        @NotBlank(message = "district is required")
        private String district;
    }
}
