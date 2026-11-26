package com.umar.plot.api.rest;

import com.umar.payload.request.plot.*;
import com.umar.payload.response.plot.*;
import com.umar.plot.service.PlotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/plots")
@RequiredArgsConstructor
public class PlotController {

    private final PlotService plotService;

    @PostMapping
    public ResponseEntity<PlotResponse> registerPlot(
            @RequestHeader(value = "X-Agent-Delegation", required = false) String delegationHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RegisterPlotRequest request) {
        PlotResponse resp = plotService.registerPlot(delegationHeader, idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .eTag("\"" + resp.getVersion() + "\"")
                .body(resp);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlotResponse> getPlot(
            @PathVariable("id") String plotId,
            @RequestParam(value = "geometry", required = false) String geometry,
            @RequestParam(value = "include", required = false) String include,
            @RequestHeader(value = "X-Agent-Delegation", required = false) String delegationHeader) {
        PlotResponse resp = plotService.getPlot(plotId, geometry, include, delegationHeader);
        return ResponseEntity.ok()
                .eTag("\"" + resp.getVersion() + "\"")
                .body(resp);
    }

    @PutMapping("/{id}/season")
    public ResponseEntity<RecordSeasonResponse> recordSeason(
            @PathVariable("id") String plotId,
            @RequestHeader("If-Match") String ifMatch,
            @RequestHeader(value = "X-Agent-Delegation", required = false) String delegationHeader,
            @Valid @RequestBody RecordSeasonRequest request) {
        RecordSeasonResponse resp = plotService.recordSeason(plotId, ifMatch, delegationHeader, request);
        return ResponseEntity.ok()
                .eTag("\"" + resp.getVersion() + "\"")
                .body(resp);
    }

    @PostMapping("/overlap-check")
    public ResponseEntity<OverlapCheckResponse> overlapCheck(
            @RequestHeader(value = "X-Agent-Delegation", required = false) String delegationHeader,
            @RequestBody OverlapCheckRequest request) {
        return ResponseEntity.ok(plotService.checkOverlap(delegationHeader, request));
    }

    @PostMapping("/within")
    public ResponseEntity<PlotsWithinResponse> findPlotsWithin(@Valid @RequestBody PlotsWithinRequest request) {
        return ResponseEntity.ok(plotService.findPlotsWithin(request));
    }

    @PostMapping("/{id}/validate")
    public ResponseEntity<ValidatePlotResponse> validatePlot(
            @PathVariable("id") String plotId,
            @RequestBody(required = false) ValidatePlotRequest request) {
        return ResponseEntity.ok(plotService.validatePlot(plotId, request));
    }

    @PutMapping("/{id}/review")
    public ResponseEntity<ReviewPlotResponse> reviewHeldPlot(
            @PathVariable("id") String plotId,
            @Valid @RequestBody ReviewPlotRequest request) {
        return ResponseEntity.ok(plotService.reviewHeldPlot(plotId, request));
    }
}
