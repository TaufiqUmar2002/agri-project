package com.umar.payload.request.auth;

import com.umar.enums.auth.RegistrationChannel;
import com.umar.enums.auth.UserType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterAccountRequest {

    @NotBlank(message = "phoneNumber is required")
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "phoneNumber must be in E.164 format")
    private String phoneNumber;

    @NotNull(message = "userType is required")
    private UserType userType;

    @NotBlank(message = "fullName is required")
    @Size(min = 2, max = 120, message = "fullName must be between 2 and 120 characters")
    private String fullName;

    @NotBlank(message = "preferredLanguage is required")
    @Pattern(regexp = "^[a-z]{2}$", message = "preferredLanguage must be a 2-letter ISO 639-1 code")
    private String preferredLanguage;

    private RegistrationChannel channel;

    @Pattern(regexp = "^\\d{4,6}$", message = "pin must be 4 to 6 digits")
    private String pin;
}
