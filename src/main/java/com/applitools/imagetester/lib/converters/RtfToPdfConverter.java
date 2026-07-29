package com.applitools.imagetester.lib.converters;

import com.applitools.imagetester.lib.Patterns;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.rtf.RTFEditorKit;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class RtfToPdfConverter implements FormatConverter {

    private final TxtToPdfConverter textRenderer = new TxtToPdfConverter();

    @Override
    public boolean accepts(File file) {
        return Patterns.RTF.matcher(file.getName()).matches();
    }

    @Override
    public File convertToPdf(File file, Path tempDir) throws IOException {
        String plainText = extractPlainText(file);
        File intermediateTxt = ConverterPaths.resolveWithinTempDir(tempDir, file.getName() + ".intermediate.txt").toFile();
        Files.write(intermediateTxt.toPath(), plainText.getBytes(StandardCharsets.UTF_8));

        File pdf = textRenderer.convertToPdf(intermediateTxt, tempDir);

        File finalPdf = ConverterPaths.resolveWithinTempDir(tempDir, ConverterPaths.basenameWithPdfExtension(file)).toFile();
        if (!pdf.equals(finalPdf)) {
            Files.move(pdf.toPath(), finalPdf.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Files.deleteIfExists(intermediateTxt.toPath());
        return finalPdf;
    }

    private static String extractPlainText(File file) throws IOException {
        RTFEditorKit kit = new RTFEditorKit();
        Document doc = kit.createDefaultDocument();
        try (InputStream in = Files.newInputStream(file.toPath())) {
            kit.read(in, doc, 0);
            return doc.getText(0, doc.getLength());
        } catch (BadLocationException e) {
            throw new IOException("RTF extraction failed for " + file.getName(), e);
        }
    }
}
