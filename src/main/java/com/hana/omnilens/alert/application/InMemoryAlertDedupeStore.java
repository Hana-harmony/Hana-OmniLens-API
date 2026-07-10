package com.hana.omnilens.alert.application;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryAlertDedupeStore implements AlertDedupeStore {

    private final int maxEntries;
    private final Map<String, Boolean> keys;
    private final Map<String, String> leases = new LinkedHashMap<>();

    public InMemoryAlertDedupeStore(int maxEntries) {
        this.maxEntries = maxEntries;
        this.keys = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > InMemoryAlertDedupeStore.this.maxEntries;
            }
        };
    }

    @Override
    public synchronized boolean markIfFirst(String key) {
        // Redis 장애나 테스트 환경에서도 같은 프로세스 내 중복 발행을 제한한다.
        if (keys.containsKey(key)) {
            return false;
        }
        keys.put(key, true);
        return true;
    }

    @Override
    public synchronized void remove(String key) {
        keys.remove(key);
    }

    @Override
    public synchronized Optional<String> acquireLease(String key, Duration leaseDuration) {
        if (leases.containsKey(key)) {
            return Optional.empty();
        }
        String token = UUID.randomUUID().toString();
        leases.put(key, token);
        return Optional.of(token);
    }

    @Override
    public synchronized void releaseLease(String key, String leaseToken) {
        if (leaseToken != null && leaseToken.equals(leases.get(key))) {
            leases.remove(key);
        }
    }
}
