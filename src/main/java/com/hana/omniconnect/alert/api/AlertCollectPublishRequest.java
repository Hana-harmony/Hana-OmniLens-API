package com.hana.omniconnect.alert.api;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AlertCollectPublishRequest(
        @NotBlank @Size(max = 80) String partnerId,
        @NotEmpty @Size(max = 20) List<@Pattern(regexp = "\\d{6}") String> stockCodes,
        @Min(1) @Max(100) Integer newsDisplay,
        @Min(1) @Max(365) Integer disclosureLookbackDays
) {
    public int effectiveNewsDisplay() {
        return newsDisplay == null ? 5 : newsDisplay;
    }

    public int effectiveDisclosureLookbackDays() {
        return disclosureLookbackDays == null ? 365 : disclosureLookbackDays;
    }
}
