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
    private static final DateTimeFormatter BUSINESS_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    public Optional<KisRealtimeTradeTick> parseTradeTick(String rawMessage) {
        return payload(rawMessage, KisRealtimeTransaction.TRADE)
                .filter(fields -> fields.length >= 46)
                .map(fields -> new KisRealtimeTradeTick(
                        fields[0],
                        fields[1],
                        decimal(fields[2]),
                        decimal(fields[5]),
                        decimal(fields[10]),
                        decimal(fields[11]),
                        number(fields[12]),
                        number(fields[13]),
                        LocalDate.parse(fields[33], BUSINESS_DATE_FORMATTER)));
    }

    public Optional<KisRealtimeOrderBookSnapshot> parseOrderBook(String rawMessage) {
        return payload(rawMessage, KisRealtimeTransaction.ORDERBOOK)
                .filter(fields -> fields.length >= 58)
                .map(fields -> new KisRealtimeOrderBookSnapshot(
                        fields[0],
                        fields[1],
                        levels(fields, 3, 23),
                        levels(fields, 13, 33),
                        number(fields[53])));
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

    private List<KisRealtimeOrderBookSnapshot.Level> levels(String[] fields, int priceOffset, int quantityOffset) {
        List<KisRealtimeOrderBookSnapshot.Level> levels = new ArrayList<>(ORDERBOOK_LEVEL_COUNT);
        for (int index = 0; index < ORDERBOOK_LEVEL_COUNT; index++) {
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
