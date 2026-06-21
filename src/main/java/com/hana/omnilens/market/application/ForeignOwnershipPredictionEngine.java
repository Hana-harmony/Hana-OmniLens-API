package com.hana.omnilens.market.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.hana.omnilens.market.domain.ForeignOwnershipDailySnapshot;
import com.hana.omnilens.market.domain.ForeignOwnershipPrediction;
import com.hana.omnilens.provider.market.KisRealtimeTradeTick;
import com.hana.omnilens.provider.market.ForeignOwnershipSnapshot;

@Component
public class ForeignOwnershipPredictionEngine {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal MAX_INTRADAY_UNCERTAINTY_RATE = new BigDecimal("0.500000");
    private static final BigDecimal MAX_HISTORY_UNCERTAINTY_RATE = new BigDecimal("1.500000");
    private static final BigDecimal SNAPSHOT_ONLY_UNCERTAINTY_RATE = new BigDecimal("0.050000");
    private static final BigDecimal INTRADAY_VOLUME_WEIGHT = new BigDecimal("0.050000");
    private static final String MODEL_VERSION = "foreign-ownership-timeseries-v1";

    private final Clock clock;

    public ForeignOwnershipPredictionEngine() {
        this(Clock.systemUTC());
    }

    ForeignOwnershipPredictionEngine(Clock clock) {
        this.clock = clock;
    }

    public ForeignOwnershipPrediction predict(
            String side,
            long quantity,
            Optional<ForeignOwnershipSnapshot> ownershipSnapshot,
            Optional<KisRealtimeTradeTick> realtimeTradeTick) {
        return predict(side, quantity, ownershipSnapshot, realtimeTradeTick, List.of());
    }

    public ForeignOwnershipPrediction predict(
            String side,
            long quantity,
            Optional<ForeignOwnershipSnapshot> ownershipSnapshot,
            Optional<KisRealtimeTradeTick> realtimeTradeTick,
            List<ForeignOwnershipDailySnapshot> history) {
        if (ownershipSnapshot.isEmpty()) {
            return new ForeignOwnershipPrediction(
                    BigDecimal.ZERO.setScale(6),
                    BigDecimal.ZERO.setScale(6),
                    BigDecimal.ZERO.setScale(6),
                    BigDecimal.ZERO.setScale(6),
                    BigDecimal.ZERO.setScale(6),
                    realtimeTradeTick.map(KisRealtimeTradeTick::accumulatedVolume).orElse(0L),
                    BigDecimal.ZERO.setScale(6),
                    0,
                    0,
                    null,
                    clock.instant(),
                    "NO_SNAPSHOT",
                    BigDecimal.ZERO.setScale(4),
                    MODEL_VERSION,
                    "FOREIGN_OWNERSHIP_PREDICTOR_NO_SNAPSHOT");
        }

        ForeignOwnershipSnapshot snapshot = ownershipSnapshot.orElseThrow();
        List<ForeignOwnershipDailySnapshot> sortedHistory = sortedHistory(history);
        TrendStats trendStats = trendStats(sortedHistory);
        BigDecimal orderImpactRate = orderImpactRate(side, quantity, snapshot.foreignLimitQuantity());
        BigDecimal baseRate = snapshot.foreignLimitExhaustionRate()
                .add(orderImpactRate)
                .add(trendStats.dailyChangeRate())
                .setScale(6, RoundingMode.HALF_UP);
        BigDecimal uncertaintyRate = intradayUncertaintyRate(snapshot.foreignLimitQuantity(), realtimeTradeTick)
                .max(trendStats.uncertaintyRate())
                .setScale(6, RoundingMode.HALF_UP);
        BigDecimal minRate = floorZero(baseRate.subtract(uncertaintyRate));
        BigDecimal maxRate = baseRate.add(uncertaintyRate).setScale(6, RoundingMode.HALF_UP);
        Confidence confidence = confidence(realtimeTradeTick, trendStats);

        return new ForeignOwnershipPrediction(
                minRate,
                baseRate,
                maxRate,
                orderImpactRate,
                uncertaintyRate,
                realtimeTradeTick.map(KisRealtimeTradeTick::accumulatedVolume).orElse(0L),
                trendStats.dailyChangeRate(),
                trendStats.observationCount(),
                trendStats.windowDays(),
                snapshot.baseDate(),
                clock.instant(),
                confidence.level(),
                confidence.score(),
                MODEL_VERSION,
                source(realtimeTradeTick, trendStats));
    }

    private BigDecimal orderImpactRate(String side, long quantity, long foreignLimitQuantity) {
        if (!"BUY".equals(side) || foreignLimitQuantity <= 0 || quantity <= 0) {
            return BigDecimal.ZERO.setScale(6);
        }
        return BigDecimal.valueOf(quantity)
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(foreignLimitQuantity), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal intradayUncertaintyRate(
            long foreignLimitQuantity,
            Optional<KisRealtimeTradeTick> realtimeTradeTick) {
        if (foreignLimitQuantity <= 0) {
            return BigDecimal.ZERO.setScale(6);
        }
        if (realtimeTradeTick.isEmpty()) {
            return SNAPSHOT_ONLY_UNCERTAINTY_RATE;
        }
        BigDecimal volumeRate = BigDecimal.valueOf(realtimeTradeTick.orElseThrow().accumulatedVolume())
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(foreignLimitQuantity), 6, RoundingMode.HALF_UP)
                .multiply(INTRADAY_VOLUME_WEIGHT)
                .setScale(6, RoundingMode.HALF_UP);
        return volumeRate.min(MAX_INTRADAY_UNCERTAINTY_RATE);
    }

    private List<ForeignOwnershipDailySnapshot> sortedHistory(List<ForeignOwnershipDailySnapshot> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        return history.stream()
                .sorted(Comparator.comparing(ForeignOwnershipDailySnapshot::baseDate))
                .toList();
    }

    private TrendStats trendStats(List<ForeignOwnershipDailySnapshot> history) {
        if (history.size() < 2) {
            return new TrendStats(BigDecimal.ZERO.setScale(6), BigDecimal.ZERO.setScale(6), history.size(), 0);
        }
        ForeignOwnershipDailySnapshot first = history.get(0);
        ForeignOwnershipDailySnapshot last = history.get(history.size() - 1);
        long windowDays = Math.max(1, ChronoUnit.DAYS.between(first.baseDate(), last.baseDate()));
        BigDecimal dailyChangeRate = last.foreignLimitExhaustionRate()
                .subtract(first.foreignLimitExhaustionRate())
                .divide(BigDecimal.valueOf(windowDays), 6, RoundingMode.HALF_UP);
        BigDecimal uncertaintyRate = averageAbsoluteDailyChange(history)
                .min(MAX_HISTORY_UNCERTAINTY_RATE)
                .setScale(6, RoundingMode.HALF_UP);
        return new TrendStats(dailyChangeRate, uncertaintyRate, history.size(), Math.toIntExact(windowDays));
    }

    private BigDecimal averageAbsoluteDailyChange(List<ForeignOwnershipDailySnapshot> history) {
        BigDecimal total = BigDecimal.ZERO;
        int intervals = 0;
        for (int index = 1; index < history.size(); index++) {
            ForeignOwnershipDailySnapshot previous = history.get(index - 1);
            ForeignOwnershipDailySnapshot current = history.get(index);
            long days = Math.max(1, ChronoUnit.DAYS.between(previous.baseDate(), current.baseDate()));
            total = total.add(current.foreignLimitExhaustionRate()
                    .subtract(previous.foreignLimitExhaustionRate())
                    .abs()
                    .divide(BigDecimal.valueOf(days), 6, RoundingMode.HALF_UP));
            intervals++;
        }
        if (intervals == 0) {
            return BigDecimal.ZERO.setScale(6);
        }
        return total.divide(BigDecimal.valueOf(intervals), 6, RoundingMode.HALF_UP);
    }

    private Confidence confidence(Optional<KisRealtimeTradeTick> realtimeTradeTick, TrendStats trendStats) {
        if (trendStats.observationCount() >= 5 && realtimeTradeTick.isPresent()) {
            return new Confidence("TIME_SERIES_REALTIME_ADJUSTED", new BigDecimal("0.8500"));
        }
        if (trendStats.observationCount() >= 5) {
            return new Confidence("TIME_SERIES_ADJUSTED", new BigDecimal("0.7500"));
        }
        if (trendStats.observationCount() >= 2) {
            return new Confidence("LIMITED_TIME_SERIES", new BigDecimal("0.6000"));
        }
        if (realtimeTradeTick.isPresent()) {
            return new Confidence("REALTIME_VOLUME_ADJUSTED", new BigDecimal("0.5500"));
        }
        return new Confidence("SNAPSHOT_ONLY", new BigDecimal("0.4500"));
    }

    private String source(Optional<KisRealtimeTradeTick> realtimeTradeTick, TrendStats trendStats) {
        String source = "KIS_FOREIGN_OWNERSHIP_CACHE";
        if (trendStats.observationCount() >= 2) {
            source += "+FOREIGN_OWNERSHIP_DAILY_TIMESERIES";
        }
        if (realtimeTradeTick.isPresent()) {
            source += "+KIS_WEBSOCKET_TRADE_VOLUME";
        }
        return source;
    }

    private BigDecimal floorZero(BigDecimal value) {
        if (value.signum() < 0) {
            return BigDecimal.ZERO.setScale(6);
        }
        return value.setScale(6, RoundingMode.HALF_UP);
    }

    private record TrendStats(
            BigDecimal dailyChangeRate,
            BigDecimal uncertaintyRate,
            int observationCount,
            int windowDays
    ) {
    }

    private record Confidence(
            String level,
            BigDecimal score
    ) {
    }
}
