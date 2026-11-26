package com.umar.payload.request.auth;

import com.umar.enums.auth.ConsentEvidenceType;
import com.umar.enums.auth.ConsentPurpose;
import com.umar.enums.auth.RegistrationChannel;
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
public class RecordConsentRequest {

    @Valid
    @NotEmpty(message = "consents must contain 1 to 4 items")
    @Size(min = 1, max = 4, message = "consents must contain 1 to 4 items")
    private List<ConsentItem> consents;

    @NotBlank(message = "language is required")
    @Pattern(regexp = "^[a-z]{2}$", message = "language must be a 2-letter ISO 639-1 code")
    private String language;

    @NotNull(message = "channel is required")
    private RegistrationChannel channel;

    @Valid
    @NotNull(message = "evidence is required")
    private ConsentEvidence evidence;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsentItem {
        @NotNull(message = "purpose is required")
        private ConsentPurpose purpose;

        @NotBlank(message = "textVersion is required")
        private String textVersion;

        private String partnerId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConsentEvidence {
        @NotNull(message = "evidence.type is required")
        private ConsentEvidenceType type;

        private String reference;
    }
}
