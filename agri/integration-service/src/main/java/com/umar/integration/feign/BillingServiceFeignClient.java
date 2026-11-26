package com.umar.integration.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "premium-billing", url = "${agreeshield.services.billing.url:http://localhost:8085}", contextId = "billingServiceFeignClient")
public interface BillingServiceFeignClient {
    @GetMapping("/api/billing/invoices/{invoiceId}")
    Map<String, Object> getInvoice(@PathVariable("invoiceId") String invoiceId);
}
