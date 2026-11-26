package com.umar.payload.request.plot;

import com.umar.enums.plot.IrrigationType;
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
public class RecordSeasonRequest {

    @NotBlank(message = "cropCode is required")
    private String cropCode;

    @NotNull(message = "plantingDate is required")
    private LocalDate plantingDate;

    @NotNull(message = "expectedHarvestDate is required")
    private LocalDate expectedHarvestDate;

    private IrrigationType irrigationType;

    @Size(max = 60)
    private String seedVariety;
}
