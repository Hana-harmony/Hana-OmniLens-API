package com.hana.omnilens.alert.application;

import java.time.Duration;
import java.util.Optional;

public interface AlertDedupeStore {

    boolean markIfFirst(String key);

    void remove(String key);

    Optional<String> acquireLease(String key, Duration leaseDuration);

    void releaseLease(String key, String leaseToken);
}
