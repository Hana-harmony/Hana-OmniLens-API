package com.hana.omnilens.tax.refund;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

@Service
public class TaxCorrectionRequestPdfService {

    private static final int MAX_TEMPLATE_BYTES = 10 * 1024 * 1024;

    public byte[] render(Map<String, String> fields, byte[] template) {
        try (PDDocument document = openTemplate(template)) {
            PDPage page = document.getPage(document.getNumberOfPages() - 1);
            PDRectangle mediaBox = page.getMediaBox();
            try (PDPageContentStream stream = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                stream.setLeading(13);
                stream.newLineAtOffset(40, 75);
                for (Map.Entry<String, String> entry : normalized(fields).entrySet()) {
                    stream.showText(safe(entry.getKey()) + ": " + safe(entry.getValue()));
                    stream.newLine();
                }
                stream.endText();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalArgumentException("A valid PDF correction-request template is required.", exception);
        }
    }

    private PDDocument openTemplate(byte[] template) throws IOException {
        if (template != null && template.length > 0) {
            if (template.length > MAX_TEMPLATE_BYTES) throw new IllegalArgumentException("The template PDF must be 10 MB or smaller.");
            return Loader.loadPDF(template);
        }
        PDDocument document = new PDDocument();
        document.addPage(new PDPage(PDRectangle.A4));
        return document;
    }

    private Map<String, String> normalized(Map<String, String> values) {
        Map<String, String> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(key, value == null ? "" : value));
        return result;
    }

    private String safe(String value) {
        return value.replaceAll("[^\\x20-\\x7E]", "?").replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }
}
