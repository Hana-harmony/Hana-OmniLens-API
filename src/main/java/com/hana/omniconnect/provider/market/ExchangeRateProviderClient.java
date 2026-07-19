package com.hana.omniconnect.provider.market;

import java.time.LocalDate;
import java.util.Optional;

public interface ExchangeRateProviderClient {

    Optional<ProviderExchangeRateSnapshot> findKrwToLocalRate(String localCurrency, LocalDate baseDate);
}
