package com.applitools.imagetester.lib;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class FormXObjectStripperTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void empties_named_form_content_stream() throws IOException {
        File file = tempFolder.newFile("with-stamp.pdf");
        writePdfWithFormNamedTarget(file, "Stamp", "STAMP CONTENT");

        try (PDDocument doc = PDDocument.load(file)) {
            FormXObjectStripper.emptyForms(doc, Collections.singleton("Stamp"));

            PDPage page = doc.getPage(0);
            PDXObject xo = page.getResources().getXObject(COSName.getPDFName("Stamp"));
            assertTrue(xo instanceof PDFormXObject);
            byte[] bytes = readContentBytes((PDFormXObject) xo);
            assertEquals("Form content should be empty after stripping", 0, bytes.length);
        }
    }

    @Test
    public void leaves_unrelated_forms_untouched() throws IOException {
        File file = tempFolder.newFile("two-forms.pdf");
        writePdfWithTwoForms(file);

        try (PDDocument doc = PDDocument.load(file)) {
            FormXObjectStripper.emptyForms(doc, Collections.singleton("Stamp"));

            PDPage page = doc.getPage(0);
            PDXObject keep = page.getResources().getXObject(COSName.getPDFName("Logo"));
            assertTrue(keep instanceof PDFormXObject);
            byte[] bytes = readContentBytes((PDFormXObject) keep);
            assertTrue("Logo should still have content after stripping Stamp",
                    bytes.length > 0);
        }
    }

    private void writePdfWithFormNamedTarget(File file, String name, String content) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            page.setResources(new org.apache.pdfbox.pdmodel.PDResources());
            PDFormXObject form = makeForm(doc, content);
            page.getResources().put(COSName.getPDFName(name), form);
            doc.save(file);
        }
    }

    private void writePdfWithTwoForms(File file) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            page.setResources(new org.apache.pdfbox.pdmodel.PDResources());
            page.getResources().put(COSName.getPDFName("Stamp"), makeForm(doc, "STAMP"));
            page.getResources().put(COSName.getPDFName("Logo"), makeForm(doc, "LOGO"));
            doc.save(file);
        }
    }

    private PDFormXObject makeForm(PDDocument doc, String content) throws IOException {
        org.apache.pdfbox.cos.COSStream stream = doc.getDocument().createCOSStream();
        PDFormXObject form = new PDFormXObject(stream);
        form.setBBox(new PDRectangle(0f, 0f, 50f, 30f));
        try (java.io.OutputStream out = stream.createOutputStream()) {
            out.write(("% " + content + "\nq Q\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return form;
    }

    private byte[] readContentBytes(PDFormXObject form) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.io.InputStream in = form.getCOSObject().createInputStream()) {
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) > 0) baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }
}
