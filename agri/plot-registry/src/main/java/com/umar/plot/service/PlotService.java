package com.umar.plot.service;

import com.umar.enums.auth.AuthRole;
import com.umar.enums.auth.DelegationScope;
import com.umar.enums.plot.*;
import com.umar.payload.request.plot.*;
import com.umar.payload.response.plot.*;
import com.umar.plot.client.*;
import com.umar.plot.config.PlotProperties;
import com.umar.plot.domain.entity.*;
import com.umar.plot.exception.PlotApiException;
import com.umar.plot.geo.GeoUtils;
import com.umar.plot.repository.*;
import com.umar.plot.security.PlotIdentity;
import com.umar.plot.security.PlotSecurityContext;
import com.umar.plot.service.support.PlotIdempotencyService;
import com.umar.plot.service.support.PlotOutboxService;
import com.umar.plot.service.support.PlotRateLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PlotService {

    private static final Map<String, int[]> CROP_CYCLES = Map.of(
            "MAIZE", new int[]{90, 150},
            "BEANS", new int[]{60, 110},
            "WHEAT", new int[]{90, 140},
            "RICE", new int[]{90, 150},
            "SORGHUM", new int[]{85, 140},
            "MILLET", new int[]{70, 120},
            "COFFEE", new int[]{180, 365},
            "TEA", new int[]{180, 365},
            "CASSAVA", new int[]{180, 365},
            "COTTON", new int[]{120, 180}
    );

    private final PlotRepository plotRepository;
    private final PlotSeasonRepository seasonRepository;
    private final PlotValidationRepository validationRepository;
    private final PlotCoverageWindowRepository coverageWindowRepository;
    private final AuthKycClient authKycClient;
    private final FarmerProfileClient farmerProfileClient;
    private final DataVaultClient dataVaultClient;
    private final LandCoverClient landCoverClient;
    private final HookSdkClient hookSdkClient;
    private final PlotOutboxService outboxService;
    private final PlotIdempotencyService idempotencyService;
    private final PlotRateLimitService rateLimitService;
    private final PlotProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public PlotResponse registerPlot(
            String delegationHeader,
            String idempotencyKey,
            RegisterPlotRequest request) {
        PlotIdentity actor = PlotSecurityContext.require();
        UUID farmerId = parseUuid(request.getFarmerId(), "VALIDATION_ERROR");

        FarmerProfileClient.FarmerStanding standing = farmerProfileClient.getFarmerStanding(farmerId);
        if (!standing.exists()) {
            throw new PlotApiException(HttpStatus.NOT_FOUND, "FARMER_NOT_FOUND", "Farmer not found");
        }
        if (!"ACTIVE".equals(standing.status())) {
            throw new PlotApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FARMER_NOT_ELIGIBLE",
                    "This farmer cannot register plots");
        }

        UUID delegationId = null;
        if (delegationHeader != null && !delegationHeader.isBlank()) {
            delegationId = parseUuid(delegationHeader, "FORBIDDEN");
            UUID farmerUserId = tryParseUuid(standing.userId()).orElse(farmerId);
            if (actor.userId() == null || !authKycClient.validateDelegation(
                    delegationId, actor.userId(), farmerUserId, DelegationScope.PLOT_REGISTER)) {
                throw new PlotApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                        "Not authorised to register plots for this farmer");
            }
        } else if (actor.userId() == null || (!actor.userId().toString().equals(standing.userId())
                && !actor.userId().equals(farmerId))) {
            throw new PlotApiException(HttpStatus.FORBIDDEN, "FORBIDDEN",
                    "Not authorised to register plots for this farmer");
        }

        Optional<PlotResponse> replay = idempotencyService.findReplay(
                idempotencyKey, "PLT-01:" + farmerId, request, PlotResponse.class);
        if (replay.isPresent()) return replay.get();

        if (plotRepository.countByFarmerIdAndStatusNot(farmerId, PlotStatus.RETIRED)
                >= properties.getRules().getMaxPlotsPerFarmer()) {
            throw new PlotApiException(HttpStatus.CONFLICT, "PLOT_LIMIT_REACHED", "Plot limit reached");
        }

        if (request.getCapturedAt().isAfter(Instant.now().plus(5, ChronoUnit.MINUTES))
                || request.getCapturedAt().isBefore(Instant.now().minus(24, ChronoUnit.HOURS))) {
            throw new PlotApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "capturedAt must be within the last 24 hours and not in the future");
        }

        RegisterPlotRequest.GeoJsonPolygon polygon;
        AccuracyClass accuracyClass;
        Set<PlotFlag> flags = EnumSet.noneOf(PlotFlag.class);
        List<String> evidenceTokens = new ArrayList<>();
        if (request.getEvidenceTokens() != null) {
            evidenceTokens.addAll(request.getEvidenceTokens());
        }

        if (request.getCaptureMethod() == CaptureMethod.PIN_AREA) {
            if (request.getPin() == null || request.getAreaHa() == null
                    || request.getAreaHa() < 0.1 || request.getAreaHa() > 50.0) {
                throw new PlotApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "pin and areaHa (0.1 to 50) are required for PIN_AREA");
            }
            polygon = GeoUtils.buildSquareFromPin(request.getPin().getLat(), request.getPin().getLon(), request.getAreaHa());
            accuracyClass = AccuracyClass.LOW;
        } else {
            if (request.getGeometry() == null) {
                throw new PlotApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "geometry is required for " + request.getCaptureMethod());
            }
            polygon = request.getGeometry();
            if (request.getCaptureMethod() == CaptureMethod.GPS_WALK) {
                if (request.getGpsTrack() == null || request.getGpsTrack().size() < 4) {
                    throw new PlotApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                            "gpsTrack is required for GPS_WALK");
                }
                GpsCheckResult gpsCheck = checkGpsTrack(request.getGpsTrack());
                if (gpsCheck.anomaly()) flags.add(PlotFlag.GPS_ANOMALY);
                accuracyClass = gpsCheck.medianAccuracy() <= 5.0 ? AccuracyClass.HIGH : AccuracyClass.MEDIUM;
                evidenceTokens.add(dataVaultClient.storeGpsTrack(farmerId.toString(), writeJson(request.getGpsTrack())));
            } else {
                accuracyClass = AccuracyClass.HIGH;
            }
        }

        List<double[]> ring = GeoUtils.extractOuterRing(polygon, 500);
        GeoUtils.GeoMetrics metrics = GeoUtils.computeMetrics(ring);
        if (metrics.areaHa() < 0.01) {
            throw new PlotApiException(HttpStatus.BAD_REQUEST, "INVALID_GEOMETRY",
                    "The boundary is not valid", Map.of("detail", "AREA_TOO_SMALL"));
        }

        String regionCountry = inferCountry(metrics.centroidLat(), metrics.centroidLon());
        if (!properties.getRules().getSupportedCountries().contains(regionCountry)) {
            throw new PlotApiException(HttpStatus.UNPROCESSABLE_ENTITY, "OUTSIDE_SUPPORTED_COUNTRY",
                    "This location is not supported yet");
        }

        HookSdkClient.PreRegisterOutcome preOutcome = hookSdkClient.preRegister(farmerId.toString());
        if (preOutcome == HookSdkClient.PreRegisterOutcome.REJECT) {
            throw new PlotApiException(HttpStatus.UNPROCESSABLE_ENTITY, "FARMER_NOT_ELIGIBLE",
                    "Registration rejected by land registry policy");
        }
        if (preOutcome == HookSdkClient.PreRegisterOutcome.HOLD) {
            flags.add(PlotFlag.NOT_CROPLAND);
        }

        OverlapEvaluation overlap = evaluateOverlap(null, ring, true);
        if ("DUPLICATE".equals(overlap.band())) flags.add(PlotFlag.DUPLICATE);
        else if ("OVERLAP".equals(overlap.band())) flags.add(PlotFlag.OVERLAP);

        String plotId = "pl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String geoJson = writeJson(polygon);
        String geoHash = GeoUtils.sha256Hex(geoJson);
        PlotStatus status = flags.isEmpty() ? PlotStatus.REGISTERED : PlotStatus.HELD;
        Instant now = Instant.now();

        PlotEntity entity = PlotEntity.builder()
                .plotId(plotId)
                .farmerId(farmerId)
                .name(request.getName())
                .status(status)
                .captureMethod(request.getCaptureMethod())
                .accuracyClass(accuracyClass)
                .geometryGeojson(geoJson)
                .geometryVersion(1)
                .geometryHash(geoHash)
                .areaHa(metrics.areaHa())
                .minWidthM(metrics.minWidthM())
                .centroidLat(metrics.centroidLat())
                .centroidLon(metrics.centroidLon())
                .bbox(metrics.bbox())
                .geohash5(metrics.geohash5())
                .geohash7(metrics.geohash7())
                .country(regionCountry)
                .district("NYERI")
                .ward("MWEIGA")
                .coverageStatus(CoverageStatus.NONE)
                .monitoringStatus(MonitoringStatus.ACTIVE)
                .evidenceTokensCsv(String.join(",", evidenceTokens))
                .registeredBy(actor.userId())
                .delegationId(delegationId)
                .deviceId(request.getDeviceId())
                .attributesJson(writeJson(hookSdkClient.enrich(plotId)))
                .secondReviewPending(false)
                .createdAt(now)
                .build();
        entity.setFlags(flags);
        entity = plotRepository.saveAndFlush(entity);

        Map<String, Object> regEvt = new LinkedHashMap<>();
        regEvt.put("eventType", "plot.registered");
        regEvt.put("plotId", plotId);
        regEvt.put("farmerId", farmerId.toString());
        regEvt.put("areaHa", entity.getAreaHa());
        regEvt.put("geohash5", entity.getGeohash5());
        regEvt.put("captureMethod", entity.getCaptureMethod().name());
        regEvt.put("accuracyClass", entity.getAccuracyClass().name());
        regEvt.put("flags", flags.stream().map(Enum::name).toList());
        regEvt.put("timestamp", now.toString());
        outboxService.enqueue(properties.getKafka().getPlotTopic(), "plot.registered", plotId, regEvt);

        if (status == PlotStatus.HELD) {
            publishPlotFlagged(entity, overlap, now);
        }

        PlotResponse resp = PlotResponse.builder()
                .plotId(entity.getPlotId())
                .farmerId(entity.getFarmerId().toString())
                .name(entity.getName())
                .status(entity.getStatus().name())
                .areaHa(entity.getAreaHa())
                .accuracyClass(entity.getAccuracyClass().name())
                .centroid(Map.of("lat", entity.getCentroidLat(), "lon", entity.getCentroidLon()))
                .region(Map.of("country", entity.getCountry(), "district", entity.getDistrict(), "ward", entity.getWard()))
                .holdReason(status == PlotStatus.HELD ? "UNDER_REVIEW" : null)
                .nextAction(status == PlotStatus.HELD ? "WAIT_FOR_REVIEW" : "SET_SEASON")
                .version(entity.getVersion())
                .createdAt(now.toString())
                .build();
        idempotencyService.save(idempotencyKey, "PLT-01:" + farmerId, request, 201, resp);
        return resp;
    }

    public PlotResponse getPlot(String plotId, String geometryMode, String include, String delegationHeader) {
        PlotIdentity actor = PlotSecurityContext.require();
        PlotEntity plot = plotRepository.findById(plotId)
                .orElseThrow(() -> new PlotApiException(HttpStatus.NOT_FOUND, "PLOT_NOT_FOUND", "Plot not found"));

        boolean isStaff = actor.hasAnyRole(AuthRole.SUPPORT, AuthRole.UNDERWRITER,
                AuthRole.PLOT_REVIEWER, AuthRole.COMPLIANCE_OFFICER, AuthRole.ADMIN);
        boolean isOwner = actor.userId() != null && actor.userId().equals(plot.getFarmerId());
        boolean isDelegated = false;
        if (!isStaff && !isOwner && actor.hasRole(AuthRole.AGENT) && delegationHeader != null && actor.userId() != null) {
            UUID delId = parseUuid(delegationHeader, "PLOT_NOT_FOUND");
            isDelegated = authKycClient.validateDelegation(delId, actor.userId(), plot.getFarmerId(), DelegationScope.PLOT_REGISTER);
        }
        if (!isStaff && !isOwner && !isDelegated) {
            throw new PlotApiException(HttpStatus.NOT_FOUND, "PLOT_NOT_FOUND", "Plot not found");
        }

        String geom = geometryMode != null ? geometryMode.toLowerCase() : (isStaff || isOwner ? "simplified" : "none");
        if (!Set.of("none", "simplified", "full").contains(geom)) {
            throw new PlotApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid geometry mode");
        }

        String displayStatus = (!isStaff && plot.getStatus() == PlotStatus.HELD)
                ? "UNDER_REVIEW" : plot.getStatus().name();

        PlotResponse.PlotResponseBuilder builder = PlotResponse.builder()
                .plotId(plot.getPlotId())
                .farmerId(plot.getFarmerId().toString())
                .name(plot.getName())
                .status(displayStatus)
                .areaHa(plot.getAreaHa())
                .accuracyClass(plot.getAccuracyClass().name())
                .captureMethod(plot.getCaptureMethod().name())
                .centroid(Map.of("lat", plot.getCentroidLat(), "lon", plot.getCentroidLon()))
                .region(Map.of("country", plot.getCountry(), "district", plot.getDistrict(), "ward", plot.getWard()))
                .monitoringStatus(plot.getMonitoringStatus().name())
                .geometryVersion(plot.getGeometryVersion())
                .version(plot.getVersion())
                .createdAt(plot.getCreatedAt().toString());

        if (!"none".equals(geom)) {
            builder.geometry(readJsonMap(plot.getGeometryGeojson()));
        }

        Set<String> inc = include == null ? Set.of("season") : new HashSet<>(Arrays.asList(include.toLowerCase().split(",")));
        if (inc.contains("season")) {
            seasonRepository.findFirstByPlotIdOrderByCreatedAtDesc(plotId).ifPresent(s ->
                    builder.season(Map.of(
                            "seasonId", s.getSeasonId(),
                            "cropCode", s.getCropCode(),
                            "plantingDate", s.getPlantingDate().toString(),
                            "expectedHarvestDate", s.getExpectedHarvestDate().toString(),
                            "irrigationType", s.getIrrigationType().name(),
                            "status", s.getStatus().name())));
        }
        if (inc.contains("coverage")) {
            builder.coverage(Map.of("status", plot.getCoverageStatus().name()));
        }
        if (isStaff) {
            builder.flags(plot.getFlags().stream().map(Enum::name).toList());
            if (inc.contains("validation")) {
                validationRepository.findFirstByPlotIdOrderByCreatedAtDesc(plotId).ifPresent(v ->
                        builder.validation(Map.of(
                                "outcome", v.getOutcome(),
                                "ruleSetVersion", v.getRuleSetVersion(),
                                "createdAt", v.getCreatedAt().toString())));
            }
        }
        return builder.build();
    }

    @Transactional
    public RecordSeasonResponse recordSeason(
            String plotId,
            String ifMatch,
            String delegationHeader,
            RecordSeasonRequest request) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PlotApiException(HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED",
                    "If-Match header is required");
        }
        PlotIdentity actor = PlotSecurityContext.require();
        PlotEntity plot = plotRepository.findById(plotId)
                .orElseThrow(() -> new PlotApiException(HttpStatus.NOT_FOUND, "PLOT_NOT_FOUND", "Plot not found"));

        authorizePlotEdit(actor, plot, delegationHeader);

        if (plot.getStatus() == PlotStatus.HELD || plot.getStatus() == PlotStatus.REJECTED || plot.getStatus() == PlotStatus.RETIRED) {
            throw new PlotApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PLOT_NOT_ELIGIBLE",
                    "This plot cannot take a season right now");
        }

        int expectedVersion = parseIfMatch(ifMatch);
        if (!Objects.equals(plot.getVersion(), expectedVersion)) {
            throw new PlotApiException(HttpStatus.PRECONDITION_FAILED, "VERSION_CONFLICT",
                    "Plot changed. Reload and retry", Map.of("currentVersion", plot.getVersion()));
        }

        String crop = request.getCropCode().toUpperCase();
        int[] range = CROP_CYCLES.get(crop);
        if (range == null) {
            throw new PlotApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CROP_NOT_SUPPORTED",
                    "This crop is not supported here");
        }

        long days = ChronoUnit.DAYS.between(request.getPlantingDate(), request.getExpectedHarvestDate());
        if (days < range[0] || days > range[1]) {
            throw new PlotApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CROP_CYCLE_IMPLAUSIBLE",
                    "Check the dates", Map.of("allowedDays", Map.of("min", range[0], "max", range[1])));
        }

        Optional<PlotSeasonEntity> existingOpt = seasonRepository.findFirstByPlotIdAndStatusInOrderByCreatedAtDesc(
                plotId, List.of(SeasonStatus.PLANNED, SeasonStatus.ACTIVE));

        Instant now = Instant.now();
        PlotSeasonEntity season;
        String changeType;
        if (existingOpt.isPresent()) {
            season = existingOpt.get();
            if (season.getLockedAt() != null) {
                boolean cropOrPlantingChanged = !season.getCropCode().equals(crop)
                        || !season.getPlantingDate().equals(request.getPlantingDate());
                long harvestShift = ChronoUnit.DAYS.between(season.getExpectedHarvestDate(), request.getExpectedHarvestDate());
                if (cropOrPlantingChanged || harvestShift < 0 || harvestShift > 14) {
                    throw new PlotApiException(HttpStatus.CONFLICT, "SEASON_LOCKED",
                            "The season can no longer be changed", Map.of("review", true));
                }
                season.setExpectedHarvestDate(request.getExpectedHarvestDate());
                season.setUpdatedAt(now);
            } else {
                season.setCropCode(crop);
                season.setPlantingDate(request.getPlantingDate());
                season.setExpectedHarvestDate(request.getExpectedHarvestDate());
                season.setIrrigationType(request.getIrrigationType() != null ? request.getIrrigationType() : IrrigationType.RAINFED);
                season.setSeedVariety(request.getSeedVariety());
                season.setStatus(request.getPlantingDate().isAfter(LocalDate.now()) ? SeasonStatus.PLANNED : SeasonStatus.ACTIVE);
                season.setUpdatedAt(now);
            }
            changeType = "CORRECTED";
        } else {
            season = PlotSeasonEntity.builder()
                    .seasonId("se_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10))
                    .plotId(plotId)
                    .seasonLabel(request.getPlantingDate().getYear() + "-S1")
                    .cropCode(crop)
                    .plantingDate(request.getPlantingDate())
                    .expectedHarvestDate(request.getExpectedHarvestDate())
                    .irrigationType(request.getIrrigationType() != null ? request.getIrrigationType() : IrrigationType.RAINFED)
                    .seedVariety(request.getSeedVariety())
                    .status(request.getPlantingDate().isAfter(LocalDate.now()) ? SeasonStatus.PLANNED : SeasonStatus.ACTIVE)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            changeType = "CREATED";
        }
        seasonRepository.save(season);

        plot.setName(plot.getName());
        plot = plotRepository.saveAndFlush(plot);

        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("eventType", "plot.season.updated");
        evt.put("plotId", plotId);
        evt.put("farmerId", plot.getFarmerId().toString());
        evt.put("seasonId", season.getSeasonId());
        evt.put("cropCode", season.getCropCode());
        evt.put("plantingDate", season.getPlantingDate().toString());
        evt.put("expectedHarvestDate", season.getExpectedHarvestDate().toString());
        evt.put("changeType", changeType);
        evt.put("timestamp", now.toString());
        outboxService.enqueue(properties.getKafka().getPlotTopic(), "plot.season.updated", plotId, evt);

        return RecordSeasonResponse.builder()
                .plotId(plotId)
                .season(Map.of(
                        "seasonId", season.getSeasonId(),
                        "seasonLabel", season.getSeasonLabel(),
                        "cropCode", season.getCropCode(),
                        "plantingDate", season.getPlantingDate().toString(),
                        "expectedHarvestDate", season.getExpectedHarvestDate().toString(),
                        "irrigationType", season.getIrrigationType().name(),
                        "status", season.getStatus().name()))
                .lockDate(season.getLockedAt() != null ? season.getLockedAt().toString() : null)
                .flags(plot.getFlags().stream().map(Enum::name).toList())
                .version(plot.getVersion())
                .build();
    }

    @Transactional
    public OverlapCheckResponse checkOverlap(String delegationHeader, OverlapCheckRequest request) {
        PlotIdentity actor = PlotSecurityContext.require();
        boolean hasPlotId = request.getPlotId() != null && !request.getPlotId().isBlank();
        boolean hasGeom = request.getGeometry() != null;
        if (hasPlotId == hasGeom) {
            throw new PlotApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Supply exactly one of plotId or geometry");
        }
        boolean isStaff = actor.hasAnyRole(AuthRole.PLOT_REVIEWER, AuthRole.COMPLIANCE_OFFICER, AuthRole.ADMIN)
                || actor.isServiceCaller();
        if (Boolean.TRUE.equals(request.getApplyHold()) && (!isStaff || !hasPlotId)) {
            throw new PlotApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }

        rateLimitService.check("overlap:" + actor.actorId(), isStaff ? 600 : 60, 3600);

        PlotEntity targetPlot = null;
        List<double[]> candidateRing;
        if (hasPlotId) {
            targetPlot = plotRepository.findById(request.getPlotId())
                    .orElseThrow(() -> new PlotApiException(HttpStatus.NOT_FOUND, "PLOT_NOT_FOUND", "Plot not found"));
            if (!isStaff && (actor.userId() == null || !actor.userId().equals(targetPlot.getFarmerId()))) {
                throw new PlotApiException(HttpStatus.NOT_FOUND, "PLOT_NOT_FOUND", "Plot not found");
            }
            if (targetPlot.getStatus() == PlotStatus.REJECTED || targetPlot.getStatus() == PlotStatus.RETIRED) {
                throw new PlotApiException(HttpStatus.UNPROCESSABLE_ENTITY, "PLOT_NOT_CHECKABLE",
                        "This plot cannot be checked");
            }
            candidateRing = GeoUtils.extractOuterRing(readPolygon(targetPlot.getGeometryGeojson()), 500);
        } else {
            candidateRing = GeoUtils.extractOuterRing(request.getGeometry(), 500);
        }

        boolean includeOwn = request.getIncludeOwn() == null || request.getIncludeOwn();
        OverlapEvaluation eval = evaluateOverlap(targetPlot != null ? targetPlot.getPlotId() : null, candidateRing, includeOwn);

        String heldPlotId = null;
        if (Boolean.TRUE.equals(request.getApplyHold()) && targetPlot != null && !"CLEAR".equals(eval.band())) {
            Set<PlotFlag> flags = targetPlot.getFlags();
            flags.add("DUPLICATE".equals(eval.band()) ? PlotFlag.DUPLICATE : PlotFlag.OVERLAP);
            targetPlot.setFlags(flags);
            targetPlot.setStatus(PlotStatus.HELD);
            plotRepository.save(targetPlot);
            heldPlotId = targetPlot.getPlotId();
            publishPlotFlagged(targetPlot, eval, Instant.now());
        }

        String msg = switch (eval.band()) {
            case "DUPLICATE" -> "This boundary matches an existing plot and is under review.";
            case "OVERLAP" -> "This boundary overlaps another plot and requires review.";
            default -> "No overlap found with registered plots.";
        };

        return OverlapCheckResponse.builder()
                .result(eval.band())
                .thresholdPercent(eval.thresholdApplied())
                .heldPlotId(heldPlotId)
                .message(msg)
                .matches(isStaff ? eval.matches() : null)
                .checkedAt(Instant.now().toString())
                .ruleSetVersion(properties.getRules().getCurrentVersion())
                .build();
    }

    public PlotsWithinResponse findPlotsWithin(PlotsWithinRequest request) {
        PlotIdentity actor = PlotSecurityContext.require();
        boolean isService = actor.isServiceCaller();
        boolean isStaff = actor.hasAnyRole(AuthRole.UNDERWRITER, AuthRole.ADMIN);
        if (!isService && !isStaff) {
            throw new PlotApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        rateLimitService.check("within:" + actor.actorId(), isService ? 30 : 10, isService ? 60 : 3600);

        List<double[]> eventRing = GeoUtils.extractOuterRing(request.getPolygon(), 20_000);
        Instant asOf = request.getAsOf() != null ? request.getAsOf() : Instant.now();
        MatchMode mode = request.getMatchMode() != null ? request.getMatchMode() : MatchMode.INTERSECTS;
        double minOverlap = request.getMinOverlapPercent() != null ? request.getMinOverlapPercent() : 1.0;

        SpatialQueryResult res = querySpatialMatches(
                eventRing, asOf, mode, minOverlap,
                !"ANY".equalsIgnoreCase(request.getCoverageStatus()),
                request.getCropCodes(), request.getRegionCodes());

        int limit = request.getLimit() != null ? request.getLimit() : 1000;
        boolean countOnly = Boolean.TRUE.equals(request.getCountOnly()) || !isService;

        List<Map<String, Object>> pageItems = countOnly ? List.of() : res.matches().stream()
                .limit(limit)
                .map(m -> Map.<String, Object>of(
                        "plotId", m.plotId(),
                        "policyId", m.policyId(),
                        "farmerId", m.farmerId(),
                        "areaHa", m.areaHa(),
                        "overlapPercent", m.overlapPercent(),
                        "centroidGeohash6", m.geohash6()))
                .toList();

        return PlotsWithinResponse.builder()
                .items(pageItems)
                .snapshotTime(asOf.toString())
                .matchedCount(res.matches().size())
                .excluded(Map.of(
                        "pausedCount", res.pausedCount(),
                        "unvalidatedCount", res.unvalidatedCount(),
                        "outsideCoverageCount", res.outsideCoverageCount()))
                .totalAreaHa(Math.round(res.totalAreaHa() * 100.0) / 100.0)
                .simplifyToleranceMeters(request.getSimplifyToleranceMeters() != null ? request.getSimplifyToleranceMeters() : 0.0)
                .ruleSetVersion(properties.getRules().getCurrentVersion())
                .build();
    }

    @Transactional
    public ValidatePlotResponse validatePlot(String plotId, ValidatePlotRequest request) {
        PlotIdentity actor = PlotSecurityContext.require();
        boolean allowed = actor.isServiceCaller()
                || actor.hasAnyRole(AuthRole.PLOT_REVIEWER, AuthRole.UNDERWRITER, AuthRole.ADMIN);
        if (!allowed) {
            throw new PlotApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        PlotEntity plot = plotRepository.findById(plotId)
                .orElseThrow(() -> new PlotApiException(HttpStatus.NOT_FOUND, "PLOT_NOT_FOUND", "Plot not found"));
        if (plot.getStatus() == PlotStatus.RETIRED || plot.getStatus() == PlotStatus.REJECTED) {
            throw new PlotApiException(HttpStatus.CONFLICT, "PLOT_NOT_VALIDATABLE",
                    "This plot cannot be validated");
        }

        String ruleSetVersion = request != null && request.getRuleSetVersion() != null
                ? request.getRuleSetVersion() : properties.getRules().getCurrentVersion();
        boolean dryRun = request != null && Boolean.TRUE.equals(request.getDryRun());

        if (!dryRun) {
            Optional<PlotValidationEntity> cached = validationRepository
                    .findFirstByPlotIdAndGeometryVersionAndRuleSetVersionAndDryRunFalseOrderByCreatedAtDesc(
                            plotId, plot.getGeometryVersion(), ruleSetVersion);
            if (cached.isPresent()) {
                PlotValidationEntity v = cached.get();
                return ValidatePlotResponse.builder()
                        .plotId(plotId)
                        .outcome(v.getOutcome())
                        .reasonCodes(v.getReasonCodesCsv() == null || v.getReasonCodesCsv().isBlank()
                                ? List.of() : Arrays.asList(v.getReasonCodesCsv().split(",")))
                        .metrics(readJsonMap(v.getMetricsJson()))
                        .ruleSetVersion(ruleSetVersion)
                        .dryRun(false)
                        .validatedAt(v.getCreatedAt().toString())
                        .build();
            }
        }

        List<double[]> ring = GeoUtils.extractOuterRing(readPolygon(plot.getGeometryGeojson()), 500);
        LandCoverClient.LandCoverMetrics lc = landCoverClient.evaluate(plotId, plot.getCentroidLat(), plot.getCentroidLon());
        OverlapEvaluation overlap = evaluateOverlap(plotId, ring, true);

        List<String> hardRejects = new ArrayList<>();
        Set<PlotFlag> holdFlags = EnumSet.noneOf(PlotFlag.class);

        if (!lc.supportedArea()) hardRejects.add("OUTSIDE_SUPPORTED_AREA");
        if (plot.getAreaHa() < properties.getRules().getMinAreaHa()) hardRejects.add("BELOW_MINIMUM_SIZE");
        if (plot.getAreaHa() > properties.getRules().getMaxAreaHa()) hardRejects.add("ABOVE_MAXIMUM_SIZE");
        if (plot.getMinWidthM() < properties.getRules().getMinWidthM()) hardRejects.add("NARROW_SHAPE");

        if (!lc.available()) {
            holdFlags.add(PlotFlag.PENDING_CROPLAND_CHECK);
        } else {
            if (lc.excludedPercent() >= properties.getRules().getMaxExclusionPercent()) {
                hardRejects.add("EXCLUDED_ZONE");
            }
            if (lc.croplandPercent() < properties.getRules().getCroplandHoldPercent()) {
                hardRejects.add("NOT_CROPLAND");
            } else if (lc.croplandPercent() < properties.getRules().getCroplandValidatePercent()) {
                holdFlags.add(PlotFlag.NOT_CROPLAND);
            }
        }

        if ("DUPLICATE".equals(overlap.band())) holdFlags.add(PlotFlag.DUPLICATE);
        else if ("OVERLAP".equals(overlap.band())) holdFlags.add(PlotFlag.OVERLAP);

        String outcome;
        List<String> reasonCodes = new ArrayList<>(hardRejects);
        if (!hardRejects.isEmpty()) {
            outcome = PlotStatus.REJECTED.name();
        } else if (!holdFlags.isEmpty() || !plot.getFlags().isEmpty()) {
            outcome = PlotStatus.HELD.name();
            holdFlags.forEach(f -> reasonCodes.add(f.name()));
        } else {
            outcome = PlotStatus.VALIDATED.name();
        }

        Map<String, Object> metrics = Map.of(
                "areaHa", plot.getAreaHa(),
                "minWidthM", plot.getMinWidthM(),
                "croplandPercent", lc.available() ? lc.croplandPercent() : -1,
                "excludedPercent", lc.available() ? lc.excludedPercent() : -1,
                "overlapPercent", overlap.maxOverlapPercent()
        );
        Instant now = Instant.now();

        validationRepository.save(PlotValidationEntity.builder()
                .validationId(UUID.randomUUID())
                .plotId(plotId)
                .geometryVersion(plot.getGeometryVersion())
                .ruleSetVersion(ruleSetVersion)
                .outcome(outcome)
                .reasonCodesCsv(String.join(",", reasonCodes))
                .metricsJson(writeJson(metrics))
                .dryRun(dryRun)
                .createdAt(now)
                .build());

        if (!dryRun) {
            plot.setStatus(PlotStatus.valueOf(outcome));
            Set<PlotFlag> allFlags = plot.getFlags();
            allFlags.addAll(holdFlags);
            plot.setFlags(allFlags);
            if (PlotStatus.VALIDATED.name().equals(outcome)) {
                plot.setValidatedAt(now);
            }
            plotRepository.save(plot);

            String eventType = switch (PlotStatus.valueOf(outcome)) {
                case VALIDATED -> "plot.validated";
                case REJECTED -> "plot.rejected";
                default -> "plot.flagged";
            };
            Map<String, Object> evt = new LinkedHashMap<>();
            evt.put("eventType", eventType);
            evt.put("plotId", plotId);
            evt.put("farmerId", plot.getFarmerId().toString());
            evt.put("outcome", outcome);
            evt.put("reasonCodes", reasonCodes);
            evt.put("ruleSetVersion", ruleSetVersion);
            evt.put("timestamp", now.toString());
            outboxService.enqueue(properties.getKafka().getPlotTopic(), eventType, plotId, evt);
            hookSdkClient.postValidate(plotId, outcome);
        }

        return ValidatePlotResponse.builder()
                .plotId(plotId)
                .outcome(outcome)
                .reasonCodes(reasonCodes)
                .metrics(metrics)
                .ruleSetVersion(ruleSetVersion)
                .dryRun(dryRun)
                .validatedAt(now.toString())
                .build();
    }

    @Transactional
    public ReviewPlotResponse reviewHeldPlot(String plotId, ReviewPlotRequest request) {
        PlotIdentity actor = PlotSecurityContext.require();
        if (!actor.hasAnyRole(AuthRole.PLOT_REVIEWER, AuthRole.ADMIN)) {
            throw new PlotApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
        }
        if (request.getNote() == null || request.getNote().trim().length() < 20) {
            throw new PlotApiException(HttpStatus.BAD_REQUEST, "MISSING_JUSTIFICATION",
                    "A written justification is required");
        }

        PlotEntity plot = plotRepository.findById(plotId)
                .orElseThrow(() -> new PlotApiException(HttpStatus.NOT_FOUND, "PLOT_NOT_FOUND", "Plot not found"));
        if (plot.getStatus() != PlotStatus.HELD) {
            throw new PlotApiException(HttpStatus.CONFLICT, "PLOT_NOT_HELD", "This plot is not awaiting review");
        }
        if (actor.userId() != null && (actor.userId().equals(plot.getFarmerId())
                || actor.userId().equals(plot.getRegisteredBy()))) {
            throw new PlotApiException(HttpStatus.FORBIDDEN, "REVIEWER_CONFLICT",
                    "You cannot decide this case");
        }

        Instant now = Instant.now();
        String reviewerId = actor.actorId();
        boolean requiresFourEyes = plot.getFlags().contains(PlotFlag.DUPLICATE)
                || plot.getFlags().contains(PlotFlag.HIGH_FRAUD_SCORE);

        if (request.getDecision() == PlotReviewDecision.CONFIRM_VALID) {
            if (request.getEvidence() == null || request.getEvidence().isEmpty()) {
                throw new PlotApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "evidence is required for CONFIRM_VALID");
            }
            if (requiresFourEyes && !plot.isSecondReviewPending()) {
                plot.setReviewerId(reviewerId);
                plot.setReviewerNote(request.getNote().trim());
                plot.setSecondReviewPending(true);
                plotRepository.save(plot);
                return ReviewPlotResponse.builder()
                        .plotId(plotId).status(PlotStatus.HELD.name())
                        .secondReviewPending(true).decidedBy(reviewerId).build();
            }
            if (requiresFourEyes && plot.isSecondReviewPending()) {
                if (reviewerId.equals(plot.getReviewerId())) {
                    throw new PlotApiException(HttpStatus.CONFLICT, "SECOND_REVIEWER_REQUIRED",
                            "A different reviewer must confirm this decision");
                }
                plot.setSecondReviewerId(reviewerId);
                plot.setSecondReviewPending(false);
            } else {
                plot.setReviewerId(reviewerId);
            }

            Set<PlotFlag> remaining = plot.getFlags();
            if (request.getResolvedFlags() != null) {
                request.getResolvedFlags().forEach(remaining::remove);
            } else {
                remaining.clear();
            }
            plot.setFlags(remaining);
            plot.setReviewerNote(request.getNote().trim());
            plot.setStatus(remaining.isEmpty() ? PlotStatus.REGISTERED : PlotStatus.HELD);
            plotRepository.save(plot);

            return ReviewPlotResponse.builder()
                    .plotId(plotId)
                    .status(plot.getStatus().name())
                    .secondReviewPending(false)
                    .decidedBy(reviewerId)
                    .decidedAt(now.toString())
                    .build();
        } else {
            if (request.getReasonCodes() == null || request.getReasonCodes().isEmpty()) {
                throw new PlotApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "reasonCodes are required for REJECT or REQUEST_REDRAW");
            }
            plot.setReviewerId(reviewerId);
            plot.setReviewerNote(request.getNote().trim());
            plot.setSecondReviewPending(false);
            plot.setStatus(request.getDecision() == PlotReviewDecision.REJECT ? PlotStatus.REJECTED : PlotStatus.HELD);
            plotRepository.save(plot);

            Map<String, Object> evt = new LinkedHashMap<>();
            evt.put("eventType", "plot.rejected");
            evt.put("plotId", plotId);
            evt.put("farmerId", plot.getFarmerId().toString());
            evt.put("reasonCodes", request.getReasonCodes());
            evt.put("decision", request.getDecision().name());
            evt.put("decidedBy", reviewerId);
            evt.put("timestamp", now.toString());
            outboxService.enqueue(properties.getKafka().getPlotTopic(), "plot.rejected", plotId, evt);

            return ReviewPlotResponse.builder()
                    .plotId(plotId)
                    .status(plot.getStatus().name())
                    .secondReviewPending(false)
                    .decidedBy(reviewerId)
                    .decidedAt(now.toString())
                    .build();
        }
    }

    public List<PlotEntity> listFarmerPlots(UUID farmerId, Set<PlotStatus> statuses, CoverageStatus covFilter, boolean includeRetired) {
        return plotRepository.findByFarmerId(farmerId).stream()
                .filter(p -> includeRetired || p.getStatus() != PlotStatus.RETIRED)
                .filter(p -> statuses == null || statuses.isEmpty() || statuses.contains(p.getStatus()))
                .filter(p -> covFilter == null || p.getCoverageStatus() == covFilter)
                .toList();
    }

    public SpatialQueryResult querySpatialMatches(
            List<double[]> eventRing,
            Instant asOf,
            MatchMode mode,
            double minOverlapPercent,
            boolean activeCoverageOnly,
            List<String> cropCodes,
            List<String> regionCodes) {

        List<PlotEntity> candidates = plotRepository.findByStatusIn(List.of(PlotStatus.REGISTERED, PlotStatus.HELD, PlotStatus.VALIDATED));
        candidates.sort(Comparator.comparing(PlotEntity::getPlotId));

        List<SpatialMatch> matches = new ArrayList<>();
        int pausedCount = 0, unvalidatedCount = 0, outsideCovCount = 0;
        double totalAreaHa = 0.0;

        for (PlotEntity p : candidates) {
            List<double[]> pRing = GeoUtils.extractOuterRing(readPolygon(p.getGeometryGeojson()), 500);
            double overlap = GeoUtils.computeOverlapPercent(pRing, eventRing, 0.0);
            boolean spatialHit = switch (mode) {
                case INTERSECTS -> overlap > 0.0 || GeoUtils.intersects(pRing, eventRing);
                case CENTROID_WITHIN -> GeoUtils.pointInPolygon(p.getCentroidLon(), p.getCentroidLat(), eventRing);
                case MIN_OVERLAP -> overlap >= minOverlapPercent;
            };
            if (!spatialHit) continue;

            if (p.getStatus() != PlotStatus.VALIDATED) {
                unvalidatedCount++;
                continue;
            }
            if (p.getMonitoringStatus() == MonitoringStatus.PAUSED) {
                pausedCount++;
                continue;
            }
            List<PlotCoverageWindowEntity> windows = coverageWindowRepository.findByPlotIdAndStatus(p.getPlotId(), CoverageStatus.ACTIVE);
            Optional<PlotCoverageWindowEntity> activeWin = windows.stream()
                    .filter(w -> !asOf.isBefore(w.getValidFrom()) && !asOf.isAfter(w.getValidTo()))
                    .findFirst();
            if (activeCoverageOnly && activeWin.isEmpty()) {
                outsideCovCount++;
                continue;
            }
            String policyId = activeWin.map(PlotCoverageWindowEntity::getPolicyId).orElse("");
            matches.add(new SpatialMatch(
                    p.getPlotId(), policyId, p.getFarmerId().toString(), p.getAreaHa(),
                    overlap, p.getGeohash7().substring(0, 6),
                    activeWin.map(w -> w.getValidFrom().toString()).orElse(""),
                    activeWin.map(w -> w.getValidTo().toString()).orElse("")));
            totalAreaHa += p.getAreaHa();
        }
        return new SpatialQueryResult(matches, totalAreaHa, pausedCount, unvalidatedCount, outsideCovCount);
    }

    @Transactional
    public void onPolicyActivated(String plotId, String policyId, Instant from, Instant to) {
        plotRepository.findById(plotId).ifPresent(p -> {
            p.setCoverageStatus(CoverageStatus.ACTIVE);
            plotRepository.save(p);
            coverageWindowRepository.save(PlotCoverageWindowEntity.builder()
                    .windowId(UUID.randomUUID())
                    .plotId(plotId)
                    .policyId(policyId)
                    .validFrom(from)
                    .validTo(to)
                    .status(CoverageStatus.ACTIVE)
                    .build());
            seasonRepository.findFirstByPlotIdAndStatusInOrderByCreatedAtDesc(
                            plotId, List.of(SeasonStatus.PLANNED, SeasonStatus.ACTIVE))
                    .ifPresent(s -> {
                        s.setLockedAt(from);
                        seasonRepository.save(s);
                    });
        });
    }

    @Transactional
    public void onConsentChanged(UUID farmerId, boolean active) {
        MonitoringStatus next = active ? MonitoringStatus.ACTIVE : MonitoringStatus.PAUSED;
        for (PlotEntity p : plotRepository.findByFarmerId(farmerId)) {
            p.setMonitoringStatus(next);
            plotRepository.save(p);
            Map<String, Object> evt = Map.of(
                    "eventType", "plot.monitoring.changed",
                    "plotId", p.getPlotId(),
                    "monitoringStatus", next.name(),
                    "timestamp", Instant.now().toString());
            outboxService.enqueue(properties.getKafka().getPlotTopic(), "plot.monitoring.changed", p.getPlotId(), evt);
        }
    }

    private OverlapEvaluation evaluateOverlap(String selfPlotId, List<double[]> candidateRing, boolean includeOwn) {
        List<PlotEntity> existing = plotRepository.findByStatusIn(
                List.of(PlotStatus.REGISTERED, PlotStatus.HELD, PlotStatus.VALIDATED));
        double threshold = hookSdkClient.overlapThreshold(properties.getRules().getOverlapReviewPercent());
        double maxOverlap = 0.0;
        String band = "CLEAR";
        boolean insuredConflict = false;
        List<Map<String, Object>> matches = new ArrayList<>();

        for (PlotEntity other : existing) {
            if (selfPlotId != null && selfPlotId.equals(other.getPlotId())) continue;
            List<double[]> otherRing = GeoUtils.extractOuterRing(readPolygon(other.getGeometryGeojson()), 500);
            double pct = GeoUtils.computeOverlapPercent(candidateRing, otherRing, 2.0);
            if (pct >= properties.getRules().getOverlapIgnorePercent()) {
                maxOverlap = Math.max(maxOverlap, pct);
                if (pct >= threshold) {
                    boolean activeCov = other.getCoverageStatus() == CoverageStatus.ACTIVE;
                    if (activeCov) insuredConflict = true;
                    matches.add(Map.of(
                            "plotId", other.getPlotId(),
                            "farmerRef", other.getFarmerId().toString().substring(0, 8),
                            "overlapPercent", pct,
                            "insuredConflict", activeCov));
                }
            }
        }
        if (maxOverlap >= properties.getRules().getOverlapDuplicatePercent()) band = "DUPLICATE";
        else if (maxOverlap >= threshold) band = "OVERLAP";
        return new OverlapEvaluation(band, threshold, maxOverlap, insuredConflict, matches);
    }

    private GpsCheckResult checkGpsTrack(List<RegisterPlotRequest.GpsPoint> track) {
        boolean anomaly = false;
        List<Double> accs = new ArrayList<>();
        for (int i = 0; i < track.size(); i++) {
            accs.add(track.get(i).getAccuracyMeters());
            if (i > 0) {
                var prev = track.get(i - 1);
                var curr = track.get(i);
                double distM = GeoUtils.haversineMeters(prev.getLat(), prev.getLon(), curr.getLat(), curr.getLon());
                long sec = Math.max(1L, Duration.between(prev.getTimestamp(), curr.getTimestamp()).toSeconds());
                double kmh = (distM / 1000.0) / (sec / 3600.0);
                if (kmh > 8.0) anomaly = true;
            }
        }
        Collections.sort(accs);
        double medianAcc = accs.get(accs.size() / 2);
        if (medianAcc > 20.0) anomaly = true;
        var first = track.getFirst();
        var last = track.getLast();
        if (GeoUtils.haversineMeters(first.getLat(), first.getLon(), last.getLat(), last.getLon()) > 15.0) {
            anomaly = true;
        }
        return new GpsCheckResult(anomaly, medianAcc);
    }

    private void authorizePlotEdit(PlotIdentity actor, PlotEntity plot, String delegationHeader) {
        if (actor.hasRole(AuthRole.SUPPORT)) return;
        if (actor.userId() != null && actor.userId().equals(plot.getFarmerId())) return;
        if (delegationHeader != null && !delegationHeader.isBlank() && actor.userId() != null) {
            UUID delId = parseUuid(delegationHeader, "FORBIDDEN");
            if (authKycClient.validateDelegation(delId, actor.userId(), plot.getFarmerId(), DelegationScope.PLOT_REGISTER)) {
                return;
            }
        }
        throw new PlotApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Not authorised");
    }

    private void publishPlotFlagged(PlotEntity plot, OverlapEvaluation overlap, Instant now) {
        Map<String, Object> evt = new LinkedHashMap<>();
        evt.put("eventType", "plot.flagged");
        evt.put("plotId", plot.getPlotId());
        evt.put("flags", plot.getFlags().stream().map(Enum::name).toList());
        evt.put("priority", overlap.insuredConflict() || plot.getFlags().contains(PlotFlag.DUPLICATE) ? "HIGH" : "NORMAL");
        evt.put("insuredConflict", overlap.insuredConflict());
        evt.put("overlapBand", overlap.band());
        evt.put("timestamp", now.toString());
        outboxService.enqueue(properties.getKafka().getPlotTopic(), "plot.flagged", plot.getPlotId(), evt);
    }

    private String inferCountry(double lat, double lon) {
        if (lat >= -5.0 && lat <= 5.5 && lon >= 33.5 && lon <= 42.0) return "KE";
        if (lat >= -2.0 && lat <= 4.5 && lon >= 29.5 && lon <= 35.0) return "UG";
        return "KE";
    }

    private int parseIfMatch(String h) {
        try { return Integer.parseInt(h.replace("\"", "").trim()); }
        catch (NumberFormatException e) { throw new PlotApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid If-Match"); }
    }

    private UUID parseUuid(String raw, String code) {
        try { return UUID.fromString(raw.trim()); }
        catch (IllegalArgumentException e) {
            HttpStatus st = "FORBIDDEN".equals(code) ? HttpStatus.FORBIDDEN
                    : ("PLOT_NOT_FOUND".equals(code) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST);
            throw new PlotApiException(st, code, "Invalid identifier");
        }
    }

    private Optional<UUID> tryParseUuid(String raw) {
        if (raw == null) return Optional.empty();
        try { return Optional.of(UUID.fromString(raw.trim())); } catch (IllegalArgumentException e) { return Optional.empty(); }
    }

    private RegisterPlotRequest.GeoJsonPolygon readPolygon(String json) {
        try { return objectMapper.readValue(json, RegisterPlotRequest.GeoJsonPolygon.class); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readJsonMap(String json) {
        try { return objectMapper.readValue(json, Map.class); }
        catch (Exception e) { return Map.of(); }
    }

    private String writeJson(Object o) {
        try { return objectMapper.writeValueAsString(o); }
        catch (Exception e) { return "{}"; }
    }

    public byte[] toWkbBytes(String geoJson) {
        return geoJson.getBytes(StandardCharsets.UTF_8);
    }

    public RegisterPlotRequest.GeoJsonPolygon parseWkbBytes(byte[] wkb) {
        try {
            return objectMapper.readValue(new String(wkb, StandardCharsets.UTF_8), RegisterPlotRequest.GeoJsonPolygon.class);
        } catch (Exception e) {
            throw new PlotApiException(HttpStatus.BAD_REQUEST, "INVALID_GEOMETRY", "Invalid polygon WKB");
        }
    }

    private record GpsCheckResult(boolean anomaly, double medianAccuracy) {}
    private record OverlapEvaluation(String band, double thresholdApplied, double maxOverlapPercent,
                                     boolean insuredConflict, List<Map<String, Object>> matches) {}
    public record SpatialMatch(String plotId, String policyId, String farmerId, double areaHa,
                               double overlapPercent, String geohash6, String coverageFrom, String coverageTo) {}
    public record SpatialQueryResult(List<SpatialMatch> matches, double totalAreaHa,
                                     int pausedCount, int unvalidatedCount, int outsideCoverageCount) {}
}
