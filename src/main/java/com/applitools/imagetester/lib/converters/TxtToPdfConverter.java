package com.applitools.imagetester.lib.converters;

import com.applitools.imagetester.lib.Patterns;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TxtToPdfConverter implements FormatConverter {

    private static final PDRectangle PAGE_SIZE = PDRectangle.LETTER;
    private static final float MARGIN = 50f;
    private static final float FONT_SIZE = 11f;
    private static final float LINE_HEIGHT = 14f;
    private static final int MAX_CHARS_PER_LINE = 90;
    private static final PDFont FONT = PDType1Font.COURIER;

    @Override
    public boolean accepts(File file) {
        return Patterns.TEXT.matcher(file.getName()).matches();
    }

    @Override
    public File convertToPdf(File file, Path tempDir) throws IOException {
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        List<String> lines = wrap(content);
        Path outPath = ConverterPaths.resolveWithinTempDir(tempDir, ConverterPaths.basenameWithPdfExtension(file));

        try (PDDocument doc = new PDDocument()) {
            renderLines(doc, lines);
            doc.save(outPath.toFile());
        }
        return outPath.toFile();
    }

    private static List<String> wrap(String content) {
        List<String> wrapped = new ArrayList<>();
        for (String line : content.split("\\r?\\n", -1)) {
            if (line.isEmpty()) { wrapped.add(""); continue; }
            int i = 0;
            while (i < line.length()) {
                int end = Math.min(i + MAX_CHARS_PER_LINE, line.length());
                wrapped.add(line.substring(i, end));
                i = end;
            }
        }
        return wrapped;
    }

    private static void renderLines(PDDocument doc, List<String> lines) throws IOException {
        float top = PAGE_SIZE.getHeight() - MARGIN;
        float usable = PAGE_SIZE.getHeight() - 2 * MARGIN;
        int linesPerPage = Math.max(1, (int) (usable / LINE_HEIGHT));

        int idx = 0;
        while (idx < lines.size() || idx == 0) {
            PDPage page = new PDPage(PAGE_SIZE);
            doc.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.setFont(FONT, FONT_SIZE);
                stream.beginText();
                stream.newLineAtOffset(MARGIN, top);
                int limit = Math.min(idx + linesPerPage, lines.size());
                boolean first = true;
                for (int i = idx; i < limit; i++) {
                    if (!first) stream.newLineAtOffset(0, -LINE_HEIGHT);
                    stream.showText(sanitize(lines.get(i)));
                    first = false;
                }
                stream.endText();
            }
            idx += linesPerPage;
            if (lines.isEmpty()) break;
        }
    }

    private static String sanitize(String line) {
        StringBuilder sb = new StringBuilder(line.length());
        for (char c : line.toCharArray()) {
            sb.append(c < 0x80 ? c : '?');
        }
        return sb.toString();
    }
}
