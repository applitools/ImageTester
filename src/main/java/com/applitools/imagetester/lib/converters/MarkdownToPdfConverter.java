package com.applitools.imagetester.lib.converters;

import com.applitools.imagetester.lib.Patterns;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class MarkdownToPdfConverter implements FormatConverter {

    private static final String CSS =
            "body { font-family: sans-serif; font-size: 11pt; margin: 36pt; }" +
            "h1 { font-size: 20pt; } h2 { font-size: 16pt; } h3 { font-size: 14pt; }" +
            "code, pre { font-family: monospace; font-size: 10pt; background: #f4f4f4; }" +
            "pre { padding: 8pt; }" +
            "blockquote { border-left: 3pt solid #ccc; padding-left: 10pt; color: #555; }";

    private final Parser parser = Parser.builder().build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().build();

    @Override
    public boolean accepts(File file) {
        return Patterns.MARKDOWN.matcher(file.getName()).matches();
    }

    @Override
    public File convertToPdf(File file, Path tempDir) throws IOException {
        String md = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        Node document = parser.parse(md);
        String body = renderer.render(document);
        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"/>" +
                "<style>" + CSS + "</style></head><body>" + body + "</body></html>";

        Path outPath = tempDir.resolve(basenameWithPdfExtension(file));
        try (OutputStream os = new FileOutputStream(outPath.toFile())) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
        }
        return outPath.toFile();
    }

    private static String basenameWithPdfExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        return base + ".pdf";
    }
}
