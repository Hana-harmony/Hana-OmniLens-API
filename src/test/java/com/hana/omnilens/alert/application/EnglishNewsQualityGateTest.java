package com.hana.omnilens.alert.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnglishNewsQualityGateTest {

    @Test
    void rejectsKnownLocalLlmBrokenEnglishSurfaces() {
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "Hanoteoreminder of the US- South Korea exchange, (Kim Yuseo's economic reform)."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "SK hynix was expected to trade on the NMSK exchange."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "None of the 40-year-olds will ever even get a fresh water break."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "I'm going to use it on the power-driven, semiconductor cluster."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "Two-carpet rose slightly this time, and the new bond's price flow came to an end."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "Ants, leaders, and officials, adding silicon to the future-sustainable capital."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "The Korean stock market faced trading by Samjeon Nix as a key issue."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "Middle and small businesses fund acts; investors net at the European show."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "No AI or human."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "Ants net bought 161 trillion KRW for KRW-3777B."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "Two-thirds of the sheriff's rifle exploded in the fifth quarter."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "Korean entrepreneurhan warned that SKhinky and SinErlwyk prices may move."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "SK Hallinkyos Semiconductor's investor impact is higher on regular market cap."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "Kamala's Klamath stock exchange trading heightened attention to the North American and South American trade disputes."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "Revenue was achieved, but a substitute offering was high-slang."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "Samsung Electronics DX and DS each hold shares in Tutat ESG."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "Hyundai Motor, Kia, and Mercedes-Benz all keep 82% of American car parts."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "I'm so freaked out about the deposits, and I'm finally getting my money back to the bank."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "This week's Triple-A Hynix ADR is up for grabs."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "Hyundai Motor and Kia hope to capture the RV/HEV truck-train."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "Korean banks rose large amounts of deposits on foreign exchanges as the market becomes more active."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "According to the National Association of Churches, 10 of the 18 temples offered a periodic allowance."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "Major central banks increased total receivable volume by almost 90 trillion yuan."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "The move-digest service was opened at the Youth Center of the 3rd Army of the Republic of China."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText("…Korean stock market"))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText("[Korean stock market ]..."))
                .isFalse();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText("Korean stock market"))
                .isFalse();
    }

    @Test
    void rejectsSummaryLineStartingWithStockCodeSubject() {
        assertThat(EnglishNewsQualityGate.englishSummaryLineOrEmpty(
                "034020 drew attention in the article around the KOSPI market move."))
                .isEmpty();
    }

    @Test
    void rejectsFragmentaryTickerAndMarketSummaryLines() {
        assertThat(EnglishNewsQualityGate.englishSummaryLineOrEmpty(
                "() crisis intraday Korean stock market."))
                .isEmpty();
        assertThat(EnglishNewsQualityGate.englishSummaryLineOrEmpty("."))
                .isEmpty();
        assertThat(EnglishNewsQualityGate.englishSummaryLineOrEmpty(
                "(WTI) 72.69 3% surge (-4.60%), (-3.43%) stock price, SK (-6.34%), (-10.25%) IT· ·."))
                .isEmpty();
        assertThat(EnglishNewsQualityGate.englishSummaryLineOrEmpty("< > 5."))
                .isEmpty();
        assertThat(EnglishNewsQualityGate.englishSummaryLineOrEmpty("167% 60%."))
                .isEmpty();
        assertThat(EnglishNewsQualityGate.englishSummaryLineOrEmpty("KOSPI 8 5% 7200."))
                .isEmpty();
        assertThat(EnglishNewsQualityGate.englishSummaryLineOrEmpty("KOSPI, 5%."))
                .isEmpty();
    }

    @Test
    void keepsNormalFinancialEnglish() {
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "SK hynix's Nasdaq listing raises questions about its impact on the domestic stock market."))
                .isTrue();
    }

    @Test
    void keepsNormalHyphenatedFinancialAndTechnologyTerms() {
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "Humanoid-robot demand creates a multi-trillion-dollar opportunity, while edge-computing parts and supply-chain suppliers benefit first."))
                .isTrue();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "The one-to-ten ADR structure, policy-rate gap, and price-to-earnings valuation remain investor focus areas."))
                .isTrue();
        assertThat(EnglishNewsQualityGate.hasUsableEnglishText(
                "Semiconductor-led flows left price-to-book valuations, interest-rate pressure, and balance-sheet quality in focus."))
                .isTrue();
    }

    @Test
    void keepsPharmaBiotechPbrSummaryLines() {
        assertThat(EnglishNewsQualityGate.englishSummaryLineOrEmpty(
                "Pharma and biotech stocks showed widespread undervaluation as many healthcare companies traded below 1x PBR."))
                .isNotEmpty();
        assertThat(EnglishNewsQualityGate.englishSummaryLineOrEmpty(
                "The article cites pharma and biotech shares lagging the semiconductor-led rally, about 90 healthcare companies trading below 1x PBR, interest-rate pressure weighing on valuations."))
                .isNotEmpty();
        assertThat(EnglishNewsQualityGate.englishSummaryLineOrEmpty(
                "Investors should track sector rotation, balance-sheet quality, and whether biotech investor appetite recovers beyond semiconductor-led flows."))
                .isNotEmpty();
    }
}
