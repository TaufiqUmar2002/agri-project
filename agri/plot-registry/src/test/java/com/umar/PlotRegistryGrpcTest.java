package com.umar;

import com.google.protobuf.ByteString;
import com.umar.enums.plot.AccuracyClass;
import com.umar.enums.plot.CaptureMethod;
import com.umar.enums.plot.CoverageStatus;
import com.umar.enums.plot.MonitoringStatus;
import com.umar.enums.plot.PlotStatus;
import com.umar.grpc.plot.v1.*;
import com.umar.plot.api.grpc.PlotGrpcServer;
import com.umar.plot.domain.entity.PlotCoverageWindowEntity;
import com.umar.plot.domain.entity.PlotEntity;
import com.umar.plot.repository.PlotCoverageWindowRepository;
import com.umar.plot.repository.PlotRepository;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = PlotRegistryApplication.class)
@ActiveProfiles("test")
class PlotRegistryGrpcTest {

    @Autowired private PlotGrpcServer grpcServer;
    @Autowired private PlotRepository plotRepository;
    @Autowired private PlotCoverageWindowRepository coverageWindowRepository;

    private PlotServiceGrpc.PlotServiceBlockingStub stub(ManagedChannel ch, String caller) {
        Metadata headers = new Metadata();
        if (caller != null) headers.put(Metadata.Key.of("x-service-identity", Metadata.ASCII_STRING_MARSHALLER), caller);
        return PlotServiceGrpc.newBlockingStub(ch).withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    @Test
    void plt08_plt09_plt10_listStreamAndDetails() {
        UUID farmerId = UUID.randomUUID();
        Instant now = Instant.now();
        String geoJson = "{\"type\":\"Polygon\",\"coordinates\":[[[36.950,-0.417],[36.952,-0.417],[36.952,-0.415],[36.950,-0.415],[36.950,-0.417]]]}";
        String plotId = "pl_grpc_1";
        plotRepository.save(PlotEntity.builder()
                .plotId(plotId)
                .farmerId(farmerId)
                .name("Stream Field")
                .status(PlotStatus.VALIDATED)
                .captureMethod(CaptureMethod.MAP_DRAW)
                .accuracyClass(AccuracyClass.HIGH)
                .geometryGeojson(geoJson)
                .geometryVersion(1)
                .geometryHash("hash123")
                .areaHa(4.92)
                .minWidthM(220.0)
                .centroidLat(-0.416)
                .centroidLon(36.951)
                .bbox("36.95,-0.417,36.952,-0.415")
                .geohash5("kzxm3")
                .geohash7("kzxm3qh")
                .country("KE")
                .district("NYERI")
                .ward("MWEIGA")
                .coverageStatus(CoverageStatus.ACTIVE)
                .monitoringStatus(MonitoringStatus.ACTIVE)
                .secondReviewPending(false)
                .createdAt(now)
                .validatedAt(now)
                .build());
        coverageWindowRepository.save(PlotCoverageWindowEntity.builder()
                .windowId(UUID.randomUUID())
                .plotId(plotId)
                .policyId("pol_grpc_1")
                .validFrom(now.minus(5, ChronoUnit.DAYS))
                .validTo(now.plus(60, ChronoUnit.DAYS))
                .status(CoverageStatus.ACTIVE)
                .build());

        ManagedChannel ch = NettyChannelBuilder.forAddress("localhost", grpcServer.getBoundPort()).usePlaintext().build();
        try {
            ListFarmerPlotsResponse list = stub(ch, "policy-service").listFarmerPlots(
                    ListFarmerPlotsRequest.newBuilder().setFarmerId(farmerId.toString()).build());
            assertThat(list.getPlotsList()).hasSize(1);
            assertThat(list.getPlots(0).getPlotId()).isEqualTo(plotId);

            // Policy service cannot call StreamPlotsWithin
            String eventPoly = "{\"type\":\"Polygon\",\"coordinates\":[[[36.949,-0.418],[36.953,-0.418],[36.953,-0.414],[36.949,-0.414],[36.949,-0.418]]]}";
            assertThatThrownBy(() -> stub(ch, "policy-service").streamPlotsWithin(
                    StreamPlotsWithinRequest.newBuilder()
                            .setPolygonWkb(ByteString.copyFrom(eventPoly.getBytes(StandardCharsets.UTF_8)))
                            .build()).next())
                    .isInstanceOf(StatusRuntimeException.class)
                    .extracting(e -> ((StatusRuntimeException) e).getStatus().getCode())
                    .isEqualTo(Status.Code.PERMISSION_DENIED);

            // Trigger engine streams batches + summary checksum
            Iterator<StreamPlotsWithinResponse> stream = stub(ch, "trigger-engine").streamPlotsWithin(
                    StreamPlotsWithinRequest.newBuilder()
                            .setPolygonWkb(ByteString.copyFrom(eventPoly.getBytes(StandardCharsets.UTF_8)))
                            .setMatchMode(GrpcMatchMode.INTERSECTS)
                            .setBatchSize(100)
                            .build());
            List<StreamPlotsWithinResponse> msgs = new ArrayList<>();
            stream.forEachRemaining(msgs::add);
            assertThat(msgs).isNotEmpty();
            StreamSummaryMessage summary = msgs.getLast().getSummary();
            assertThat(summary.getMatchedCount()).isGreaterThanOrEqualTo(1);
            assertThat(summary.getChecksum()).isNotBlank();

            GetPlotDetailsResponse details = stub(ch, "trigger-engine").getPlotDetails(
                    GetPlotDetailsRequest.newBuilder()
                            .addPlotIds(plotId)
                            .addPlotIds("pl_missing")
                            .setPurpose(GrpcGeometryPurpose.TRIGGER_EVALUATION)
                            .setIncludeGeometry(true)
                            .build());
            assertThat(details.getPlotsList()).hasSize(1);
            assertThat(details.getPlots(0).getGeometryHash()).isEqualTo("hash123");
            assertThat(details.getNotFoundIdsList()).containsExactly("pl_missing");
        } finally {
            ch.shutdownNow();
        }
    }
}
