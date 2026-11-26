package com.umar.plot.client;

import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LocalHookSdkClient implements HookSdkClient {

    @Setter private volatile PreRegisterOutcome preRegisterOutcome = PreRegisterOutcome.ALLOW;

    @Override
    public PreRegisterOutcome preRegister(String farmerId) { return preRegisterOutcome; }

    @Override
    public Map<String, Object> enrich(String plotId) { return Map.of(); }

    @Override
    public double overlapThreshold(double platformDefaultPercent) { return platformDefaultPercent; }

    @Override
    public void postValidate(String plotId, String outcome) {}
}
