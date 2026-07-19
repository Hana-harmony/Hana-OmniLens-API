package com.hana.omniconnect.market.infra;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.hana.omniconnect.provider.disclosure.OpenDartDisclosureClient;

@Component
@Order(2)
public class OpenDartCorpCodeSyncRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OpenDartCorpCodeSyncRunner.class);

    private final JdbcStockMasterRepository repository;
    private final OpenDartDisclosureClient openDartDisclosureClient;

    public OpenDartCorpCodeSyncRunner(
            JdbcStockMasterRepository repository,
            OpenDartDisclosureClient openDartDisclosureClient) {
        this.repository = repository;
        this.openDartDisclosureClient = openDartDisclosureClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Map<String, String> corpCodeByStockCode = openDartDisclosureClient.listedCorpCodesByStockCode();
            int directUpdated = repository.updateDartCorpCodes(corpCodeByStockCode);
            int preferredUpdated = repository.updatePreferredShareDartCorpCodesFromCommonShares();
            log.info("OpenDART corp code synced: sourceCount={} directUpdatedCount={} preferredUpdatedCount={}",
                    corpCodeByStockCode.size(),
                    directUpdated,
                    preferredUpdated);
        } catch (RuntimeException exception) {
            // 공시 코드 보강 실패가 시세/뉴스 API 기동을 막지 않도록 기존 마스터를 유지한다.
            log.warn("OpenDART corp code sync skipped", exception);
        }
    }
}
