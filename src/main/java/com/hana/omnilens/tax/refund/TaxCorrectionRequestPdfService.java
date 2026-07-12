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
            text(stream, font, fields, "receiptNumber", 95, 777, 8, 220);
            text(stream, font, fields, "receiptDate", 468, 777, 8, 90);
            text(stream, font, fields, "claimantName", 92, 729, 9, 205);
            text(stream, font, fields, "birthDate", 293, 699, 8, 90);
            text(stream, font, fields, "taxpayerIdentificationNumber", 92, 670, 8, 95);
            text(stream, font, fields, "claimantPhone", 279, 670, 8, 100);
            text(stream, font, fields, "claimantResidence", 433, 670, 8, 86);
            text(stream, font, fields, "residencyCountryCode", 548, 670, 8, 32);
            text(stream, font, fields, "claimantAddress", 92, 641, 8, 480);
            text(stream, font, fields, "agentName", 92, 593, 8, 130);
            text(stream, font, fields, "agentPhone", 285, 593, 8, 100);
            text(stream, font, fields, "agentAddress", 92, 565, 8, 480);
            text(stream, font, fields, "withholdingAgentName", 92, 517, 8, 140);
            text(stream, font, fields, "withholdingAgentTaxId", 286, 517, 8, 95);
            text(stream, font, fields, "taxOffice", 433, 517, 8, 78);
            text(stream, font, fields, "withholdingAgentPhone", 516, 517, 8, 66);
            text(stream, font, fields, "withholdingAgentAddress", 92, 489, 8, 480);
            multiline(stream, font, value(fields, "claimContent"), 56, 415, 8, 70, 485);
            text(stream, font, fields, "applicationDate", 430, 185, 8, 135);
            text(stream, font, fields, "claimantSignatureName", 255, 143, 9, 170);
            text(stream, font, fields, "agentSignatureName", 255, 117, 9, 170);
        }
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
