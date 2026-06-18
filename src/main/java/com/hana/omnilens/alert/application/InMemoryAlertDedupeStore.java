package com.hana.omnilens.alert.application;

import java.util.LinkedHashMap;
import java.util.Map;

public class InMemoryAlertDedupeStore implements AlertDedupeStore {

    private final int maxEntries;
    private final Map<String, Boolean> keys;

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
}
