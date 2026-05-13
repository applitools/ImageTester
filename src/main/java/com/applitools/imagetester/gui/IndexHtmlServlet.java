package com.applitools.imagetester.gui;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class IndexHtmlServlet extends HttpServlet {

    private final GuiToken token_;
    private final byte[] cachedHtml_;

    public IndexHtmlServlet(GuiToken token) {
        this.token_ = token;
        this.cachedHtml_ = loadIndexHtml();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html; charset=utf-8");
        resp.setHeader("Cache-Control", "no-store");
        String html = new String(cachedHtml_, StandardCharsets.UTF_8)
            .replace("__GUI_TOKEN__", token_.value());
        resp.getWriter().write(html);
    }

    private static byte[] loadIndexHtml() {
        try (InputStream in = IndexHtmlServlet.class.getResourceAsStream("/web/index.html")) {
            if (in == null) throw new IllegalStateException("index.html not found on classpath at /web/index.html");
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load index.html: " + e.getMessage(), e);
        }
    }
}
