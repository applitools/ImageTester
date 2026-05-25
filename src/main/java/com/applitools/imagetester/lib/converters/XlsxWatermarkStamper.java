package com.applitools.imagetester.lib.converters;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Re-stamps Excel header/footer pictures onto a LibreOffice-produced PDF.
 *
 * LibreOffice's headless xlsx->pdf conversion silently drops VML header/footer
 * graphics (the legacy mechanism Excel uses for "watermarks"). We pull the
 * picture directly out of the .xlsx package and draw it on every page so the
 * downstream visual comparison sees what Excel would actually print.
 */
public class XlsxWatermarkStamper {

    private static final String NS_RELATIONSHIPS =
            "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final String NS_PACKAGE_RELS =
            "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String NS_SHEETML =
            "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String NS_VML = "urn:schemas-microsoft-com:vml";
    private static final String NS_VML_OFFICE = "urn:schemas-microsoft-com:office:office";

    private static final String GRAPHIC_TOKEN = "&G";
    private static final String SHEETS_PREFIX = "xl/worksheets/";
    private static final String SHEET_SUFFIX = ".xml";
    private static final float SCALE_TO_FIT_RATIO = 0.75f;

    private static final Pattern VML_STYLE_DIMENSION =
            Pattern.compile("(width|height)\\s*:\\s*([0-9.]+)\\s*pt", Pattern.CASE_INSENSITIVE);

    public File stampIfPresent(File xlsx, File pdfIn, Path tempDir) throws IOException {
        Optional<Watermark> watermark = extractWatermark(xlsx);
        if (!watermark.isPresent()) return pdfIn;

        File pdfOut = tempDir.resolve(stampedFilename(pdfIn)).toFile();
        stamp(pdfIn, watermark.get(), pdfOut);
        return pdfOut;
    }

    Optional<Watermark> extractWatermark(File xlsx) throws IOException {
        try (ZipFile zip = new ZipFile(xlsx)) {
            for (String sheetPath : listSheets(zip)) {
                Optional<Watermark> found = resolveForSheet(zip, sheetPath);
                if (found.isPresent()) return found;
            }
        }
        return Optional.empty();
    }

    private Optional<Watermark> resolveForSheet(ZipFile zip, String sheetPath) throws IOException {
        Document sheet = parseEntry(zip, sheetPath);
        if (sheet == null) return Optional.empty();

        Element headerFooter = firstChild(sheet.getDocumentElement(), NS_SHEETML, "headerFooter");
        Element legacyHf = firstChild(sheet.getDocumentElement(), NS_SHEETML, "legacyDrawingHF");
        if (headerFooter == null || legacyHf == null || !hasGraphicToken(headerFooter)) {
            return Optional.empty();
        }

        String legacyRid = legacyHf.getAttributeNS(NS_RELATIONSHIPS, "id");
        if (legacyRid.isEmpty()) return Optional.empty();

        String sheetDir = posixDir(sheetPath);
        String sheetRels = sheetDir + "/_rels/" + filename(sheetPath) + ".rels";
        String vmlPath = resolveRelationship(zip, sheetRels, legacyRid, sheetDir);
        if (vmlPath == null) return Optional.empty();

        VmlReference ref = parseVml(zip, vmlPath);
        if (ref == null) return Optional.empty();

        String vmlDir = posixDir(vmlPath);
        String vmlRels = vmlDir + "/_rels/" + filename(vmlPath) + ".rels";
        String imagePath = resolveRelationship(zip, vmlRels, ref.relid, vmlDir);
        if (imagePath == null) return Optional.empty();

        byte[] imageBytes = readEntry(zip, imagePath);
        if (imageBytes == null) return Optional.empty();

        return Optional.of(new Watermark(imageBytes, ref.widthPt, ref.heightPt));
    }

    private static List<String> listSheets(ZipFile zip) {
        List<String> out = new ArrayList<String>();
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (name.startsWith(SHEETS_PREFIX) && name.endsWith(SHEET_SUFFIX)
                    && !name.contains("/_rels/")) {
                out.add(name);
            }
        }
        return out;
    }

    private static boolean hasGraphicToken(Element headerFooter) {
        NodeList children = headerFooter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && child.getTextContent() != null
                    && child.getTextContent().contains(GRAPHIC_TOKEN)) {
                return true;
            }
        }
        return false;
    }

    private static String resolveRelationship(ZipFile zip, String relsPath, String rid, String baseDir)
            throws IOException {
        Document rels = parseEntry(zip, relsPath);
        if (rels == null) return null;

        NodeList rs = rels.getDocumentElement().getElementsByTagNameNS(NS_PACKAGE_RELS, "Relationship");
        for (int i = 0; i < rs.getLength(); i++) {
            Element rel = (Element) rs.item(i);
            if (rid.equals(rel.getAttribute("Id"))) {
                return normalizePath(baseDir, rel.getAttribute("Target"));
            }
        }
        return null;
    }

    private static VmlReference parseVml(ZipFile zip, String vmlPath) throws IOException {
        Document vml = parseEntry(zip, vmlPath);
        if (vml == null) return null;

        NodeList shapes = vml.getElementsByTagNameNS(NS_VML, "shape");
        for (int i = 0; i < shapes.getLength(); i++) {
            Element shape = (Element) shapes.item(i);
            NodeList imageData = shape.getElementsByTagNameNS(NS_VML, "imagedata");
            if (imageData.getLength() == 0) continue;
            String relid = ((Element) imageData.item(0)).getAttributeNS(NS_VML_OFFICE, "relid");
            if (relid.isEmpty()) continue;

            float[] dims = parseStyleDimensions(shape.getAttribute("style"));
            return new VmlReference(relid, dims[0], dims[1]);
        }
        return null;
    }

    private static float[] parseStyleDimensions(String style) {
        float width = 0f, height = 0f;
        Matcher m = VML_STYLE_DIMENSION.matcher(style == null ? "" : style);
        while (m.find()) {
            float value = Float.parseFloat(m.group(2));
            if ("width".equalsIgnoreCase(m.group(1))) width = value;
            else height = value;
        }
        return new float[]{width, height};
    }

    private void stamp(File pdfIn, Watermark watermark, File pdfOut) throws IOException {
        try (PDDocument doc = PDDocument.load(pdfIn)) {
            PDImageXObject image = PDImageXObject.createFromByteArray(
                    doc, watermark.imageBytes, "watermark");
            for (PDPage page : doc.getPages()) {
                drawCentered(doc, page, image, watermark);
            }
            doc.save(pdfOut);
        }
    }

    private static void drawCentered(PDDocument doc, PDPage page, PDImageXObject image,
                                     Watermark watermark) throws IOException {
        PDRectangle box = page.getMediaBox();
        float pageW = box.getWidth();
        float pageH = box.getHeight();
        float[] drawSize = drawSize(image, watermark, pageW, pageH);
        float x = (pageW - drawSize[0]) / 2f + box.getLowerLeftX();
        float y = (pageH - drawSize[1]) / 2f + box.getLowerLeftY();

        try (PDPageContentStream cs = new PDPageContentStream(
                doc, page, AppendMode.APPEND, true, true)) {
            cs.drawImage(image, x, y, drawSize[0], drawSize[1]);
        }
    }

    private static float[] drawSize(PDImageXObject image, Watermark watermark,
                                    float pageW, float pageH) {
        if (watermark.widthPt > 0f && watermark.heightPt > 0f) {
            return new float[]{watermark.widthPt, watermark.heightPt};
        }
        float scale = Math.min(
                (pageW * SCALE_TO_FIT_RATIO) / image.getWidth(),
                (pageH * SCALE_TO_FIT_RATIO) / image.getHeight());
        return new float[]{image.getWidth() * scale, image.getHeight() * scale};
    }

    private static Document parseEntry(ZipFile zip, String path) throws IOException {
        byte[] bytes = readEntry(zip, path);
        if (bytes == null) return null;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            try (InputStream in = new ByteArrayInputStream(bytes)) {
                return builder.parse(in);
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse " + path + ": " + e.getMessage(), e);
        }
    }

    private static byte[] readEntry(ZipFile zip, String path) throws IOException {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null) return null;
        try (InputStream in = zip.getInputStream(entry)) {
            return readAllBytes(in);
        }
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) > 0) {
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
    }

    private static Element firstChild(Element parent, String ns, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE
                    && ns.equals(n.getNamespaceURI())
                    && localName.equals(n.getLocalName())) {
                return (Element) n;
            }
        }
        return null;
    }

    private static String normalizePath(String baseDir, String target) {
        List<String> parts = new ArrayList<String>();
        for (String segment : baseDir.split("/")) parts.add(segment);
        for (String segment : (target == null ? "" : target).split("/")) parts.add(segment);

        List<String> resolved = new ArrayList<String>();
        for (String segment : parts) {
            if (segment.isEmpty() || ".".equals(segment)) continue;
            if ("..".equals(segment)) {
                if (!resolved.isEmpty()) resolved.remove(resolved.size() - 1);
                continue;
            }
            resolved.add(segment);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < resolved.size(); i++) {
            if (i > 0) out.append('/');
            out.append(resolved.get(i));
        }
        return out.toString();
    }

    private static String posixDir(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    private static String filename(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private static String stampedFilename(File pdfIn) {
        String name = pdfIn.getName();
        int dot = name.toLowerCase(Locale.ROOT).lastIndexOf(".pdf");
        String stem = dot > 0 ? name.substring(0, dot) : name;
        return stem + "-wm.pdf";
    }

    static final class Watermark {
        final byte[] imageBytes;
        final float widthPt;
        final float heightPt;

        Watermark(byte[] imageBytes, float widthPt, float heightPt) {
            this.imageBytes = imageBytes;
            this.widthPt = widthPt;
            this.heightPt = heightPt;
        }
    }

    private static final class VmlReference {
        final String relid;
        final float widthPt;
        final float heightPt;

        VmlReference(String relid, float widthPt, float heightPt) {
            this.relid = relid;
            this.widthPt = widthPt;
            this.heightPt = heightPt;
        }
    }
}
