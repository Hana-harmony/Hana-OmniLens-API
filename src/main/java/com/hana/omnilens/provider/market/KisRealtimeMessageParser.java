package com.hana.omnilens.provider.market;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class KisRealtimeMessageParser {

    private static final int KIS_FRAME_FIELD_COUNT = 4;
    private static final int ORDERBOOK_LEVEL_COUNT = 10;
    private static final int AFTER_HOURS_ORDERBOOK_LEVEL_COUNT = 9;
    private static final int TRADE_MIN_FIELD_COUNT = 46;
    private static final int AFTER_HOURS_TRADE_MIN_FIELD_COUNT = 41;
    private static final int VI_STATUS_INDEX = 43;
    private static final int SINGLE_PRICE_TRADING_INDEX = 44;
    private static final int TRADING_HALT_INDEX = 45;
    private static final int AFTER_HOURS_TRADING_HALT_INDEX = 35;
    private static final DateTimeFormatter BUSINESS_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    public Optional<KisRealtimeTradeTick> parseTradeTick(String rawMessage) {
        Optional<KisRealtimeTradeTick> regularTrade = payload(rawMessage, KisRealtimeTransaction.TRADE)
                .filter(fields -> fields.length >= TRADE_MIN_FIELD_COUNT)
                .map(fields -> tradeTick(
                        fields,
                        KisRealtimeTradeTick.REGULAR_SESSION,
                        fields[VI_STATUS_INDEX],
                        fields[SINGLE_PRICE_TRADING_INDEX],
                        fields[TRADING_HALT_INDEX]));
        if (regularTrade.isPresent()) {
            return regularTrade;
        }
        return payload(rawMessage, KisRealtimeTransaction.AFTER_HOURS_TRADE)
                .filter(fields -> fields.length >= AFTER_HOURS_TRADE_MIN_FIELD_COUNT)
                .map(fields -> tradeTick(
                        fields,
                        KisRealtimeTradeTick.AFTER_HOURS_SESSION,
                        "",
                        "Y",
                        fields[AFTER_HOURS_TRADING_HALT_INDEX]));
    }

    public Optional<KisRealtimeOrderBookSnapshot> parseOrderBook(String rawMessage) {
        Optional<KisRealtimeOrderBookSnapshot> regularOrderBook = payload(rawMessage, KisRealtimeTransaction.ORDERBOOK)
                .filter(fields -> fields.length >= 58)
                .map(fields -> new KisRealtimeOrderBookSnapshot(
                        fields[0],
                        fields[1],
                        levels(fields, ORDERBOOK_LEVEL_COUNT, 3, 23),
                        levels(fields, ORDERBOOK_LEVEL_COUNT, 13, 33),
                        number(fields[53])));
        if (regularOrderBook.isPresent()) {
            return regularOrderBook;
        }
        return payload(rawMessage, KisRealtimeTransaction.AFTER_HOURS_ORDERBOOK)
                .filter(fields -> fields.length >= 54)
                .map(fields -> new KisRealtimeOrderBookSnapshot(
                        fields[0],
                        fields[1],
                        levels(fields, AFTER_HOURS_ORDERBOOK_LEVEL_COUNT, 3, 21),
                        levels(fields, AFTER_HOURS_ORDERBOOK_LEVEL_COUNT, 12, 30),
                        number(fields[49])));
    }

    private Optional<String[]> payload(String rawMessage, KisRealtimeTransaction transaction) {
        if (rawMessage == null || rawMessage.isBlank()) {
            return Optional.empty();
        }
        String[] frame = rawMessage.split("\\|", KIS_FRAME_FIELD_COUNT);
        if (frame.length < KIS_FRAME_FIELD_COUNT || !frame[1].equals(transaction.trId())) {
            return Optional.empty();
        }
        return Optional.of(frame[3].split("\\^", -1));
    }

    private KisRealtimeTradeTick tradeTick(
            String[] fields,
            String marketSession,
            String viStatusCode,
            String singlePriceTradingCode,
            String tradingHaltCode) {
        return new KisRealtimeTradeTick(
                fields[0],
                fields[1],
                decimal(fields[2]),
                decimal(fields[5]),
                decimal(fields[10]),
                decimal(fields[11]),
                number(fields[12]),
                number(fields[13]),
                LocalDate.parse(fields[33], BUSINESS_DATE_FORMATTER),
                marketSession,
                viStatusCode,
                singlePriceTradingCode,
                tradingHaltCode);
    }

    private List<KisRealtimeOrderBookSnapshot.Level> levels(
            String[] fields,
            int levelCount,
            int priceOffset,
            int quantityOffset) {
        List<KisRealtimeOrderBookSnapshot.Level> levels = new ArrayList<>(levelCount);
        for (int index = 0; index < levelCount; index++) {
            levels.add(new KisRealtimeOrderBookSnapshot.Level(
                    decimal(fields[priceOffset + index]),
                    number(fields[quantityOffset + index])));
        }
        return List.copyOf(levels);
    }

    private BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private long number(String value) {
        return Long.parseLong(value);
    }
}
