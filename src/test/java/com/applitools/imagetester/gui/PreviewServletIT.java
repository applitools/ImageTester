package com.applitools.imagetester.gui;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PreviewServletIT {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private GuiServer server;

    @After
    public void tearDown() throws Exception {
        if (server != null) server.stop();
    }

    @Test
    public void servesThumbnailForFileUnderTheRunningSourceRoot() throws Exception {
        server = GuiServer.startForTest();
        String base = "http://127.0.0.1:" + server.port();
        String token = server.token().value();

        File dir = tmp.newFolder("pix");
        File png = makePng(dir, "sample.png");

        int runStatus = postJson(base + "/api/run",
            JSON.writeValueAsString(Map.of("sourcePath", dir.getAbsolutePath(), "options", Map.of())), token);
        assertEquals(200, runStatus);

        // The source root (and each test's preview path) is set synchronously as the Suite
        // discovers files, well before any network call to Eyes resolves — a short wait is enough.
        waitUntil(() -> server.controller().sourceRoot() != null);

        HttpClient client = HttpClient.newHttpClient();
        String previewUrl = base + "/api/preview?path=" + java.net.URLEncoder.encode(png.getAbsolutePath(), "UTF-8")
            + "&token=" + token;
        HttpResponse<byte[]> resp = client.send(
            HttpRequest.newBuilder(URI.create(previewUrl)).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, resp.statusCode());
        assertEquals("image/png", resp.headers().firstValue("Content-Type").orElse(null));
        assertTrue("expected a non-trivial thumbnail body", resp.body().length > 50);
        // A valid PNG can be re-decoded.
        assertTrue(ImageIO.read(new java.io.ByteArrayInputStream(resp.body())) != null);
    }

    @Test
    public void servesBothCompareModeDocsAndRejectsAnUnrelatedPath() throws Exception {
        server = GuiServer.startForTest();
        String base = "http://127.0.0.1:" + server.port();
        String token = server.token().value();

        // doc1 and doc2 deliberately live in unrelated directories — compare mode has no
        // shared root to prefix-check, unlike folder/file mode.
        File doc1 = makePng(tmp.newFolder("doc1"), "doc1.png");
        File doc2 = makePng(tmp.newFolder("doc2"), "doc2.png");
        File unrelated = makePng(tmp.newFolder("elsewhere"), "other.png");

        String body = JSON.writeValueAsString(Map.of(
            "doc1Path", doc1.getAbsolutePath(),
            "doc2Path", doc2.getAbsolutePath(),
            "options", Map.of("fn", "compare-1")));
        int runStatus = postJson(base + "/api/run", body, token);
        assertEquals(200, runStatus);

        // Compare-mode preview authorization is set synchronously at the start of the compare
        // run, before either document's Eyes test actually opens/closes.
        waitUntil(() -> server.controller().compareModePaths() != null);

        assertEquals(200, getPreviewStatus(base, token, doc1));
        assertEquals(200, getPreviewStatus(base, token, doc2));
        assertEquals(403, getPreviewStatus(base, token, unrelated));
    }

    @Test
    public void rejectsPathOutsideTheSourceRoot() throws Exception {
        server = GuiServer.startForTest();
        String base = "http://127.0.0.1:" + server.port();
        String token = server.token().value();

        File dir = tmp.newFolder("pix");
        makePng(dir, "sample.png");
        File outside = makePng(tmp.newFolder("elsewhere"), "other.png");

        int runStatus = postJson(base + "/api/run",
            JSON.writeValueAsString(Map.of("sourcePath", dir.getAbsolutePath(), "options", Map.of())), token);
        assertEquals(200, runStatus);
        waitUntil(() -> server.controller().sourceRoot() != null);

        HttpClient client = HttpClient.newHttpClient();
        String previewUrl = base + "/api/preview?path=" + java.net.URLEncoder.encode(outside.getAbsolutePath(), "UTF-8")
            + "&token=" + token;
        HttpResponse<String> resp = client.send(
            HttpRequest.newBuilder(URI.create(previewUrl)).GET().build(),
            HttpResponse.BodyHandlers.ofString());

        assertEquals(403, resp.statusCode());
    }

    private static File makePng(File folder, String name) throws Exception {
        File png = new File(folder, name);
        BufferedImage img = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 40, 30);
        g.dispose();
        try (FileOutputStream out = new FileOutputStream(png)) {
            ImageIO.write(img, "png", out);
        }
        return png;
    }

    private static int getPreviewStatus(String base, String token, File file) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String previewUrl = base + "/api/preview?path=" + java.net.URLEncoder.encode(file.getAbsolutePath(), "UTF-8")
            + "&token=" + token;
        HttpResponse<String> resp = client.send(
            HttpRequest.newBuilder(URI.create(previewUrl)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        return resp.statusCode();
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        assertTrue("condition not met within timeout", condition.getAsBoolean());
    }

    private static int postJson(String url, String body, String token) throws Exception {
        HttpResponse<Void> resp = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.discarding());
        return resp.statusCode();
    }
}
