package com.hana.omnilens.tax.refund;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

class TaxCorrectionRequestPdfServiceTest {

    @Test
    void placesValuesInsideTrustedFormCells() throws Exception {
        byte[] pdf = new TaxCorrectionRequestPdfService().render(Map.of(
                "receiptNumber", "QA-RECEIPT",
                "claimantName", "VISUAL-QA-CLAIMANT",
                "applicationDate", "2099-12-31"));

        List<PlacedText> texts = positions(pdf);
        assertPosition(texts, "QA-RECEIPT", 130, 109);
        assertPosition(texts, "VISUAL-QA-CLAIMANT", 173, 141);
        assertPosition(texts, "2099", 410, 509);
        assertPosition(texts, "12", 470, 509);
        assertPosition(texts, "31", 510, 509);
    }

    private List<PlacedText> positions(byte[] pdf) throws IOException {
        List<PlacedText> texts = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) {
                    if (!positions.isEmpty()) {
                        TextPosition first = positions.get(0);
                        texts.add(new PlacedText(text, first.getXDirAdj(), first.getYDirAdj()));
                    }
                }
            };
            stripper.getText(document);
        }
        return texts;
    }

    private void assertPosition(List<PlacedText> texts, String value, float x, float y) {
        assertThat(texts)
                .filteredOn(text -> text.value().equals(value))
                .singleElement()
                .satisfies(text -> {
                    assertThat(text.x()).isBetween(x - 1, x + 1);
                    assertThat(text.y()).isBetween(y - 1, y + 1);
                });
    }

    private record PlacedText(String value, float x, float y) {
    }
}
