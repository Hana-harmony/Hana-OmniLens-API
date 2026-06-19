package com.hana.omnilens.market.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.hana.omnilens.market.domain.ForeignOwnershipPrediction;
import com.hana.omnilens.provider.market.KisRealtimeTradeTick;
import com.hana.omnilens.provider.market.ForeignOwnershipSnapshot;

@Component
public class ForeignOwnershipPredictionEngine {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal MAX_INTRADAY_UNCERTAINTY_RATE = new BigDecimal("0.500000");
    private static final BigDecimal SNAPSHOT_ONLY_UNCERTAINTY_RATE = new BigDecimal("0.050000");
    private static final BigDecimal INTRADAY_VOLUME_WEIGHT = new BigDecimal("0.050000");

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
        if (ownershipSnapshot.isEmpty()) {
            return new ForeignOwnershipPrediction(
                    BigDecimal.ZERO.setScale(6),
                    BigDecimal.ZERO.setScale(6),
                    BigDecimal.ZERO.setScale(6),
                    BigDecimal.ZERO.setScale(6),
                    BigDecimal.ZERO.setScale(6),
                    realtimeTradeTick.map(KisRealtimeTradeTick::accumulatedVolume).orElse(0L),
                    null,
                    clock.instant(),
                    "NO_SNAPSHOT",
                    "FOREIGN_OWNERSHIP_PREDICTOR_NO_SNAPSHOT");
        }

        ForeignOwnershipSnapshot snapshot = ownershipSnapshot.orElseThrow();
        BigDecimal orderImpactRate = orderImpactRate(side, quantity, snapshot.foreignLimitQuantity());
        BigDecimal baseRate = snapshot.foreignLimitExhaustionRate()
                .add(orderImpactRate)
                .setScale(6, RoundingMode.HALF_UP);
        BigDecimal uncertaintyRate = intradayUncertaintyRate(snapshot.foreignLimitQuantity(), realtimeTradeTick);
        BigDecimal minRate = floorZero(baseRate.subtract(uncertaintyRate));
        BigDecimal maxRate = baseRate.add(uncertaintyRate).setScale(6, RoundingMode.HALF_UP);

        return new ForeignOwnershipPrediction(
                minRate,
                baseRate,
                maxRate,
                orderImpactRate,
                uncertaintyRate,
                realtimeTradeTick.map(KisRealtimeTradeTick::accumulatedVolume).orElse(0L),
                snapshot.baseDate(),
                clock.instant(),
                realtimeTradeTick.isPresent() ? "REALTIME_VOLUME_ADJUSTED" : "SNAPSHOT_ONLY",
                realtimeTradeTick.isPresent()
                        ? "KIS_FOREIGN_OWNERSHIP_CACHE+KIS_WEBSOCKET_TRADE_VOLUME"
                        : "KIS_FOREIGN_OWNERSHIP_CACHE");
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

    private BigDecimal floorZero(BigDecimal value) {
        if (value.signum() < 0) {
            return BigDecimal.ZERO.setScale(6);
        }
        return value.setScale(6, RoundingMode.HALF_UP);
    }
}
