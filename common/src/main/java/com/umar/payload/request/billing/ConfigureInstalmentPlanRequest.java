package com.umar.payload.request.billing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class ConfigureInstalmentPlanRequest {
    @NotEmpty
    private Map<String, String> name;

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9_]{3,40}$")
    private String planCode;

    private String basePlanId;

    @NotEmpty
    private Map<String, Object> scope;

    @NotEmpty
    @Size(min = 2, max = 4)
    @Valid
    private List<InstalmentSpec> instalments;

    @NotNull
    private Double depositPercent;

    @NotNull
    private Double activationThresholdPercent;

    @NotNull
    @Positive
    private Long minInstalmentMinor;

    @NotEmpty
    private Map<String, Object> grace;

    @NotEmpty
    private Map<String, Object> lapseRule;

    @NotEmpty
    private Map<String, Object> reinstatement;

    @NotEmpty
    private Map<String, Object> reminders;

    @NotNull
    private LocalDate effectiveFrom;

    @Data
    public static class InstalmentSpec {
        @NotNull
        private Integer seq;
        @NotNull
        private Double percent;
        @NotNull
        private Integer dueOffsetDays;
    }
}
