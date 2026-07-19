package com.hana.omniconnect.market.application;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

final class ForeignOwnershipTradingDatePolicy {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final LocalTime CLOSING_DATA_READY_TIME = LocalTime.of(15, 40);

    private ForeignOwnershipTradingDatePolicy() {
    }

    static LocalDate expectedBaseDate(Clock clock) {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(KOREA_ZONE);
        LocalDate today = now.toLocalDate();
        if (isWeekday(today) && !now.toLocalTime().isBefore(CLOSING_DATA_READY_TIME)) {
            return today;
        }
        return previousWeekday(today);
    }

    static LocalDate previousWeekday(LocalDate date) {
        LocalDate candidate = date.minusDays(1);
        while (!isWeekday(candidate)) {
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }

    private static boolean isWeekday(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }
}
