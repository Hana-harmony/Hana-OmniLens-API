package com.hana.omnilens.alert.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.hana.omnilens.provider.translation.PapagoTranslationClient;

@Service
public class AlertTitleTranslationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertTitleTranslationService.class);

    private final PapagoTranslationClient papagoTranslationClient;

    public AlertTitleTranslationService(PapagoTranslationClient papagoTranslationClient) {
        this.papagoTranslationClient = papagoTranslationClient;
    }

    public String translateTitle(String originalTitle) {
        if (!StringUtils.hasText(originalTitle)) {
            return "";
        }
        try {
            String translatedTitle = papagoTranslationClient.translateKoToEn(originalTitle);
            if (StringUtils.hasText(translatedTitle)) {
                return translatedTitle;
            }
        } catch (RuntimeException exception) {
            // 번역 장애가 알림 발행을 막지 않도록 원문 제목으로 대체한다.
            LOGGER.warn("Alert title translation failed. Falling back to original title: {}",
                    exception.getClass().getSimpleName());
        }
        return originalTitle;
    }
}
