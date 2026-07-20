package com.hana.omniconnect.portal;

public record PortalApiKeyApplicationView(
        PortalApiKeyApplication application,
        String applicantUsername,
        String applicantName
) {
}
