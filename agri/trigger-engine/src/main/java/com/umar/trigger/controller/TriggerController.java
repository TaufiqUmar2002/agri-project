package com.umar.trigger.controller;

import com.umar.payload.request.trigger.*;
import com.umar.payload.response.trigger.*;
import com.umar.trigger.service.TriggerEvaluationService;
import com.umar.trigger.service.TriggerRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/triggers")
@RequiredArgsConstructor
public class TriggerController {

    private final TriggerRuleService ruleService;
    private final TriggerEvaluationService evaluationService;

    @PostMapping("/rules")
    public ResponseEntity<TriggerRuleResponse> createRule(@Valid @RequestBody CreateTriggerRuleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ruleService.createRule(req));
    }

    @PutMapping("/rules/{ruleId}/versions/{version}/decision")
    public ResponseEntity<TriggerRuleResponse> decideRule(
            @PathVariable String ruleId,
            @PathVariable int version,
            @Valid @RequestBody RuleDecisionRequest req) {
        return ResponseEntity.ok(ruleService.decideRule(ruleId, version, req));
    }

    @PostMapping("/evaluate")
    public ResponseEntity<TriggerEvaluateResponse> evaluate(@Valid @RequestBody EvaluateTriggerRequest req) {
        return ResponseEntity.ok(evaluationService.evaluate(req));
    }

    @GetMapping("/decisions/{id}")
    public ResponseEntity<TriggerDecisionDetailResponse> getDecision(
            @PathVariable String id,
            @RequestParam(required = false) String include,
            @RequestHeader(value = "X-Delegation-Id", required = false) String delegationHeader) {
        TriggerDecisionDetailResponse resp = evaluationService.getDecision(id, include, delegationHeader);
        return ResponseEntity.ok().eTag("\"" + resp.getVersion() + "\"").body(resp);
    }

    @PutMapping("/decisions/{id}/review")
    public ResponseEntity<TriggerDecisionDetailResponse> reviewDecision(
            @PathVariable String id,
            @Valid @RequestBody ReviewTriggerDecisionRequest req) {
        return ResponseEntity.ok(evaluationService.reviewDecision(id, req));
    }

    @GetMapping("/events")
    public ResponseEntity<TriggerEventsResponse> listEvents(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String peril,
            @RequestParam(required = false) String ruleId,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) Integer minAffected,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(evaluationService.listEvents(status, peril, ruleId, regionCode, minAffected, page, size));
    }

    @PostMapping("/simulate")
    public ResponseEntity<SimulationResponse> simulate(@Valid @RequestBody RunSimulationRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ruleService.runSimulation(req));
    }

    @GetMapping("/simulations/{id}")
    public ResponseEntity<SimulationResponse> getSimulation(
            @PathVariable String id,
            @RequestParam(required = false) String include,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        if ("\"1\"".equals(ifNoneMatch) || "1".equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
        return ResponseEntity.ok().eTag("\"1\"").body(ruleService.getSimulation(id, include));
    }
}
