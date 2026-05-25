package com.applitools.imagetester.lib.converters;

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class XlsxWatermarkStamperTest {

    @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

    private static final Path PERM_WAT = Paths.get("TestData", "PermWat.xlsx");
    private static final int EXPECTED_WATERMARK_WIDTH = 750;
    private static final int EXPECTED_WATERMARK_HEIGHT = 600;

    @Test
    public void extractsHeaderFooterWatermarkFromPermWat() throws Exception {
        assertTrue("Test fixture missing: " + PERM_WAT, Files.isRegularFile(PERM_WAT));

        Optional<XlsxWatermarkStamper.Watermark> wm =
                new XlsxWatermarkStamper().extractWatermark(PERM_WAT.toFile());

        assertTrue("expected to find a header/footer watermark", wm.isPresent());
        assertNotNull(wm.get().imageBytes);
        assertTrue("image bytes should be non-empty", wm.get().imageBytes.length > 0);
        assertTrue("VML width should be reported in points", wm.get().widthPt > 0f);
        assertTrue("VML height should be reported in points", wm.get().heightPt > 0f);
    }

    @Test
    public void returnsEmptyForXlsxWithoutHeaderFooterPicture() throws Exception {
        File blank = createBlankXlsx(tempFolder.newFile("plain.xlsx"));

        Optional<XlsxWatermarkStamper.Watermark> wm =
                new XlsxWatermarkStamper().extractWatermark(blank);

        assertEquals(Optional.empty(), wm);
    }

    @Test
    public void stampIfPresentPassesThroughWhenNoWatermark() throws Exception {
        File blank = createBlankXlsx(tempFolder.newFile("plain.xlsx"));
        File pdf = writeMinimalPdf(tempFolder.newFile("plain.pdf"));

        File result = new XlsxWatermarkStamper()
                .stampIfPresent(blank, pdf, tempFolder.getRoot().toPath());

        assertSame("no watermark -> original pdf returned unchanged", pdf, result);
    }

    @Test
    public void stampsWatermarkOnEveryPageOfConvertedPdf() throws Exception {
        assertTrue("Test fixture missing: " + PERM_WAT, Files.isRegularFile(PERM_WAT));
        File source = writeMultiPagePdf(tempFolder.newFile("converted.pdf"), 6);

        File result = new XlsxWatermarkStamper()
                .stampIfPresent(PERM_WAT.toFile(), source, tempFolder.getRoot().toPath());

        assertNotEquals("stamped pdf should be distinct from input", source, result);
        try (PDDocument doc = PDDocument.load(result)) {
            assertEquals(6, doc.getNumberOfPages());
            for (PDPage page : doc.getPages()) {
                assertTrue("expected the watermark XObject on every page",
                        hasImageMatching(page, EXPECTED_WATERMARK_WIDTH, EXPECTED_WATERMARK_HEIGHT));
            }
        }
    }

    private static boolean hasImageMatching(PDPage page, int width, int height) throws Exception {
        PDResources resources = page.getResources();
        if (resources == null) return false;
        for (COSName name : resources.getXObjectNames()) {
            PDXObject xobj = resources.getXObject(name);
            if (containsImageMatching(xobj, width, height)) return true;
        }
        return false;
    }

    private static boolean containsImageMatching(PDXObject xobj, int width, int height) throws Exception {
        if (xobj instanceof PDImageXObject) {
            PDImageXObject img = (PDImageXObject) xobj;
            return img.getWidth() == width && img.getHeight() == height;
        }
        if (xobj instanceof org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject) {
            org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject form =
                    (org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject) xobj;
            PDResources nested = form.getResources();
            if (nested == null) return false;
            for (COSName name : nested.getXObjectNames()) {
                if (containsImageMatching(nested.getXObject(name), width, height)) return true;
            }
        }
        return false;
    }

    private static File createBlankXlsx(File target) throws Exception {
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(
                new java.io.FileOutputStream(target))) {
            writeEntry(zip, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                  + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                  + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                  + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                  + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                  + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                  + "</Types>");
            writeEntry(zip, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                  + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                  + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                  + "</Relationships>");
            writeEntry(zip, "xl/workbook.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                  + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                  + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                  + "<sheets><sheet name=\"Sheet1\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                  + "</workbook>");
            writeEntry(zip, "xl/worksheets/sheet1.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                  + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                  + "<sheetData/>"
                  + "</worksheet>");
        }
        return target;
    }

    private static void writeEntry(java.util.zip.ZipOutputStream zip, String name, String content)
            throws Exception {
        zip.putNextEntry(new java.util.zip.ZipEntry(name));
        zip.write(content.getBytes("UTF-8"));
        zip.closeEntry();
    }

    private static File writeMinimalPdf(File target) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(target);
        }
        return target;
    }

    private static File writeMultiPagePdf(File target, int pages) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) doc.addPage(new PDPage());
            doc.save(target);
        }
        return target;
    }
}
