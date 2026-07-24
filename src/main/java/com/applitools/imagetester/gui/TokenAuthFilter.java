package com.applitools.imagetester.gui;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public final class TokenAuthFilter implements Filter {

    private static final String BEARER = "Bearer ";
    private final GuiToken token_;
    private final List<String> allowedHosts_;
    private final List<String> allowedOrigins_;

    public TokenAuthFilter(GuiToken token, int port) {
        this.token_ = token;
        this.allowedHosts_ = Arrays.asList("localhost:" + port, "127.0.0.1:" + port);
        this.allowedOrigins_ = Arrays.asList("http://localhost:" + port, "http://127.0.0.1:" + port);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;
        String uri = req.getRequestURI();

        if (uri == null || !uri.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        String host = req.getHeader("Host");
        if (host == null || !allowedHosts_.contains(host)) {
            resp.setStatus(403);
            return;
        }

        String origin = req.getHeader("Origin");
        if (origin != null && !allowedOrigins_.contains(origin)) {
            resp.setStatus(403);
            return;
        }

        String candidate = extractToken(req);
        if (candidate == null || !token_.verify(candidate)) {
            resp.setStatus(401);
            return;
        }

        chain.doFilter(request, response);
    }

    private static String extractToken(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith(BEARER)) return auth.substring(BEARER.length());
        return req.getParameter("token");
    }
}
