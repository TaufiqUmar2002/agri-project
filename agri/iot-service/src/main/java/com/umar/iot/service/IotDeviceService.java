package com.umar.iot.service;

import com.umar.enums.auth.AuthRole;
import com.umar.enums.iot.*;
import com.umar.payload.request.iot.*;
import com.umar.payload.response.iot.*;
import com.umar.iot.client.IotExternalClients;
import com.umar.iot.config.IotProperties;
import com.umar.iot.domain.IotDeviceEntity;
import com.umar.iot.domain.IotReadingEntity;
import com.umar.iot.domain.IotRejectedReadingEntity;
import com.umar.iot.exception.IotApiException;
import com.umar.iot.repository.IotDeviceRepository;
import com.umar.iot.repository.IotReadingRepository;
import com.umar.iot.repository.IotRejectedReadingRepository;
import com.umar.iot.security.IotIdentityFilter;
import com.umar.iot.service.support.IotSupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class IotDeviceService {

    private final IotDeviceRepository deviceRepository;
    private final IotReadingRepository readingRepository;
    private final IotRejectedReadingRepository rejectedRepository;
    private final IotExternalClients clients;
    private final IotSupportService support;
    private final IotProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public DeviceRegistrationResponse registerDevice(RegisterDeviceRequest req, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IotApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Idempotency-Key required");
        }
        IotIdentityFilter.Identity actor = requireIdentity();
        if (!actor.hasAnyRole(AuthRole.AGENT, AuthRole.IOT_OPERATOR, AuthRole.ADMIN)) {
            throw new IotApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        Optional<DeviceRegistrationResponse> replay = support.findReplay(idempotencyKey, "IOT-02", req, DeviceRegistrationResponse.class);
        if (replay.isPresent()) return replay.get();

        if (!clients.isModelSupported(req.getModel(), req.getProtocol())) {
            throw new IotApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PROTOCOL_UNSUPPORTED", "Unsupported model or protocol");
        }
        if (deviceRepository.findBySerialNumber(req.getSerialNumber()).isPresent()) {
            throw new IotApiException(HttpStatus.CONFLICT, "DEVICE_ALREADY_REGISTERED", "This device is already registered");
        }
        IotExternalClients.PlotView plot = clients.fetchPlot(req.getPlotId());
        if (plot == null) {
            throw new IotApiException(HttpStatus.NOT_FOUND, "PLOT_NOT_FOUND", "Plot not found");
        }
        if (!plot.active()) {
            throw new IotApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PLOT_NOT_ACTIVE", "The plot is not active");
        }
        double maxDist = req.getDeviceType() == DeviceType.SOIL_PROBE ? 30.0 : properties.getRules().getRainGaugeMaxDistanceMeters();
        if (plot.distanceMeters() > maxDist) {
            throw new IotApiException(HttpStatus.UNPROCESSABLE_ENTITY, "LOCATION_OUTSIDE_PLOT",
                    "The location does not match the plot", Map.of("distanceMeters", plot.distanceMeters()));
        }
        if (deviceRepository.findByPlotIdAndDeviceTypeAndStatusNot(req.getPlotId(), req.getDeviceType(), DeviceStatus.RETIRED).size()
                >= properties.getRules().getMaxPerPlotType()) {
            throw new IotApiException(HttpStatus.CONFLICT, "PLOT_DEVICE_LIMIT", "Plot already has max sensors of this type");
        }
        if (!clients.isConsentValid(req.getConsentRef())) {
            throw new IotApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CONSENT_REQUIRED", "Sensor consent required");
        }

        Instant now = Instant.now();
        String deviceId = "dev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String rawToken = "enr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        double lat = ((Number) req.getLocation().getOrDefault("latitude", 0.0)).doubleValue();
        double lon = ((Number) req.getLocation().getOrDefault("longitude", 0.0)).doubleValue();
        double acc = ((Number) req.getLocation().getOrDefault("accuracyMeters", 5.0)).doubleValue();
        List<String> warnings = plot.distanceMeters() > properties.getRules().getRainGaugeWarnDistanceMeters()
                ? List.of("RAIN_GAUGE_BEYOND_150M") : List.of();

        deviceRepository.save(IotDeviceEntity.builder()
                .deviceId(deviceId).serialNumber(req.getSerialNumber()).deviceType(req.getDeviceType())
                .manufacturer(req.getManufacturer()).model(req.getModel()).protocol(req.getProtocol())
                .status(DeviceStatus.PROVISIONED).connectivity(ConnectivityStatus.STALE)
                .plotId(req.getPlotId()).farmerId(plot.farmerId()).districtCode(plot.districtCode())
                .latitude(lat).longitude(lon).accuracyMeters(acc)
                .expectedIntervalSeconds(req.getExpectedIntervalSeconds() != null ? req.getExpectedIntervalSeconds() : 900)
                .installerId(actor.actorId()).consentRef(req.getConsentRef()).installPhotoToken(req.getInstallPhotoToken())
                .calibrationVersion(1).calibrationJson(writeJson(Map.of("scale", 1.0, "offset", 0.0)))
                .clockOffsetSeconds(0).alertsCsv("").credentialState("ENROLLMENT_PENDING")
                .enrolmentTokenHash(clients.sha256(rawToken)).enrolmentExpiresAt(now.plus(24, ChronoUnit.HOURS))
                .createdAt(now).versionNum(1).build());

        support.enqueue(properties.getKafka().getIotTopic(), "device.binding.changed", deviceId, Map.of(
                "eventType", "device.binding.changed", "deviceId", deviceId, "plotId", req.getPlotId(),
                "districtCode", plot.districtCode(), "action", "BOUND", "effectiveFrom", now.toString()));

        DeviceRegistrationResponse resp = DeviceRegistrationResponse.builder()
                .deviceId(deviceId).serialNumber(req.getSerialNumber()).status(DeviceStatus.PROVISIONED.name())
                .binding(Map.of("plotId", req.getPlotId(), "districtCode", plot.districtCode(),
                        "distanceToPlotMeters", plot.distanceMeters(), "locationVerified", true, "warnings", warnings))
                .mqtt(Map.of("clientId", deviceId, "topics", List.of("sensors/" + deviceId + "/data", "sensors/" + deviceId + "/status")))
                .enrolmentToken(rawToken).enrolmentExpiresAt(now.plus(24, ChronoUnit.HOURS).toString())
                .calibration(Map.of("version", 1)).version(1).build();
        support.saveIdempotency(idempotencyKey, "IOT-02", req, 201, resp);
        return resp;
    }

    public DeviceHealthResponse getHealth(String deviceId) {
        IotIdentityFilter.Identity actor = requireIdentity();
        IotDeviceEntity d = loadVisibleDevice(deviceId, actor);
        boolean isFarmerOnly = actor.hasRole(AuthRole.FARMER) && !actor.hasAnyRole(
                AuthRole.SUPPORT, AuthRole.IOT_OPERATOR, AuthRole.ADMIN, AuthRole.AUDITOR);

        String batState = d.getBatteryPercent() == null ? "UNKNOWN"
                : (d.getBatteryPercent() < 10 ? "CRITICAL" : (d.getBatteryPercent() <= 30 ? "LOW" : "OK"));
        return DeviceHealthResponse.builder()
                .deviceId(d.getDeviceId()).deviceType(d.getDeviceType().name()).model(d.getModel())
                .status(d.getStatus().name()).connectivity(d.getConnectivity().name())
                .lastSeenAt(d.getLastSeenAt() != null ? d.getLastSeenAt().toString() : null)
                .lastReadingAt(d.getLastReadingAt() != null ? d.getLastReadingAt().toString() : null)
                .battery(isFarmerOnly ? null : Map.of("percent", Objects.toString(d.getBatteryPercent(), "N/A"), "state", batState))
                .binding(isFarmerOnly ? null : Map.of("plotId", Objects.toString(d.getPlotId(), ""), "districtCode", Objects.toString(d.getDistrictCode(), "")))
                .calibrationVersion(d.getCalibrationVersion()).version(d.getVersionNum()).build();
    }

    @Transactional
    public DeviceCalibrationResponse calibrateDevice(String deviceId, CalibrateDeviceRequest req) {
        IotIdentityFilter.Identity actor = requireIdentity();
        if (!actor.hasAnyRole(AuthRole.IOT_OPERATOR, AuthRole.ADMIN)) {
            throw new IotApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        IotDeviceEntity d = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IotApiException(HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND", "Device not found"));
        if (d.getStatus() == DeviceStatus.RETIRED) {
            throw new IotApiException(HttpStatus.CONFLICT, "DEVICE_STATE_INVALID", "Device is retired");
        }

        Instant now = Instant.now();
        if (req.getApproveProposal() != null && !req.getApproveProposal().isBlank()) {
            if (d.getPendingProposalHash() == null || !req.getApproveProposal().equals(d.getPendingProposalHash())) {
                throw new IotApiException(HttpStatus.CONFLICT, "PROPOSAL_CHANGED", "Proposal hash mismatch");
            }
            if (actor.actorId().equals(d.getPendingProposalActor())) {
                throw new IotApiException(HttpStatus.FORBIDDEN, "APPROVER_NOT_INDEPENDENT", "Second approver must differ");
            }
            Map<String, Object> prop = readMap(d.getPendingProposalJson());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> channels = (List<Map<String, Object>>) prop.get("channels");
            d.setCalibrationVersion(d.getCalibrationVersion() + 1);
            d.setCalibrationJson(writeJson(channels.getFirst()));
            d.setPendingProposalHash(null);
            d.setPendingProposalActor(null);
            d.setVersionNum(d.getVersionNum() + 1);
            deviceRepository.save(d);
            support.enqueue(properties.getKafka().getIotTopic(), "device.calibration.changed", deviceId, Map.of(
                    "eventType", "device.calibration.changed", "deviceId", deviceId,
                    "calibrationVersion", d.getCalibrationVersion(), "approver", actor.actorId(), "timestamp", now.toString()));
            return DeviceCalibrationResponse.builder()
                    .deviceId(deviceId).calibrationVersion(d.getCalibrationVersion()).status("APPLIED")
                    .channels(channels).approvalsRequired(List.of()).version(d.getVersionNum()).build();
        }

        if (("FIELD_CHECK".equals(req.getReason()) || "DRIFT_CORRECTION".equals(req.getReason())) && req.getReference() == null) {
            throw new IotApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EVIDENCE_REQUIRED", "Reference reading required");
        }
        Map<String, Object> ch = req.getChannels().getFirst();
        double scale = ((Number) ch.getOrDefault("scale", 1.0)).doubleValue();
        double offset = ((Number) ch.getOrDefault("offset", 0.0)).doubleValue();
        if (scale < 0.5 || scale > 1.5 || offset < -20 || offset > 20) {
            throw new IotApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CALIBRATION_OUT_OF_BOUNDS", "Calibration beyond hard limits");
        }
        boolean needsSecond = Math.abs(scale - 1.0) > 0.10 || req.isReprocess();
        if (needsSecond) {
            String propHash = clients.sha256(deviceId + "|" + writeJson(req.getChannels()) + "|" + now);
            d.setPendingProposalHash(propHash);
            d.setPendingProposalActor(actor.actorId());
            d.setPendingProposalJson(writeJson(Map.of("channels", req.getChannels())));
            deviceRepository.save(d);
            return DeviceCalibrationResponse.builder()
                    .deviceId(deviceId).calibrationVersion(d.getCalibrationVersion()).status("PENDING_APPROVAL")
                    .channels(req.getChannels()).approvalsRequired(List.of("IOT_OPERATOR"))
                    .proposalHash(propHash).version(d.getVersionNum()).build();
        }

        d.setCalibrationVersion(d.getCalibrationVersion() + 1);
        d.setCalibrationJson(writeJson(ch));
        d.setVersionNum(d.getVersionNum() + 1);
        deviceRepository.save(d);
        support.enqueue(properties.getKafka().getIotTopic(), "device.calibration.changed", deviceId, Map.of(
                "eventType", "device.calibration.changed", "deviceId", deviceId,
                "calibrationVersion", d.getCalibrationVersion(), "changedBy", actor.actorId(), "timestamp", now.toString()));

        return DeviceCalibrationResponse.builder()
                .deviceId(deviceId).calibrationVersion(d.getCalibrationVersion()).status("APPLIED")
                .channels(req.getChannels()).effectiveFrom(req.getEffectiveFrom().toString())
                .approvalsRequired(List.of()).version(d.getVersionNum()).build();
    }

    @Transactional
    public DeviceCredentialResponse manageCredentials(String deviceId, ManageDeviceCredentialsRequest req, String idempotencyKey) {
        IotIdentityFilter.Identity actor = requireIdentity();
        if (!actor.hasAnyRole(AuthRole.IOT_OPERATOR, AuthRole.ADMIN)
                && !(actor.hasRole(AuthRole.AGENT) && req.getAction() == CredentialAction.REISSUE_ENROLLMENT)) {
            throw new IotApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        IotDeviceEntity d = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IotApiException(HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND", "Device not found"));
        Instant now = Instant.now();
        String enrToken = null;
        if (req.getAction() == CredentialAction.REVOKE) {
            d.setCredentialState("REVOKED");
            d.setStatus(DeviceStatus.SUSPENDED);
        } else if (req.getAction() == CredentialAction.REISSUE_ENROLLMENT) {
            enrToken = "enr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            d.setCredentialState("ENROLLMENT_PENDING");
            d.setEnrolmentTokenHash(clients.sha256(enrToken));
            d.setEnrolmentExpiresAt(now.plus(24, ChronoUnit.HOURS));
        } else {
            d.setCredentialState("ROTATING");
        }
        d.setVersionNum(d.getVersionNum() + 1);
        deviceRepository.save(d);
        return DeviceCredentialResponse.builder()
                .deviceId(deviceId).status(d.getStatus().name())
                .credential(Map.of("state", d.getCredentialState()))
                .enrolmentToken(enrToken).revokedAt(req.getAction() == CredentialAction.REVOKE ? now.toString() : null)
                .version(d.getVersionNum()).build();
    }

    @Transactional
    public DeviceBindingResponse updateBinding(String deviceId, UpdateDeviceBindingRequest req) {
        IotIdentityFilter.Identity actor = requireIdentity();
        if (!actor.hasAnyRole(AuthRole.IOT_OPERATOR, AuthRole.ADMIN)) {
            throw new IotApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        IotDeviceEntity d = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IotApiException(HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND", "Device not found"));
        if (d.getStatus() == DeviceStatus.RETIRED) {
            throw new IotApiException(HttpStatus.CONFLICT, "DEVICE_STATE_INVALID", "Device is retired");
        }

        Instant now = Instant.now();
        if (req.getApproveProposal() != null && !req.getApproveProposal().isBlank()) {
            if (!req.getApproveProposal().equals(d.getPendingProposalHash())) {
                throw new IotApiException(HttpStatus.CONFLICT, "PROPOSAL_CHANGED", "Proposal mismatch");
            }
            Map<String, Object> prop = readMap(d.getPendingProposalJson());
            String newPlot = (String) prop.get("plotId");
            String actStr = (String) prop.get("action");
            d.setPlotId(newPlot);
            if ("RETIRE".equals(actStr)) d.setStatus(DeviceStatus.RETIRED);
            d.setPendingProposalHash(null);
            d.setVersionNum(d.getVersionNum() + 1);
            deviceRepository.save(d);
            return DeviceBindingResponse.builder().deviceId(deviceId).status(d.getStatus().name())
                    .approvalStatus("APPLIED").binding(Map.of("plotId", Objects.toString(newPlot, "")))
                    .version(d.getVersionNum()).build();
        }

        IotExternalClients.PlotView curPlot = d.getPlotId() != null ? clients.fetchPlot(d.getPlotId()) : null;
        if (curPlot != null && curPlot.hasActivePolicy() && req.getAction() != BindingAction.RESUME) {
            String propHash = clients.sha256(deviceId + "|" + req.getAction() + "|" + Objects.toString(req.getPlotId(), ""));
            d.setPendingProposalHash(propHash);
            d.setPendingProposalActor(actor.actorId());
            d.setPendingProposalJson(writeJson(Map.of("action", req.getAction().name(), "plotId", Objects.toString(req.getPlotId(), ""))));
            deviceRepository.save(d);
            return DeviceBindingResponse.builder().deviceId(deviceId).status(d.getStatus().name())
                    .approvalStatus("PENDING_APPROVAL").approvalsRequired(List.of("IOT_OPERATOR"))
                    .proposalHash(propHash).version(d.getVersionNum()).build();
        }

        if (req.getAction() == BindingAction.SUSPEND) d.setStatus(DeviceStatus.SUSPENDED);
        else if (req.getAction() == BindingAction.RESUME) d.setStatus(DeviceStatus.ACTIVE);
        else if (req.getAction() == BindingAction.RETIRE) d.setStatus(DeviceStatus.RETIRED);
        else if (req.getAction() == BindingAction.UNBIND) d.setPlotId(null);
        else if (req.getAction() == BindingAction.MOVE) d.setPlotId(req.getPlotId());
        d.setVersionNum(d.getVersionNum() + 1);
        deviceRepository.save(d);

        support.enqueue(properties.getKafka().getIotTopic(), "device.binding.changed", deviceId, Map.of(
                "eventType", "device.binding.changed", "deviceId", deviceId,
                "plotId", Objects.toString(d.getPlotId(), ""), "action", req.getAction().name(), "effectiveFrom", now.toString()));

        return DeviceBindingResponse.builder().deviceId(deviceId).status(d.getStatus().name())
                .approvalStatus("APPLIED").binding(Map.of("plotId", Objects.toString(d.getPlotId(), "")))
                .version(d.getVersionNum()).build();
    }

    public DeviceListResponse listDevices(String connectivity, String districtCode) {
        IotIdentityFilter.Identity actor = requireIdentity();
        boolean isFarmer = actor.hasRole(AuthRole.FARMER) && !actor.hasAnyRole(
                AuthRole.AGENT, AuthRole.SUPPORT, AuthRole.IOT_OPERATOR, AuthRole.ADMIN, AuthRole.AUDITOR);
        List<IotDeviceEntity> devs = deviceRepository.findAll().stream()
                .filter(d -> !isFarmer || actor.actorId().equals(d.getFarmerId()))
                .filter(d -> connectivity == null || d.getConnectivity().name().equalsIgnoreCase(connectivity))
                .filter(d -> districtCode == null || districtCode.equalsIgnoreCase(d.getDistrictCode()))
                .toList();
        List<Map<String, Object>> items = devs.stream().map(d -> Map.<String, Object>of(
                "deviceId", d.getDeviceId(), "deviceType", d.getDeviceType().name(), "model", d.getModel(),
                "status", d.getStatus().name(), "connectivity", d.getConnectivity().name(),
                "plotId", Objects.toString(d.getPlotId(), ""), "version", d.getVersionNum())).toList();
        return DeviceListResponse.builder().items(items).counts(Map.of("TOTAL", (long) items.size())).build();
    }

    public DeviceReadingsResponse getReadings(String deviceId, Instant from, Instant to, ReadingResolution resolution) {
        IotIdentityFilter.Identity actor = requireIdentity();
        IotDeviceEntity d = loadVisibleDevice(deviceId, actor);
        boolean isStaff = actor.hasAnyRole(AuthRole.SUPPORT, AuthRole.IOT_OPERATOR, AuthRole.ADMIN, AuthRole.AUDITOR);
        if (!isStaff && resolution == ReadingResolution.RAW) {
            throw new IotApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Farmers receive DAILY readings only");
        }
        if (ChronoUnit.DAYS.between(from, to) > 7) {
            throw new IotApiException(HttpStatus.UNPROCESSABLE_ENTITY, "RANGE_TOO_LARGE", "Choose a range of 7 days or fewer");
        }
        List<IotReadingEntity> rows = readingRepository.findByDeviceIdAndObservedAtBetweenOrderByObservedAtAsc(deviceId, from, to);
        List<IotRejectedReadingEntity> rejected = isStaff
                ? rejectedRepository.findByDeviceIdAndReceivedAtBetweenOrderByReceivedAtDesc(deviceId, from, to)
                : List.of();
        List<Map<String, Object>> pts = rows.stream().map(r -> Map.<String, Object>of(
                "time", r.getObservedAt().toString(), "value", r.getMetricValue(),
                "calibrationVersion", r.getCalibrationVersion(), "revision", r.getRevision(),
                "lateArrival", r.isLateArrival())).toList();
        List<Map<String, Object>> rejList = isStaff ? rejected.stream().map(rj -> Map.<String, Object>of(
                "time", rj.getReceivedAt().toString(), "reasonCode", rj.getReasonCode())).toList() : null;
        return DeviceReadingsResponse.builder()
                .deviceId(deviceId).plotId(isStaff ? d.getPlotId() : null).channel("rain.mm").unit("mm")
                .resolution(resolution.name()).points(pts).rejected(rejList)
                .summary(Map.of("count", pts.size(), "rejected", rejected.size())).build();
    }

    private IotDeviceEntity loadVisibleDevice(String deviceId, IotIdentityFilter.Identity actor) {
        IotDeviceEntity d = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IotApiException(HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND", "Device not found"));
        boolean isStaff = actor.hasAnyRole(AuthRole.SUPPORT, AuthRole.IOT_OPERATOR, AuthRole.ADMIN, AuthRole.AUDITOR);
        if (!isStaff && !actor.actorId().equals(d.getFarmerId()) && !actor.actorId().equals(d.getInstallerId())) {
            throw new IotApiException(HttpStatus.NOT_FOUND, "DEVICE_NOT_FOUND", "Device not found");
        }
        return d;
    }

    private IotIdentityFilter.Identity requireIdentity() {
        IotIdentityFilter.Identity id = IotIdentityFilter.CTX.get();
        if (id == null) throw new IotApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Sign in required");
        return id;
    }

    private String writeJson(Object o) {
        try { return objectMapper.writeValueAsString(o); } catch (Exception e) { return "{}"; }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String json) {
        try { return objectMapper.readValue(json, Map.class); } catch (Exception e) { return Map.of(); }
    }
}
