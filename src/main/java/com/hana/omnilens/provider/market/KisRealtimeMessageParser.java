package com.hana.omnilens.provider.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
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
    private static final int INDEX_TRADE_MIN_FIELD_COUNT = 30;
    private static final int VI_STATUS_INDEX = 43;
    private static final int SINGLE_PRICE_TRADING_INDEX = 44;
    private static final int TRADING_HALT_INDEX = 45;
    private static final int AFTER_HOURS_TRADING_HALT_INDEX = 35;
    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter BUSINESS_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter TRADE_TIME_FORMATTER = DateTimeFormatter.ofPattern("HHmmss");

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

    public Optional<KisRealtimeIndexTick> parseIndexTick(String rawMessage) {
        return payload(rawMessage, KisRealtimeTransaction.INDEX_TRADE)
                .filter(fields -> fields.length >= INDEX_TRADE_MIN_FIELD_COUNT)
                .map(fields -> new KisRealtimeIndexTick(
                        fields[0],
                        indexName(fields[0]),
                        indexMarket(fields[0]),
                        fields[1],
                        decimal(fields[2]),
                        fields[3],
                        decimal(fields[4]),
                        decimal(fields[9]),
                        number(fields[5]),
                        number(fields[6]),
                        decimal(fields[10]),
                        decimal(fields[11]),
                        decimal(fields[12]),
                        decimal(fields[29]),
                        marketDataTime(fields[1]),
                        "KIS_WEBSOCKET_INDEX"));
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
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value);
    }

    private long number(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        return Long.parseLong(value);
    }

    private Instant marketDataTime(String tradeTime) {
        LocalTime time = LocalTime.parse(tradeTime, TRADE_TIME_FORMATTER);
        return LocalDateTime.of(LocalDate.now(KOREA_ZONE), time).atZone(KOREA_ZONE).toInstant();
    }

    private String indexName(String indexCode) {
        return switch (indexCode) {
            case "0001" -> "KOSPI";
            case "1001" -> "KOSDAQ";
            case "2001" -> "KOSPI 200";
            default -> "Korea Index " + indexCode;
        };
    }

    private String indexMarket(String indexCode) {
        return switch (indexCode) {
            case "1001" -> "KOSDAQ";
            case "2001" -> "KOSPI200";
            default -> "KOSPI";
        };
    }
}
