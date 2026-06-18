package com.hana.omnilens.alert.application;

public interface AlertDedupeStore {

    boolean markIfFirst(String key);

    void remove(String key);
}
