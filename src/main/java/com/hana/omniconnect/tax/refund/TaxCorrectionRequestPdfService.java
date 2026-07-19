package com.hana.omniconnect.tax.refund;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class TaxCorrectionRequestPdfService {

    private static final String TEMPLATE = "forms/tax-correction-request.pdf";
    private static final String FONT = "fonts/NanumBarunGothic.ttf";
    private static final float PREVIEW_DPI = 144;
    private static final List<FieldPlacement> FIELD_PLACEMENTS = List.of(
            field("receiptNumber", "접수번호", 130, 733, 85, 8),
            field("receiptDate", "접수일자", 300, 733, 85, 8),
            field("claimantName", "청구인 성명", 173, 701, 170, 9),
            field("birthDate", "생년월일", 432, 701, 90, 8),
            field("taxpayerIdentificationNumber", "납세자번호", 194, 674, 95, 8),
            field("claimantPhone", "전화번호", 441, 674, 90, 8),
            field("claimantResidence", "거주지국", 194, 645, 150, 8),
            field("residencyCountryCode", "거주지국 코드", 522, 645, 32, 8),
            field("claimantAddress", "주소", 173, 617, 360, 8),
            field("agentName", "대리인 성명", 202, 583, 135, 8),
            field("agentPhone", "대리인 전화번호", 441, 583, 90, 8),
            field("agentAddress", "대리인 주소", 173, 558, 360, 8),
            field("withholdingAgentName", "원천징수의무자 성명", 202, 525, 135, 8),
            field("withholdingAgentTaxId", "원천징수의무자 납세자번호", 441, 525, 85, 8),
            field("taxOffice", "세무서", 202, 500, 135, 8),
            field("withholdingAgentPhone", "원천징수의무자 전화번호", 480, 500, 75, 8),
            field("withholdingAgentAddress", "원천징수의무자 주소", 173, 475, 360, 8),
            new FieldPlacement("claimContent", "청구 내용", 125, 437, 405, 8, 390, 58, true),
            new FieldPlacement("applicationDate", "신청일", 405, 333, 130, 8, 327, 16, false),
            field("claimantSignatureName", "청구인 서명 성명", 400, 306, 130, 9),
            field("agentSignatureName", "대리인 서명 성명", 400, 288, 130, 9));

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

    public TemplateLayout templateLayout() {
        try (InputStream template = new ClassPathResource(TEMPLATE).getInputStream();
             PDDocument document = Loader.loadPDF(template.readAllBytes())) {
            PDPage page = document.getPage(0);
            return new TemplateLayout(
                    page.getMediaBox().getWidth(),
                    page.getMediaBox().getHeight(),
                    document.getNumberOfPages(),
                    FIELD_PLACEMENTS.stream().map(FieldPlacement::editorField).toList());
        } catch (IOException exception) {
            throw new IllegalStateException("The trusted correction-request form layout could not be loaded.", exception);
        }
    }

    public byte[] templatePage(int pageNumber) {
        try (InputStream template = new ClassPathResource(TEMPLATE).getInputStream();
             PDDocument document = Loader.loadPDF(template.readAllBytes())) {
            if (pageNumber < 1 || pageNumber > document.getNumberOfPages()) {
                throw new IllegalArgumentException("Correction-request template page is out of range.");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(new PDFRenderer(document).renderImageWithDPI(pageNumber - 1, PREVIEW_DPI, ImageType.RGB), "png", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("The trusted correction-request form preview could not be rendered.", exception);
        }
    }

    private void overlay(PDDocument document, Map<String, String> fields, PDFont font) throws IOException {
        PDPage page = document.getPage(0);
        try (PDPageContentStream stream = new PDPageContentStream(
                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
            for (FieldPlacement placement : FIELD_PLACEMENTS) {
                if (placement.key().equals("applicationDate")) {
                    date(stream, font, value(fields, placement.key()), placement.baselineY());
                } else if (placement.multiline()) {
                    multiline(stream, font, value(fields, placement.key()), placement.x(), placement.baselineY(),
                            placement.fontSize(), 70, placement.width());
                } else {
                    text(stream, font, fields, placement.key(), placement.x(), placement.baselineY(),
                            placement.fontSize(), placement.width());
                }
            }
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

    private static FieldPlacement field(
            String key,
            String label,
            float x,
            float baselineY,
            float width,
            float fontSize) {
        return new FieldPlacement(key, label, x, baselineY, width, fontSize, baselineY - 4, 16, false);
    }

    private record FieldPlacement(
            String key,
            String label,
            float x,
            float baselineY,
            float width,
            float fontSize,
            float boxBottom,
            float boxHeight,
            boolean multiline) {
        private EditorField editorField() {
            return new EditorField(key, label, 1, x, boxBottom, width, boxHeight, multiline);
        }
    }

    public record TemplateLayout(float pageWidth, float pageHeight, int pageCount, List<EditorField> fields) {
    }

    public record EditorField(
            String key,
            String label,
            int page,
            float x,
            float y,
            float width,
            float height,
            boolean multiline) {
    }
}
