package com.umar.trigger.grpc;

import com.umar.trigger.config.TriggerProperties;
import com.umar.trigger.domain.SimulationRunEntity;
import com.umar.trigger.domain.TriggerDecisionEntity;
import com.umar.trigger.grpc.proto.*;
import com.umar.trigger.repository.SimulationRunRepository;
import com.umar.trigger.repository.TriggerDecisionRepository;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TriggerGrpcServiceImpl extends TriggerServiceGrpc.TriggerServiceImplBase {

    private final TriggerDecisionRepository decisionRepository;
    private final SimulationRunRepository simulationRepository;
    private final TriggerProperties properties;

    @Override
    public void getDecisionStatus(GetDecisionStatusRequest req, StreamObserver<GetDecisionStatusResponse> observer) {
        String caller = TriggerGrpcAuthInterceptor.CALLER_ID.get();
        List<String> allowed = Arrays.stream(properties.getGrpc().getStatusAllowedCallers().split(","))
                .map(String::trim).map(String::toLowerCase).toList();
        if (caller == null || !allowed.contains(caller)) {
            observer.onError(Status.PERMISSION_DENIED.withDescription("Caller not allowed").asRuntimeException());
            return;
        }
        if (req.getDecisionIdsCount() > 200) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription("Maximum 200 decision_ids allowed").asRuntimeException());
            return;
        }

        List<DecisionStatusEntry> foundEntries = new ArrayList<>();
        List<String> notFound = new ArrayList<>();

        if (req.getDecisionIdsCount() > 0) {
            Map<String, TriggerDecisionEntity> byId = new HashMap<>();
            decisionRepository.findByDecisionIdIn(req.getDecisionIdsList())
                    .forEach(d -> byId.put(d.getDecisionId(), d));
            for (String id : req.getDecisionIdsList()) {
                TriggerDecisionEntity d = byId.get(id);
                if (d == null) {
                    notFound.add(id);
                } else {
                    foundEntries.add(toEntry(d));
                }
            }
        } else if (req.hasPolicyWindow() && !req.getPolicyWindow().getPolicyId().isBlank()) {
            PolicyWindowLookup pw = req.getPolicyWindow();
            LocalDate ws = LocalDate.parse(pw.getWindowStart());
            LocalDate we = LocalDate.parse(pw.getWindowEnd());
            decisionRepository.findByPolicyIdAndWindowStartAndWindowEnd(pw.getPolicyId(), ws, we)
                    .ifPresent(d -> foundEntries.add(toEntry(d)));
        } else {
            observer.onError(Status.INVALID_ARGUMENT.withDescription("Provide decision_ids or policy_window").asRuntimeException());
            return;
        }

        observer.onNext(GetDecisionStatusResponse.newBuilder()
                .addAllDecisions(foundEntries)
                .addAllNotFoundIds(notFound)
                .setAsOf(Instant.now().toString())
                .build());
        observer.onCompleted();
    }

    @Override
    public void getSimulationSummary(GetSimulationSummaryRequest req, StreamObserver<GetSimulationSummaryResponse> observer) {
        String caller = TriggerGrpcAuthInterceptor.CALLER_ID.get();
        List<String> allowed = Arrays.stream(properties.getGrpc().getSimulationAllowedCallers().split(","))
                .map(String::trim).map(String::toLowerCase).toList();
        if (caller == null || !allowed.contains(caller)) {
            observer.onError(Status.PERMISSION_DENIED.withDescription("Caller not allowed").asRuntimeException());
            return;
        }
        if (req.getSimulationId().isBlank() || req.getDefinitionHash().isBlank()) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription("simulation_id and definition_hash required").asRuntimeException());
            return;
        }
        SimulationRunEntity sim = simulationRepository.findById(req.getSimulationId()).orElse(null);
        if (sim == null) {
            observer.onError(Status.NOT_FOUND.withDescription("Simulation not found").asRuntimeException());
            return;
        }

        boolean matchesDef = sim.getDefinitionHash().equals(req.getDefinitionHash());
        boolean notExpired = !sim.getValidUntil().isBefore(LocalDate.now());
        boolean enoughSeasons = sim.getSeasonsCovered() >= properties.getRules().getMinSimulationSeasons();
        List<String> reasons = new ArrayList<>();
        if (!matchesDef) reasons.add("DEFINITION_MISMATCH");
        if (!notExpired) reasons.add("SIMULATION_EXPIRED");
        if (!enoughSeasons) reasons.add("INSUFFICIENT_SEASONS");
        boolean valid = matchesDef && notExpired && enoughSeasons;

        observer.onNext(GetSimulationSummaryResponse.newBuilder()
                .setValid(valid)
                .addAllReasons(reasons)
                .setMatchesDefinition(matchesDef)
                .setSeasonsCovered(sim.getSeasonsCovered())
                .addAllRegions(Arrays.asList(sim.getRegionCodesCsv().split(",")))
                .setExpectedLossRatio(sim.getExpectedLossRatio())
                .setBurningCostPercent(sim.getBurningCostPercent())
                .setDataCoverage(sim.getDataCoveragePercent())
                .setValidUntil(sim.getValidUntil().toString())
                .build());
        observer.onCompleted();
    }

    private DecisionStatusEntry toEntry(TriggerDecisionEntity d) {
        return DecisionStatusEntry.newBuilder()
                .setDecisionId(d.getDecisionId())
                .setPolicyId(d.getPolicyId())
                .setOutcome(d.getOutcome().name())
                .setStatus(d.getStatus().name())
                .setTierId(d.getTierId() != null ? d.getTierId() : "")
                .setPayoutPercent(d.getPayoutPercent())
                .setEvidenceHash(d.getEvidenceHash())
                .setDecidedAt(d.getDecidedAt().toString())
                .build();
    }
}
