package com.hana.omniconnect.provider.market;

import java.net.URI;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.springframework.util.StringUtils;

import com.hana.omniconnect.config.ExternalProviderProperties;

public final class KisProviderSupport {

    private static final String VIRTUAL_TRADING_HOST_MARKER = "openapivts";
    private static final int VIRTUAL_TRADING_WEBSOCKET_PORT = 31_000;

    private KisProviderSupport() {
    }

    public static Optional<ExternalProviderProperties.Kis> realIndexRestProvider(
            ExternalProviderProperties properties) {
        Optional<ExternalProviderProperties.Kis> explicitRealProvider =
                usableProvider(properties.realKis(), KisProviderSupport::hasRestCredential, false);
        if (explicitRealProvider.isPresent()) {
            return explicitRealProvider.map(KisProviderSupport::withoutPreissuedTokenWhenRefreshable);
        }
        Optional<ExternalProviderProperties.Kis> primaryRealProvider =
                usableProvider(properties.kis(), KisProviderSupport::hasRestCredential, false);
        if (primaryRealProvider.isPresent()) {
            return primaryRealProvider.map(KisProviderSupport::withoutPreissuedTokenWhenRefreshable);
        }
        return Optional.empty();
    }

    public static Optional<ExternalProviderProperties.Kis> realIndexRealtimeProvider(
            ExternalProviderProperties properties) {
        Optional<ExternalProviderProperties.Kis> explicitRealProvider =
                usableProvider(properties.realKis(), KisProviderSupport::hasRealtimeCredential, true);
        if (explicitRealProvider.isPresent()) {
            return explicitRealProvider.map(KisProviderSupport::withoutPreissuedApprovalWhenRefreshable);
        }
        Optional<ExternalProviderProperties.Kis> primaryRealProvider =
                usableProvider(properties.kis(), KisProviderSupport::hasRealtimeCredential, true);
        if (primaryRealProvider.isPresent()) {
            return primaryRealProvider.map(KisProviderSupport::withoutPreissuedApprovalWhenRefreshable);
        }
        return Optional.empty();
    }

    public static boolean isVirtualRestProvider(ExternalProviderProperties.Kis properties) {
        return hostContains(properties.baseUrl(), VIRTUAL_TRADING_HOST_MARKER);
    }

    public static boolean isVirtualRealtimeProvider(ExternalProviderProperties.Kis properties) {
        return hostContains(properties.websocketUrl(), VIRTUAL_TRADING_HOST_MARKER)
                || properties.websocketUrl().getPort() == VIRTUAL_TRADING_WEBSOCKET_PORT;
    }

    public static boolean isSameProvider(
            ExternalProviderProperties.Kis first,
            ExternalProviderProperties.Kis second) {
        return first.baseUrl().equals(second.baseUrl())
                && first.websocketUrl().equals(second.websocketUrl())
                && safeText(first.accountNumber()).equals(safeText(second.accountNumber()))
                && safeText(first.appKey()).equals(safeText(second.appKey()))
                && safeText(first.appSecret()).equals(safeText(second.appSecret()))
                && safeText(first.accessToken()).equals(safeText(second.accessToken()))
                && safeText(first.approvalKey()).equals(safeText(second.approvalKey()));
    }

    public static boolean isSameRealtimeIdentity(
            ExternalProviderProperties.Kis first,
            ExternalProviderProperties.Kis second) {
        return first.baseUrl().equals(second.baseUrl())
                && first.websocketUrl().equals(second.websocketUrl())
                && safeText(first.accountNumber()).equals(safeText(second.accountNumber()))
                && safeText(first.appKey()).equals(safeText(second.appKey()))
                && safeText(first.appSecret()).equals(safeText(second.appSecret()));
    }

    private static Optional<ExternalProviderProperties.Kis> usableProvider(
            ExternalProviderProperties.Kis properties,
            Predicate<ExternalProviderProperties.Kis> credentialPredicate,
            boolean realtime) {
        return Stream.of(properties)
                .filter(kis -> kis != null && credentialPredicate.test(kis))
                .filter(kis -> realtime
                        ? !isVirtualRealtimeProvider(kis)
                        : !isVirtualRestProvider(kis))
                .findFirst();
    }

    private static ExternalProviderProperties.Kis withoutPreissuedTokenWhenRefreshable(
            ExternalProviderProperties.Kis properties) {
        if (!hasRestCredential(properties)) {
            return properties;
        }
        return new ExternalProviderProperties.Kis(
                properties.baseUrl(),
                properties.websocketUrl(),
                properties.accountNumber(),
                properties.appKey(),
                properties.appSecret(),
                "",
                properties.approvalKey());
    }

    private static ExternalProviderProperties.Kis withoutPreissuedApprovalWhenRefreshable(
            ExternalProviderProperties.Kis properties) {
        if (!hasRestCredential(properties)) {
            return properties;
        }
        return new ExternalProviderProperties.Kis(
                properties.baseUrl(),
                properties.websocketUrl(),
                properties.accountNumber(),
                properties.appKey(),
                properties.appSecret(),
                properties.accessToken(),
                "");
    }

    private static boolean hasRestCredential(ExternalProviderProperties.Kis properties) {
        return StringUtils.hasText(properties.appKey())
                && StringUtils.hasText(properties.appSecret());
    }

    private static boolean hasRealtimeCredential(ExternalProviderProperties.Kis properties) {
        return StringUtils.hasText(properties.approvalKey()) || hasRestCredential(properties);
    }

    private static boolean hostContains(URI uri, String marker) {
        String host = uri == null ? "" : uri.getHost();
        return host != null && host.toLowerCase(java.util.Locale.ROOT).contains(marker);
    }

    private static String safeText(String value) {
        return value == null ? "" : value;
    }
}
