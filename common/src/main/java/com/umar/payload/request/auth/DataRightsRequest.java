package com.umar.payload.request.auth;

import com.umar.enums.auth.DataCategory;
import com.umar.enums.auth.DataRequestType;
import jakarta.validation.constraints.NotNull;
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
public class DataRightsRequest {

    @NotNull(message = "type is required")
    private DataRequestType type;

    private List<DataCategory> categories;

    private String deliveryChannel; // SMS | WHATSAPP

    private Boolean confirmErasure;

    @Size(max = 500, message = "reason cannot exceed 500 characters")
    private String reason;
}
