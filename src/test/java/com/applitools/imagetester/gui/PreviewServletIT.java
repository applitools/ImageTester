package com.applitools.imagetester.gui;

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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PreviewServletIT {

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
            "{\"sourcePath\":\"" + escape(dir.getAbsolutePath()) + "\",\"options\":{}}", token);
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
    public void rejectsPathOutsideTheSourceRoot() throws Exception {
        server = GuiServer.startForTest();
        String base = "http://127.0.0.1:" + server.port();
        String token = server.token().value();

        File dir = tmp.newFolder("pix");
        makePng(dir, "sample.png");
        File outside = makePng(tmp.newFolder("elsewhere"), "other.png");

        int runStatus = postJson(base + "/api/run",
            "{\"sourcePath\":\"" + escape(dir.getAbsolutePath()) + "\",\"options\":{}}", token);
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

    private static String escape(String path) {
        return path.replace("\\", "\\\\");
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            Thread.sleep(25);
        }
        assertTrue("condition not met within timeout", condition.getAsBoolean());
    }

    private static int postJson(String url, String body, String token) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }
        return conn.getResponseCode();
    }
}
