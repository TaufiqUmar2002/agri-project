package com.umar.payload.request.iot;

import com.umar.enums.iot.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class RegisterDeviceRequest {
    @NotNull
    private DeviceType deviceType;
    @NotBlank
    private String manufacturer;
    @NotBlank
    private String model;
    @NotBlank
    private String serialNumber;
    @NotBlank
    private String protocol;
    @NotBlank
    private String plotId;
    @NotNull
    private Map<String, Object> location;
    private List<Integer> installDepthsCm;
    private Integer expectedIntervalSeconds;
    @NotBlank
    private String consentRef;
    private String installPhotoToken;
}
