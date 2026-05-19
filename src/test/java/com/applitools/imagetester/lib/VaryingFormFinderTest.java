package com.applitools.imagetester.lib;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class VaryingFormFinderTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void identifies_form_whose_position_varies_across_docs() throws IOException {
        // Both docs reference the same FormXObject /Stamp, but doc A draws it
        // at (100,200) and doc B draws it at (300,500). They also reference a
        // /Logo form at the same fixed position in both docs.
        File a = tempFolder.newFile("a.pdf");
        File b = tempFolder.newFile("b.pdf");
        writePdfWithStampAndLogo(a, /*stampX*/ 100f, /*stampY*/ 200f);
        writePdfWithStampAndLogo(b, /*stampX*/ 300f, /*stampY*/ 500f);

        Set<String> varying = VaryingFormFinder.findVarying(Arrays.asList(a, b));

        assertTrue("Stamp form's position varies", varying.contains("Stamp"));
        assertFalse("Logo form's position is constant", varying.contains("Logo"));
    }

    @Test
    public void single_pdf_has_no_varying_forms() throws IOException {
        File a = tempFolder.newFile("a.pdf");
        writePdfWithStampAndLogo(a, 100f, 200f);

        Set<String> varying = VaryingFormFinder.findVarying(Arrays.asList(a));

        assertTrue("With only one PDF, nothing is comparable", varying.isEmpty());
    }

    /**
     * Reproduces the ANG email pattern: a form is regenerated per-document with
     * the same resource name but different drawn content (e.g. the email card
     * whose height stretches to fit a varying body). Such forms must NOT be
     * flagged as watermark stamps, even when their placement differs across docs.
     */
    @Test
    public void form_whose_content_varies_per_doc_is_not_a_stamp() throws IOException {
        File a = tempFolder.newFile("a.pdf");
        File b = tempFolder.newFile("b.pdf");
        writePdfWithRegeneratedTemplate(a, /*templateText*/ "TEMPLATE_A", /*x*/ 100f, /*y*/ 200f);
        writePdfWithRegeneratedTemplate(b, /*templateText*/ "TEMPLATE_B", /*x*/ 300f, /*y*/ 500f);

        Set<String> varying = VaryingFormFinder.findVarying(Arrays.asList(a, b));

        assertFalse("Form with per-doc content is not a stamp", varying.contains("Template"));
    }

    private void writePdfWithRegeneratedTemplate(File file, String templateText, float x, float y)
            throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            page.setResources(new org.apache.pdfbox.pdmodel.PDResources());

            PDFormXObject template = makeForm(doc, templateText);
            page.getResources().put(org.apache.pdfbox.cos.COSName.getPDFName("Template"), template);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page,
                    PDPageContentStream.AppendMode.APPEND, false)) {
                cs.saveGraphicsState();
                cs.transform(org.apache.pdfbox.util.Matrix.getTranslateInstance(x, y));
                cs.drawForm(template);
                cs.restoreGraphicsState();
            }
            doc.save(file);
        }
    }

    private void writePdfWithStampAndLogo(File file, float stampX, float stampY) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            page.setResources(new org.apache.pdfbox.pdmodel.PDResources());

            PDFormXObject stamp = makeForm(doc, "STAMP");
            PDFormXObject logo = makeForm(doc, "LOGO");

            page.getResources().put(org.apache.pdfbox.cos.COSName.getPDFName("Stamp"), stamp);
            page.getResources().put(org.apache.pdfbox.cos.COSName.getPDFName("Logo"), logo);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page,
                    PDPageContentStream.AppendMode.APPEND, false)) {
                // Logo at fixed position 50,50 in both docs
                cs.saveGraphicsState();
                cs.transform(org.apache.pdfbox.util.Matrix.getTranslateInstance(50f, 50f));
                cs.drawForm(logo);
                cs.restoreGraphicsState();

                // Stamp at varying position
                cs.saveGraphicsState();
                cs.transform(org.apache.pdfbox.util.Matrix.getTranslateInstance(stampX, stampY));
                cs.drawForm(stamp);
                cs.restoreGraphicsState();
            }
            doc.save(file);
        }
    }

    private PDFormXObject makeForm(PDDocument doc, String text) throws IOException {
        org.apache.pdfbox.cos.COSStream stream = doc.getDocument().createCOSStream();
        PDFormXObject form = new PDFormXObject(stream);
        form.setBBox(new PDRectangle(0f, 0f, 50f, 30f));
        try (java.io.OutputStream out = stream.createOutputStream()) {
            out.write(("% " + text + "\nq Q\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return form;
    }
}
