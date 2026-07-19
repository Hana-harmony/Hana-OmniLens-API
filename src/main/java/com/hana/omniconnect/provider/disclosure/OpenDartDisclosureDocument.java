package com.hana.omniconnect.provider.disclosure;

public record OpenDartDisclosureDocument(
        String content,
        String contentHash,
        String sourceLicensePolicy
) {
}
