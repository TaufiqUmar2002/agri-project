package com.umar.payload.request.farmer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
public class InviteGroupMembersRequest {

    @Valid
    @NotEmpty(message = "members must contain 1 to 200 items")
    @Size(min = 1, max = 200, message = "members must contain 1 to 200 items")
    private List<InviteRow> members;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InviteRow {
        @NotBlank(message = "phoneNumber is required")
        private String phoneNumber;

        @NotBlank(message = "fullName is required")
        @Size(min = 2, max = 120)
        private String fullName;

        private String preferredLanguage;
    }
}
