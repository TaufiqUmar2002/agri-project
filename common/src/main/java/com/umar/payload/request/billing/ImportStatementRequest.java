package com.umar.payload.request.billing;

import com.umar.enums.billing.StatementFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ImportStatementRequest {
    @NotBlank
    private String source;
    @NotNull
    private LocalDate statementDate;
    @NotNull
    private StatementFormat format;
    private String fileToken;
    private String checksum;
    private Boolean autoApply = Boolean.TRUE;
}
