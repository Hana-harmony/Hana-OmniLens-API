package com.hana.omnilens.tax.refund;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class TaxCorrectionRequestPdfService {

    private static final String TEMPLATE = "forms/tax-correction-request.pdf";
    private static final String FONT = "fonts/NanumBarunGothic.ttf";

    public byte[] render(Map<String, String> fields) {
        try (InputStream template = new ClassPathResource(TEMPLATE).getInputStream();
             PDDocument document = Loader.loadPDF(template.readAllBytes());
             InputStream fontInput = new ClassPathResource(FONT).getInputStream()) {
            PDFont font = PDType0Font.load(document, fontInput, true);
            overlay(document, normalized(fields), font);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("The trusted correction-request form could not be rendered.", exception);
        }
    }

    private void overlay(PDDocument document, Map<String, String> fields, PDFont font) throws IOException {
        PDPage page = document.getPage(0);
        try (PDPageContentStream stream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            text(stream, font, fields, "receiptNumber", 130, 733, 8, 85);
            text(stream, font, fields, "receiptDate", 300, 733, 8, 85);
            text(stream, font, fields, "claimantName", 173, 701, 9, 170);
            text(stream, font, fields, "birthDate", 432, 701, 8, 90);
            text(stream, font, fields, "taxpayerIdentificationNumber", 194, 674, 8, 95);
            text(stream, font, fields, "claimantPhone", 441, 674, 8, 90);
            text(stream, font, fields, "claimantResidence", 194, 645, 8, 150);
            text(stream, font, fields, "residencyCountryCode", 522, 645, 8, 32);
            text(stream, font, fields, "claimantAddress", 173, 617, 8, 360);
            text(stream, font, fields, "agentName", 202, 583, 8, 135);
            text(stream, font, fields, "agentPhone", 441, 583, 8, 90);
            text(stream, font, fields, "agentAddress", 173, 558, 8, 360);
            text(stream, font, fields, "withholdingAgentName", 202, 525, 8, 135);
            text(stream, font, fields, "withholdingAgentTaxId", 441, 525, 8, 85);
            text(stream, font, fields, "taxOffice", 202, 500, 8, 135);
            text(stream, font, fields, "withholdingAgentPhone", 480, 500, 8, 75);
            text(stream, font, fields, "withholdingAgentAddress", 173, 475, 8, 360);
            multiline(stream, font, value(fields, "claimContent"), 125, 437, 8, 70, 405);
            date(stream, font, value(fields, "applicationDate"), 333);
            text(stream, font, fields, "claimantSignatureName", 400, 306, 9, 130);
            text(stream, font, fields, "agentSignatureName", 400, 288, 9, 130);
        }
    }

    private void date(PDPageContentStream stream, PDFont font, String value, float y) throws IOException {
        String[] parts = value.split("-");
        if (parts.length == 3) {
            write(stream, font, parts[0], 410, y, 8);
            write(stream, font, parts[1], 470, y, 8);
            write(stream, font, parts[2], 510, y, 8);
            return;
        }
        write(stream, font, fit(font, value, 8, 95), 410, y, 8);
    }

    private void text(
            PDPageContentStream stream,
            PDFont font,
            Map<String, String> fields,
            String key,
            float x,
            float y,
            float size,
            float maxWidth) throws IOException {
        String value = fit(font, value(fields, key), size, maxWidth);
        if (value.isBlank()) return;
        write(stream, font, value, x, y, size);
    }

    private void write(PDPageContentStream stream, PDFont font, String value, float x, float y, float size)
            throws IOException {
        if (value.isBlank()) return;
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(value);
        stream.endText();
    }

    private void multiline(
            PDPageContentStream stream,
            PDFont font,
            String value,
            float x,
            float y,
            float size,
            int maxChars,
            float maxWidth) throws IOException {
        if (value.isBlank()) return;
        String normalized = value.replaceAll("\\s+", " ").strip();
        int offset = 0;
        int line = 0;
        while (offset < normalized.length() && line < 4) {
            int end = Math.min(normalized.length(), offset + maxChars);
            if (end < normalized.length()) {
                int boundary = normalized.lastIndexOf(' ', end);
                if (boundary > offset) end = boundary;
            }
            String part = fit(font, normalized.substring(offset, end).strip(), size, maxWidth);
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(x, y - line * 13);
            stream.showText(part);
            stream.endText();
            offset = end;
            while (offset < normalized.length() && normalized.charAt(offset) == ' ') offset++;
            line++;
        }
    }

    private String fit(PDFont font, String value, float size, float maxWidth) throws IOException {
        String candidate = value;
        while (!candidate.isEmpty() && font.getStringWidth(candidate) / 1000f * size > maxWidth) {
            candidate = candidate.substring(0, candidate.length() - 1);
        }
        return candidate.length() < value.length() && candidate.length() > 1
                ? candidate.substring(0, candidate.length() - 1) + "…"
                : candidate;
    }

    private Map<String, String> normalized(Map<String, String> values) {
        Map<String, String> result = new LinkedHashMap<>();
        if (values == null) return result;
        values.forEach((key, value) -> result.put(key, value == null ? "" : value.strip()));
        return result;
    }

    private String value(Map<String, String> fields, String key) {
        return fields.getOrDefault(key, "");
    }
}
