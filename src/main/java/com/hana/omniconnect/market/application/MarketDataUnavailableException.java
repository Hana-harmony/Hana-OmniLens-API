package com.hana.omniconnect.market.application;

import com.hana.omniconnect.common.exception.BusinessException;
import com.hana.omniconnect.common.exception.ErrorCode;

public class MarketDataUnavailableException extends BusinessException {

    public MarketDataUnavailableException(String message) {
        super(ErrorCode.MARKET_DATA_UNAVAILABLE, message);
    }
}
