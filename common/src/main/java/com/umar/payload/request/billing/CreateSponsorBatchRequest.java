package com.umar.payload.request.billing;

import com.umar.enums.billing.BatchAllocationMode;
import com.umar.enums.billing.PaymentMethod;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class CreateSponsorBatchRequest {
    @NotEmpty
    @Size(min = 1, max = 2000)
    private List<String> policyIds;

    @NotNull
    private PaymentMethod paymentMethod;

    private BatchAllocationMode allocationMode = BatchAllocationMode.ALL_OR_NOTHING;
    private Instant dueDate;
    @Size(max = 60)
    private String externalBatchRef;
}
