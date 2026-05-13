package com.applitools.imagetester.gui;

import org.junit.After;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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
}
