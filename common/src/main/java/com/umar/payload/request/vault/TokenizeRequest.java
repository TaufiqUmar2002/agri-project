package com.umar.payload.request.vault;

import com.umar.enums.vault.TokenFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class TokenizeRequest {

    @NotBlank(message = "Purpose is required")
    private String purpose;

    @Size(max = 128)
    private String contextRef;

    @Valid
    @NotEmpty(message = "At least one item is required")
    @Size(max = 100, message = "Maximum 100 items allowed")
    private List<Items> items;


    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    @Data
    public static class Items{

        @NotBlank(message = "Data class is required")
        private String dataClass;
        @NotEmpty(message = "Value is required")
        @Size(max = 512, message = "Value cannot exceed 512 characters")
        private char[] value;
        @NotBlank(message = "Subject reference is required")
        @Size(max = 128)
        private String subjectRef;

        private TokenFormat format;
        private Boolean reuseExisting;
    }
}
