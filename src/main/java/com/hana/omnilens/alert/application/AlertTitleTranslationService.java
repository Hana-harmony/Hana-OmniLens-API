package com.hana.omnilens.alert.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.hana.omnilens.provider.translation.DeepLTranslationClient;

@Service
public class AlertTitleTranslationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertTitleTranslationService.class);

    private final DeepLTranslationClient deepLTranslationClient;

    public AlertTitleTranslationService(DeepLTranslationClient deepLTranslationClient) {
        this.deepLTranslationClient = deepLTranslationClient;
    }

    public String translateTitle(String originalTitle) {
        if (!StringUtils.hasText(originalTitle)) {
            return "";
        }
        String deepLTitle = translateWithDeepL(originalTitle);
        if (StringUtils.hasText(deepLTitle)) {
            return deepLTitle;
        }
        return originalTitle;
    }

    private String translateWithDeepL(String originalTitle) {
        try {
            return deepLTranslationClient.translateKoToEn(originalTitle);
        } catch (RuntimeException exception) {
            LOGGER.warn("DeepL alert title translation failed. Falling back to original title: {}",
                    exception.getClass().getSimpleName());
            return "";
        }
    }
}
