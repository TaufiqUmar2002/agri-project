package com.umar.plot.api.grpc;

import com.google.protobuf.ByteString;
import com.umar.enums.plot.CoverageStatus;
import com.umar.enums.plot.MatchMode;
import com.umar.enums.plot.PlotStatus;
import com.umar.grpc.plot.v1.*;
import com.umar.payload.request.plot.RegisterPlotRequest;
import com.umar.plot.config.PlotProperties;
import com.umar.plot.domain.entity.PlotEntity;
import com.umar.plot.geo.GeoUtils;
import com.umar.plot.repository.PlotCoverageWindowRepository;
import com.umar.plot.repository.PlotRepository;
import com.umar.plot.repository.PlotSeasonRepository;
import com.umar.plot.service.PlotService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PlotGrpcServiceImpl extends PlotServiceGrpc.PlotServiceImplBase {

    private final PlotService plotService;
    private final PlotRepository plotRepository;
    private final PlotSeasonRepository seasonRepository;
    private final PlotCoverageWindowRepository coverageWindowRepository;
    private final PlotProperties properties;

    @Override
    public void listFarmerPlots(ListFarmerPlotsRequest request, StreamObserver<ListFarmerPlotsResponse> responseObserver) {
        try {
            UUID farmerId = UUID.fromString(request.getFarmerId().trim());
            Set<PlotStatus> statuses = EnumSet.noneOf(PlotStatus.class);
            for (GrpcPlotStatus s : request.getStatusesList()) {
                if (s != GrpcPlotStatus.PLOT_STATUS_UNSPECIFIED && s != GrpcPlotStatus.UNRECOGNIZED) {
                    statuses.add(PlotStatus.valueOf(s.name()));
                }
            }
            CoverageStatus cov = switch (request.getCoverage()) {
                case COVERAGE_NONE -> CoverageStatus.NONE;
                case COVERAGE_ACTIVE -> CoverageStatus.ACTIVE;
                case COVERAGE_LAPSED -> CoverageStatus.LAPSED;
                default -> null;
            };
            List<PlotEntity> plots = plotService.listFarmerPlots(farmerId, statuses, cov, request.getIncludeRetired());

            ListFarmerPlotsResponse.Builder b = ListFarmerPlotsResponse.newBuilder()
                    .setAsOf(Instant.now().toString());
            for (PlotEntity p : plots) {
                PlotSummaryItem.Builder item = PlotSummaryItem.newBuilder()
                        .setPlotId(p.getPlotId())
                        .setName(p.getName() != null ? p.getName() : "")
                        .setStatus(GrpcPlotStatus.valueOf(p.getStatus().name()))
                        .setAreaHa(p.getAreaHa())
                        .setAccuracyClass(p.getAccuracyClass().name())
                        .setCoverageStatus(p.getCoverageStatus().name())
                        .setMonitoringStatus(p.getMonitoringStatus().name())
                        .setLocationCell(p.getGeohash5());
                seasonRepository.findFirstByPlotIdOrderByCreatedAtDesc(p.getPlotId()).ifPresent(s -> item
                        .setCropCode(s.getCropCode())
                        .setPlantingDate(s.getPlantingDate().toString())
                        .setExpectedHarvestDate(s.getExpectedHarvestDate().toString()));
                b.addPlots(item.build());
            }
            responseObserver.onNext(b.build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("Invalid farmer_id").asRuntimeException());
        }
    }

    @Override
    public void streamPlotsWithin(StreamPlotsWithinRequest request, StreamObserver<StreamPlotsWithinResponse> responseObserver) {
        try {
            String caller = PlotGrpcAuthInterceptor.CALLER_CTX_KEY.get();
            if (properties.getSecurity().getStreamConsumers().stream().noneMatch(c -> c.equalsIgnoreCase(caller))) {
                responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Stream limited to Trigger Engine and Risk Pool").asRuntimeException());
                return;
            }
            RegisterPlotRequest.GeoJsonPolygon poly = plotService.parseWkbBytes(request.getPolygonWkb().toByteArray());
            List<double[]> ring = GeoUtils.extractOuterRing(poly, 200_000);
            Instant asOf = request.getAsOf().isBlank() ? Instant.now() : Instant.parse(request.getAsOf());
            MatchMode mode = switch (request.getMatchMode()) {
                case CENTROID_WITHIN -> MatchMode.CENTROID_WITHIN;
                case MIN_OVERLAP -> MatchMode.MIN_OVERLAP;
                default -> MatchMode.INTERSECTS;
            };
            double minOverlap = request.getMinOverlapPercent() > 0 ? request.getMinOverlapPercent() : 1.0;

            PlotService.SpatialQueryResult res = plotService.querySpatialMatches(
                    ring, asOf, mode, minOverlap, true, request.getCropCodesList(), request.getRegionCodesList());

            int batchSize = request.getBatchSize() > 0 ? Math.min(10_000, Math.max(100, request.getBatchSize())) : 5000;
            List<PlotService.SpatialMatch> matches = res.matches();
            StringBuilder checksumInput = new StringBuilder();
            for (int i = 0; i < matches.size(); i += batchSize) {
                int end = Math.min(i + batchSize, matches.size());
                StreamBatchMessage.Builder batch = StreamBatchMessage.newBuilder()
                        .setResumeToken("rt_" + end);
                for (int j = i; j < end; j++) {
                    PlotService.SpatialMatch m = matches.get(j);
                    checksumInput.append(m.plotId()).append(';');
                    batch.addMatches(StreamPlotMatch.newBuilder()
                            .setPlotId(m.plotId())
                            .setPolicyId(m.policyId())
                            .setFarmerId(m.farmerId())
                            .setAreaHa(m.areaHa())
                            .setOverlapPercent(m.overlapPercent())
                            .setLocationCell6(m.geohash6())
                            .setCoverageFrom(m.coverageFrom())
                            .setCoverageTo(m.coverageTo())
                            .build());
                }
                responseObserver.onNext(StreamPlotsWithinResponse.newBuilder().setBatch(batch.build()).build());
            }

            responseObserver.onNext(StreamPlotsWithinResponse.newBuilder()
                    .setSummary(StreamSummaryMessage.newBuilder()
                            .setMatchedCount(matches.size())
                            .setTotalAreaHa(Math.round(res.totalAreaHa() * 100.0) / 100.0)
                            .setPausedCount(res.pausedCount())
                            .setUnvalidatedCount(res.unvalidatedCount())
                            .setOutsideCoverageCount(res.outsideCoverageCount())
                            .setChecksum(GeoUtils.sha256Hex(checksumInput.toString()))
                            .setSnapshotTime(asOf.toString())
                            .setRuleSetVersion(properties.getRules().getCurrentVersion())
                            .build())
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void getPlotDetails(GetPlotDetailsRequest request, StreamObserver<GetPlotDetailsResponse> responseObserver) {
        if (request.getPlotIdsCount() == 0 || request.getPlotIdsCount() > 100) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription("1 to 100 plot_ids required").asRuntimeException());
            return;
        }
        if (request.getPurpose() == GrpcGeometryPurpose.PURPOSE_UNSPECIFIED || request.getPurpose() == GrpcGeometryPurpose.UNRECOGNIZED) {
            responseObserver.onError(Status.PERMISSION_DENIED.withDescription("Registered purpose required").asRuntimeException());
            return;
        }
        GetPlotDetailsResponse.Builder b = GetPlotDetailsResponse.newBuilder().setAsOf(Instant.now().toString());
        for (String id : request.getPlotIdsList()) {
            Optional<PlotEntity> opt = plotRepository.findById(id);
            if (opt.isEmpty()) {
                b.addNotFoundIds(id);
                continue;
            }
            PlotEntity p = opt.get();
            PlotDetailItem.Builder item = PlotDetailItem.newBuilder()
                    .setPlotId(p.getPlotId())
                    .setFarmerId(p.getFarmerId().toString())
                    .setStatus(GrpcPlotStatus.valueOf(p.getStatus().name()))
                    .setAreaHa(p.getAreaHa())
                    .setAccuracyClass(p.getAccuracyClass().name())
                    .setCentroid(LatLon.newBuilder().setLat(p.getCentroidLat()).setLon(p.getCentroidLon()).build())
                    .setBbox(p.getBbox())
                    .setGeohash7(p.getGeohash7())
                    .setGeometryVersion(p.getGeometryVersion())
                    .setGeometryHash(p.getGeometryHash());
            if (request.getIncludeGeometry()) {
                item.setGeometryWkb(ByteString.copyFrom(plotService.toWkbBytes(p.getGeometryGeojson())));
            }
            seasonRepository.findFirstByPlotIdOrderByCreatedAtDesc(p.getPlotId()).ifPresent(s ->
                    item.setSeason(PlotSeasonDetail.newBuilder()
                            .setCropCode(s.getCropCode())
                            .setPlantingDate(s.getPlantingDate().toString())
                            .setExpectedHarvestDate(s.getExpectedHarvestDate().toString())
                            .setIrrigationType(s.getIrrigationType().name())
                            .setSeasonStatus(s.getStatus().name())
                            .build()));
            coverageWindowRepository.findByPlotId(p.getPlotId()).forEach(w ->
                    item.addCoverageWindows(PlotCoverageWindowMsg.newBuilder()
                            .setPolicyId(w.getPolicyId())
                            .setFrom(w.getValidFrom().toString())
                            .setTo(w.getValidTo().toString())
                            .build()));
            b.addPlots(item.build());
        }
        responseObserver.onNext(b.build());
        responseObserver.onCompleted();
    }
}
