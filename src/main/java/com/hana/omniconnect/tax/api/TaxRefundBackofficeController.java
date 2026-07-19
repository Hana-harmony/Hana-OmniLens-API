package com.hana.omniconnect.tax.api;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hana.omniconnect.common.api.ApiResponse;
import com.hana.omniconnect.tax.refund.TaxRefundBackofficeService;
import com.hana.omniconnect.tax.refund.TaxRefundCaseSyncRequest;
import com.hana.omniconnect.tax.refund.TaxRefundCaseSyncResponse;

@RestController
@RequestMapping("/api/v1/tax/refund-cases")
public class TaxRefundBackofficeController {
    private final TaxRefundBackofficeService service;
    public TaxRefundBackofficeController(TaxRefundBackofficeService service) { this.service = service; }
    @PostMapping("/sync")
    public ApiResponse<TaxRefundCaseSyncResponse> sync(@Valid @RequestBody TaxRefundCaseSyncRequest request) { return ApiResponse.success(service.sync(request)); }
}
