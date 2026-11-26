package com.umar.integration.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(
        name = "plot-registry",
        url = "${agreeshield.services.plot.url:http://localhost:8083}",
        contextId = "plotRegistryFeignClient"
)
public interface PlotRegistryFeignClient {
    @GetMapping("/api/plots/{plotId}")
    Map<String, Object> getPlot(@PathVariable("plotId") String plotId);
}
