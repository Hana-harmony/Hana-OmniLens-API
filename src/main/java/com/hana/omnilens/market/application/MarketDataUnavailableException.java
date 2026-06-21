package com.hana.omnilens.market.application;

import com.hana.omnilens.common.exception.BusinessException;
import com.hana.omnilens.common.exception.ErrorCode;

public class MarketDataUnavailableException extends BusinessException {

    public MarketDataUnavailableException(String message) {
        super(ErrorCode.MARKET_DATA_UNAVAILABLE, message);
    }
}
