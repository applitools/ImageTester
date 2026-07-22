package com.applitools.imagetester.gui;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Test;

import com.applitools.imagetester.lib.UpdateChecker;
import com.applitools.imagetester.lib.UpdateInfo;

import static org.junit.Assert.*;

public class GuiServerBootIT {

    private GuiServer server;

    @After
    public void tearDown() throws Exception {
        if (server != null) server.stop();
    }

    @Test
    public void bootsAndServesStatus() throws Exception {
        server = GuiServer.startForTest();
        String url = "http://localhost:" + server.port();
        String token = server.token().value();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> resp = client.send(
            HttpRequest.newBuilder(URI.create(url + "/api/status"))
                .header("Authorization", "Bearer " + token)
                .GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue("expected idle in body, got: " + resp.body(), resp.body().contains("idle"));
    }

    @Test
    public void rejectsStatusWithoutToken() throws Exception {
        server = GuiServer.startForTest();
        String url = "http://localhost:" + server.port();

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> resp = client.send(
            HttpRequest.newBuilder(URI.create(url + "/api/status")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(401, resp.statusCode());
    }

    @Test
    public void runWithInvalidOptionReturns400() throws Exception {
        server = GuiServer.startForTest();
        String url = "http://127.0.0.1:" + server.port() + "/api/run";
        String body = "{\"sourcePath\":\".\",\"options\":{\"di\":\"not-a-number\"}}";
        int code = postJson(url, body, server.token().value());
        assertEquals(400, code);
    }

    @Test
    public void runWithRwoButNoRemoveTextReturns400() throws Exception {
        server = GuiServer.startForTest();
        String url = "http://127.0.0.1:" + server.port() + "/api/run";
        String body = "{\"sourcePath\":\".\",\"options\":{\"rwo\":\"/tmp/out\"}}";
        int code = postJson(url, body, server.token().value());
        assertEquals(400, code);
    }

    @Test
    public void updateStatusReportsUnavailableInTestMode() throws Exception {
        server = GuiServer.startForTest();
        String url = "http://127.0.0.1:" + server.port() + "/api/update";

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> resp = client.send(
            HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + server.token().value())
                .GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue("expected \"available\":false in body, got: " + resp.body(),
                resp.body().contains("\"available\":false"));
    }

    @Test
    public void updateInstallWithoutUpdateReturns400() throws Exception {
        server = GuiServer.startForTest();
        String url = "http://127.0.0.1:" + server.port() + "/api/update/install";
        int code = postJson(url, "{}", server.token().value());
        assertEquals(400, code);
    }

    @Test
    public void updateInstallWithPendingUpdateReturns202() throws Exception {
        BlockingInstaller installer = new BlockingInstaller();
        server = GuiServer.startForTest(new UpdateService(checkerWithUpdate(), installer));
        waitForUpdateAvailable(server);

        String url = "http://127.0.0.1:" + server.port() + "/api/update/install";
        int code = postJson(url, "{}", server.token().value());
        assertEquals(202, code);

        installer.release.countDown();
    }

    @Test
    public void secondUpdateInstallWhileDownloadingReturns409() throws Exception {
        BlockingInstaller installer = new BlockingInstaller();
        server = GuiServer.startForTest(new UpdateService(checkerWithUpdate(), installer));
        waitForUpdateAvailable(server);

        String url = "http://127.0.0.1:" + server.port() + "/api/update/install";
        assertEquals(202, postJson(url, "{}", server.token().value()));
        assertTrue("installer never entered", installer.entered.await(5, TimeUnit.SECONDS));
        assertEquals(409, postJson(url, "{}", server.token().value()));

        installer.release.countDown();
    }

    /** Posts a JSON body to the given URL with a Bearer token and returns the HTTP status code. */
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

    /** An UpdateChecker whose check() always reports a pending 3.16.0 update. */
    private static UpdateChecker checkerWithUpdate() {
        String feed = "{\"tag_name\":\"v3.16.0\",\"html_url\":\"https://example.invalid/rel\",\"assets\":["
                + "{\"name\":\"ImageTester-3.16.0-Windows.msi\",\"browser_download_url\":\"https://example.invalid/msi\"},"
                + "{\"name\":\"SHA256SUMS.txt\",\"browser_download_url\":\"https://example.invalid/sums\"}]}";
        return new UpdateChecker(url -> feed, "3.15.0", "Windows 11", "amd64", name -> null);
    }

    /** Polls GET /api/update until the background check has populated a pending update. */
    private static void waitForUpdateAvailable(GuiServer server) throws Exception {
        String url = "http://127.0.0.1:" + server.port() + "/api/update";
        HttpClient client = HttpClient.newHttpClient();
        for (int i = 0; i < 100; i++) {
            HttpResponse<String> resp = client.send(
                HttpRequest.newBuilder(URI.create(url))
                    .header("Authorization", "Bearer " + server.token().value())
                    .GET().build(),
                HttpResponse.BodyHandlers.ofString());
            if (resp.body().contains("\"available\":true")) return;
            Thread.sleep(50);
        }
        throw new AssertionError("update never became available");
    }

    /** Installer whose work blocks until released — lets tests observe the downloading state over HTTP. */
    private static final class BlockingInstaller extends UpdateInstaller {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        BlockingInstaller() {
            super(url -> { throw new AssertionError("unused"); }, p -> {}, java.nio.file.Paths.get("."));
        }

        @Override public java.nio.file.Path downloadAndLaunch(UpdateInfo info) throws IOException {
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return java.nio.file.Paths.get(".");
        }
    }
}
