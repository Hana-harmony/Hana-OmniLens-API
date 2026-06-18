package com.hana.omnilens.provider;

public class ProviderCircuitOpenException extends RuntimeException {

    public ProviderCircuitOpenException(String providerName) {
        super("External provider circuit is open: " + providerName);
    }
}
