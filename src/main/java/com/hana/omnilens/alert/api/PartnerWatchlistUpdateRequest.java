package com.hana.omnilens.alert.api;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PartnerWatchlistUpdateRequest(
        @NotNull @Size(max = 100) List<@NotBlank @Pattern(regexp = "\\d{6}") String> stockCodes
) {
}
