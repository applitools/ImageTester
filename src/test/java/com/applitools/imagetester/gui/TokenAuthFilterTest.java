package com.applitools.imagetester.gui;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class TokenAuthFilterTest {

    private GuiToken token;
    private TokenAuthFilter filter;
    private HttpServletRequest req;
    private HttpServletResponse resp;
    private FilterChain chain;
    private int port;

    @Before
    public void setUp() {
        token = GuiToken.generate();
        port = 12345;
        filter = new TokenAuthFilter(token, port);
        req = mock(HttpServletRequest.class);
        resp = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        when(req.getServerPort()).thenReturn(port);
        when(req.getHeader("Host")).thenReturn("localhost:" + port);
    }

    @Test
    public void rejectsApiRequestMissingToken() throws Exception {
        when(req.getRequestURI()).thenReturn("/api/status");
        when(req.getHeader("Authorization")).thenReturn(null);
        filter.doFilter(req, resp, chain);
        verify(resp).setStatus(401);
        verify(chain, never()).doFilter(req, resp);
    }

    @Test
    public void rejectsApiRequestWithWrongToken() throws Exception {
        when(req.getRequestURI()).thenReturn("/api/status");
        when(req.getHeader("Authorization")).thenReturn("Bearer not-the-token");
        filter.doFilter(req, resp, chain);
        verify(resp).setStatus(401);
        verify(chain, never()).doFilter(req, resp);
    }

    @Test
    public void acceptsApiRequestWithCorrectToken() throws Exception {
        when(req.getRequestURI()).thenReturn("/api/status");
        when(req.getHeader("Authorization")).thenReturn("Bearer " + token.value());
        filter.doFilter(req, resp, chain);
        verify(chain).doFilter(req, resp);
    }

    @Test
    public void rejectsApiRequestWithWrongHost() throws Exception {
        when(req.getRequestURI()).thenReturn("/api/status");
        when(req.getHeader("Authorization")).thenReturn("Bearer " + token.value());
        when(req.getHeader("Host")).thenReturn("evil.example.com:" + port);
        filter.doFilter(req, resp, chain);
        verify(resp).setStatus(403);
        verify(chain, never()).doFilter(req, resp);
    }

    @Test
    public void rejectsApiRequestWithWrongOrigin() throws Exception {
        when(req.getRequestURI()).thenReturn("/api/status");
        when(req.getHeader("Authorization")).thenReturn("Bearer " + token.value());
        when(req.getHeader("Origin")).thenReturn("http://evil.example.com");
        filter.doFilter(req, resp, chain);
        verify(resp).setStatus(403);
        verify(chain, never()).doFilter(req, resp);
    }

    @Test
    public void allowsNonApiPathsWithoutToken() throws Exception {
        when(req.getRequestURI()).thenReturn("/index.html");
        filter.doFilter(req, resp, chain);
        verify(chain).doFilter(req, resp);
    }

    @Test
    public void sseEndpointAcceptsTokenInQuery() throws Exception {
        when(req.getRequestURI()).thenReturn("/api/events");
        when(req.getParameter("token")).thenReturn(token.value());
        filter.doFilter(req, resp, chain);
        verify(chain).doFilter(req, resp);
    }
}
