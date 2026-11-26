package com.umar.payload.request.payout;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ImportPayoutStatementRequest {
    @NotBlank
    private String connection;
    @NotNull
    private LocalDate statementDate;
    @NotBlank
    private String fileToken;
    @NotBlank
    private String checksum;
    private boolean replace;
}
