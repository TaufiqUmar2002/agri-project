package com.umar.payload.request.policy;

import com.umar.enums.policy.IndexSource;
import com.umar.enums.policy.PerilType;
import com.umar.enums.policy.RefundBasis;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class CreateProductRequest {
    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9_]{3,40}$")
    private String productCode;

    private String baseProductId;

    @NotEmpty
    private Map<String, String> name;

    @NotEmpty
    private Map<String, String> description;

    @NotNull
    private PerilType peril;

    @NotNull
    @Valid
    private IndexDefinition index;

    @NotNull
    @Valid
    private TriggerDefinition trigger;

    @NotNull
    @Valid
    private CoverageWindowDefinition coverageWindow;

    @NotNull
    @Min(0)
    @Max(21)
    private Integer waitingPeriodDays;

    @NotNull
    @Valid
    private EnrollmentDefinition enrollment;

    @NotNull
    @Valid
    private EligibilityDefinition eligibility;

    @NotNull
    @Valid
    private SumInsuredDefinition sumInsured;

    @NotNull
    @Valid
    private PricingDefinition pricing;

    @NotNull
    @Valid
    private PaymentDefinition payment;

    @NotNull
    @Valid
    private CancellationDefinition cancellation;

    @NotNull
    @Valid
    private RenewalDefinition renewal;

    private Map<String, Object> attributes;
    private String backtestRef;

    @NotNull
    private LocalDate effectiveFrom;

    private Boolean submitForApproval = Boolean.FALSE;

    @Data
    public static class IndexDefinition {
        @NotBlank
        private String type;
        @NotNull
        private IndexSource source;
        @NotBlank
        private String aggregation;
        @NotNull
        @Min(5)
        @Max(30)
        private Integer baselineYears;
        @NotEmpty
        @Valid
        private List<PhaseDefinition> phases;
    }

    @Data
    public static class PhaseDefinition {
        @NotBlank
        private String name;
        @NotNull
        private Integer startOffsetDays;
        @NotNull
        private Integer endOffsetDays;
        @NotNull
        private Double weight;
    }

    @Data
    public static class TriggerDefinition {
        @NotEmpty
        @Size(min = 1, max = 5)
        @Valid
        private List<TierDefinition> tiers;
        @NotNull
        @Valid
        private CorroborationDefinition corroboration;
    }

    @Data
    public static class TierDefinition {
        @NotBlank
        private String operator; // LT, LTE, GT, GTE
        @NotNull
        private Double thresholdValue;
        @NotNull
        @Min(1)
        @Max(100)
        private Integer payoutPercent;
    }

    @Data
    public static class CorroborationDefinition {
        @NotNull
        @Min(1)
        @Max(2)
        private Integer requiredSources;
        private List<String> allowedSources;
    }

    @Data
    public static class CoverageWindowDefinition {
        private String anchor = "PLANTING_DATE";
        @NotNull
        private Integer startOffsetDays;
        @NotNull
        private Integer endOffsetDays;
        @NotNull
        @Min(30)
        private Integer minDays;
        @NotNull
        private Integer maxDays;
    }

    @Data
    public static class EnrollmentDefinition {
        @NotNull
        private LocalDate opensOn;
        @NotNull
        private LocalDate closesOn;
        @NotEmpty
        private List<String> regionCodes;
    }

    @Data
    public static class EligibilityDefinition {
        @NotEmpty
        private List<String> cropCodes;
        @NotEmpty
        private List<String> regionCodes;
        private List<String> irrigationTypes;
        private Double minAreaHa = 0.01;
        private Double maxAreaHa = 500.0;
        private String minAccuracyClass = "LOW";
        private String minKycTier = "TIER_1";
        private List<String> requiredConsents = List.of("DATA_COLLECTION", "SATELLITE_MONITORING", "AUTOMATED_PAYOUT");
    }

    @Data
    public static class SumInsuredDefinition {
        @NotBlank
        private String mode; // PER_HECTARE | FIXED
        @NotNull
        @Positive
        private Long minMinor;
        @NotNull
        @Positive
        private Long maxMinor;
        @NotNull
        @Positive
        private Long stepMinor;
        @NotBlank
        private String currency;
    }

    @Data
    public static class PricingDefinition {
        @NotBlank
        private String rateCardRef;
        private Double expenseLoadingPercent = 15.0;
        private Double marginPercent = 5.0;
        private Double reinsuranceLoadingPercent = 5.0;
        private Double riskLoadingCapPercent = 30.0;
        private Double customLoadingCapPercent = 5.0;
        private Long minPremiumMinor = 10000L;
        private List<Map<String, Object>> levies = List.of();
    }

    @Data
    public static class PaymentDefinition {
        @NotNull
        @Min(1)
        @Max(14)
        private Integer payByDays;
        private Boolean instalmentsAllowed = Boolean.TRUE;
    }

    @Data
    public static class CancellationDefinition {
        private Integer coolingOffDays = 14;
        private Integer refundPercentInCoolingOff = 100;
        private Long adminFeeMinor = 5000L;
        private RefundBasis refundAfterCoverStart = RefundBasis.NONE;
    }

    @Data
    public static class RenewalDefinition {
        private List<Integer> reminderDaysBefore = List.of(30, 14, 3);
        private Double priceChangeCapPercent = 15.0;
    }
}
