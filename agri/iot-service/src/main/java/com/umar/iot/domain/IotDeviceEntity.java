package com.umar.iot.domain;

import com.umar.enums.iot.ConnectivityStatus;
import com.umar.enums.iot.DeviceStatus;
import com.umar.enums.iot.DeviceType;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity @Table(name = "iot_devices")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IotDeviceEntity {
    @Id @Column(name = "device_id") private String deviceId;
    @Column(name = "serial_number", nullable = false, unique = true) private String serialNumber;
    @Enumerated(EnumType.STRING) @Column(name = "device_type", nullable = false) private DeviceType deviceType;
    @Column(nullable = false) private String manufacturer;
    @Column(nullable = false) private String model;
    @Column(nullable = false) private String protocol;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private DeviceStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ConnectivityStatus connectivity;
    @Column(name = "plot_id") private String plotId;
    @Column(name = "farmer_id") private String farmerId;
    @Column(name = "district_code") private String districtCode;
    private Double latitude;
    private Double longitude;
    @Column(name = "accuracy_meters") private Double accuracyMeters;
    @Column(name = "expected_interval_seconds", nullable = false) private int expectedIntervalSeconds;
    @Column(name = "installer_id", nullable = false) private String installerId;
    @Column(name = "consent_ref", nullable = false) private String consentRef;
    @Column(name = "install_photo_token") private String installPhotoToken;
    @Column(name = "calibration_version", nullable = false) private int calibrationVersion;
    @Column(name = "calibration_json", nullable = false, columnDefinition = "TEXT") private String calibrationJson;
    @Column(name = "clock_offset_seconds", nullable = false) private int clockOffsetSeconds;
    @Column(name = "last_seen_at") private Instant lastSeenAt;
    @Column(name = "last_reading_at") private Instant lastReadingAt;
    @Column(name = "battery_percent") private Integer batteryPercent;
    private Integer rssi;
    @Column(name = "alerts_csv", nullable = false, columnDefinition = "TEXT") private String alertsCsv;
    @Column(name = "credential_state", nullable = false) private String credentialState;
    @Column(name = "cert_serial_masked") private String certSerialMasked;
    @Column(name = "cert_expires_at") private Instant certExpiresAt;
    @Column(name = "enrolment_token_hash") private String enrolmentTokenHash;
    @Column(name = "enrolment_expires_at") private Instant enrolmentExpiresAt;
    @Column(name = "pending_proposal_hash") private String pendingProposalHash;
    @Column(name = "pending_proposal_actor") private String pendingProposalActor;
    @Column(name = "pending_proposal_json", columnDefinition = "TEXT") private String pendingProposalJson;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "version_num", nullable = false) private int versionNum;
}
