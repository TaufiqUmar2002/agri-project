package com.umar.payload.request.auth;

import com.umar.enums.auth.IdType;
import com.umar.enums.auth.KycTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitKycRequest {

    @NotNull(message = "idType is required")
    private IdType idType;

    @NotBlank(message = "idNumber is required")
    @Size(min = 5, max = 30, message = "idNumber must be 5 to 30 characters")
    private String idNumber;

    @NotBlank(message = "fullNameOnId is required")
    @Size(min = 2, max = 120, message = "fullNameOnId must be 2 to 120 characters")
    private String fullNameOnId;

    @NotNull(message = "dateOfBirth is required")
    private LocalDate dateOfBirth;

    private KycTier targetTier;
}
