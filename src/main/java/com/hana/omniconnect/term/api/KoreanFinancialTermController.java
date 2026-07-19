package com.hana.omniconnect.term.api;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hana.omniconnect.common.api.ApiResponse;
import com.hana.omniconnect.term.application.KoreanFinancialTermExplanationService;
import com.hana.omniconnect.term.domain.KoreanFinancialTermClickStat;
import com.hana.omniconnect.term.domain.KoreanFinancialTermExplanation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Validated
@RestController
@RequestMapping("/api/v1/korean-financial-terms")
@Tag(name = "Korean Financial Terms", description = "Korean financial term explanation APIs")
public class KoreanFinancialTermController {

    private final KoreanFinancialTermExplanationService explanationService;

    public KoreanFinancialTermController(KoreanFinancialTermExplanationService explanationService) {
        this.explanationService = explanationService;
    }

    @PostMapping("/explain")
    @Operation(summary = "Explain clicked Korean financial term and record analytics")
    public ApiResponse<KoreanFinancialTermExplanation> explain(
            @Valid @RequestBody KoreanFinancialTermExplainRequest request) {
        return ApiResponse.success(explanationService.explain(request));
    }

    @GetMapping("/stats")
    @Operation(summary = "List Korean financial term click statistics")
    public ApiResponse<List<KoreanFinancialTermClickStat>> stats(
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        return ApiResponse.success(explanationService.stats(limit));
    }
}
