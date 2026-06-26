package com.applitools.imagetester.gui;

import org.junit.After;
import org.junit.Test;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

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
}
