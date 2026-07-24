package com.hana.omniconnect.provider.news;

import org.springframework.web.client.RestClientException;

public class NaverNewsDailyBudgetExceededException extends RestClientException {

    public NaverNewsDailyBudgetExceededException(int dailyBudget) {
        super("Naver news daily request budget exhausted: " + dailyBudget);
    }
}
