package com.hana.omnilens.provider.disclosure;

public record OpenDartDisclosureDocument(
        String content,
        String contentHash,
        String sourceLicensePolicy
) {
}
