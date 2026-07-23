package com.applitools.imagetester.gui;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.imgscalr.Scalr;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public final class GuiServer {

    private final Server server_;
    private final GuiToken token_;
    private final int port_;
    private final RunController controller_;
    private final UploadStore uploads_;

    private GuiServer(Server server, GuiToken token, int port, RunController controller, UploadStore uploads) {
        this.server_ = server;
        this.token_ = token;
        this.port_ = port;
        this.controller_ = controller;
        this.uploads_ = uploads;
    }

    public int port() { return port_; }
    public GuiToken token() { return token_; }
    public RunController controller() { return controller_; }

    public static GuiServer start() throws Exception { return start(false, null); }
    public static GuiServer startForTest() throws Exception { return startForTest(UpdateService.disabled()); }

    /** Test-only entry point that lets callers drive real update-service behavior (202/409 paths). */
    public static GuiServer startForTest(UpdateService updates) throws Exception { return start(true, updates); }

    private static GuiServer start(boolean testMode, UpdateService injectedUpdates) throws Exception {
        GuiToken token = GuiToken.generate();
        Server server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setHost("127.0.0.1");
        connector.setPort(0); // any free port
        server.addConnector(connector);

        ServletContextHandler ctx = new ServletContextHandler();
        ctx.setContextPath("/");

        // The filter needs the port, which isn't known until after server.start().
        // Wrap a deferred filter that delegates to the real one once port is known.
        TokenAuthFilter[] filterRef = new TokenAuthFilter[1];
        Filter deferredFilter = new Filter() {
            @Override public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
                    throws IOException, ServletException {
                if (filterRef[0] == null) { chain.doFilter(req, resp); return; }
                filterRef[0].doFilter(req, resp, chain);
            }
        };
        ctx.addFilter(new FilterHolder(deferredFilter), "/*", EnumSet.of(DispatcherType.REQUEST));

        SecretsStore secrets = testMode ? SecretsStore.inMemoryForTest() : SecretsStore.forProduction();
        if (testMode) secrets.setApiKey("sk_test_test");
        RunStream stream = new RunStream();
        RunController controller = new RunController(secrets, stream);
        UploadStore uploads = new UploadStore();

        UpdateService updates = testMode
                ? injectedUpdates
                : new UpdateService(com.applitools.imagetester.lib.UpdateChecker.production(), UpdateInstaller.production());
        updates.startBackgroundCheck();

        ctx.addServlet(new ServletHolder(new IndexHtmlServlet(token)), "/");
        ctx.addServlet(new ServletHolder(new IndexHtmlServlet(token)), "/index.html");
        ctx.addServlet(new ServletHolder(new StaticAssetServlet()), "/assets/*");
        ctx.addServlet(new ServletHolder(new SseServlet(controller)), "/api/events");
        ctx.addServlet(new ServletHolder(new PreviewServlet(controller)), "/api/preview");
        ctx.addServlet(new ServletHolder(new ApiServlet(controller, updates, uploads)), "/api/*");

        server.setHandler(ctx);
        server.start();
        int port = connector.getLocalPort();
        filterRef[0] = new TokenAuthFilter(token, port);

        // Skip in test mode — Swing init can be problematic on headless CI.
        if (!testMode) NativePathChooser.prewarm();

        return new GuiServer(server, token, port, controller, uploads);
    }

    public void stop() throws Exception {
        server_.stop();
        try { uploads_.deleteAll(); } catch (java.io.IOException ignored) { /* temp files; best effort */ }
    }
    public void join() throws InterruptedException { server_.join(); }

    // ---- Inline servlets ----

    private static final class StaticAssetServlet extends HttpServlet {
        @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            String path = req.getPathInfo();
            if (path == null || path.contains("..")) { resp.setStatus(404); return; }
            URL url = StaticAssetServlet.class.getResource("/web/assets" + path);
            if (url == null) { resp.setStatus(404); return; }
            resp.setHeader("Cache-Control", "public,max-age=31536000,immutable");
            if (path.endsWith(".js"))  resp.setContentType("application/javascript");
            if (path.endsWith(".css")) resp.setContentType("text/css");
            try (InputStream in = url.openStream()) { in.transferTo(resp.getOutputStream()); }
        }
    }

    private static final class ApiServlet extends HttpServlet {
        private final RunController controller_;
        private final UpdateService updates_;
        private final UploadStore uploads_;
        private final ObjectMapper json_ = new ObjectMapper();

        ApiServlet(RunController c, UpdateService u, UploadStore up) { this.controller_ = c; this.updates_ = u; this.uploads_ = up; }

        @Override
        protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            String path = req.getPathInfo() != null ? req.getPathInfo() : "";
            String method = req.getMethod();
            try {
                if (method.equals("GET")    && path.equals("/status"))         { writeJson(resp, snapshotToMap(controller_.snapshot())); return; }
                if (method.equals("POST")   && path.equals("/run"))            { handleRun(req, resp); return; }
                if (method.equals("POST")   && path.equals("/cancel"))         { controller_.cancel(); resp.setStatus(204); return; }
                if (method.equals("GET")    && path.equals("/secret/api-key")) { writeJson(resp, Map.of("hasKey", controller_.secrets().hasApiKey())); return; }
                if (method.equals("PUT")    && path.equals("/secret/api-key")) { handleSetSecret(req, resp); return; }
                if (method.equals("DELETE") && path.equals("/secret/api-key")) { controller_.secrets().deleteApiKey(); resp.setStatus(204); return; }
                if (method.equals("POST")   && path.equals("/choose-path"))    { handleChoosePath(req, resp); return; }
                if (method.equals("POST")   && path.equals("/upload"))         { handleUpload(req, resp); return; }
                if (method.equals("GET")    && path.equals("/update"))         { writeJson(resp, updates_.statusJson()); return; }
                if (method.equals("POST")   && path.equals("/update/install")) { updates_.startInstall(); resp.setStatus(202); return; }
                resp.setStatus(404);
            } catch (RunController.RunInProgressException e) {
                resp.setStatus(409); writeJson(resp, Map.of("error", e.getMessage()));
            } catch (UpdateService.InstallInProgressException e) {
                resp.setStatus(409); writeJson(resp, Map.of("error", e.getMessage()));
            } catch (RunController.MissingApiKeyException | SourcePathValidator.InvalidSourceException e) {
                resp.setStatus(400); writeJson(resp, Map.of("error", e.getMessage()));
            } catch (RuntimeException e) {
                // Synchronous option/parse errors from start(): NumberFormatException (-di/-th),
                // bare RuntimeException (Config.setViewport/setCaptureRegion/setProxy),
                // IllegalArgumentException (Config.setProperties), InvalidOptionsException (-rwo guard).
                resp.setStatus(400); writeJson(resp, Map.of("error", e.getMessage() != null ? e.getMessage() : "Invalid options"));
            } catch (Throwable t) {
                resp.setStatus(500); writeJson(resp, Map.of("error", t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()));
            }
        }

        private void handleRun(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            RunRequest runReq = json_.readValue(req.getInputStream(), RunRequest.class);
            RunController.StartResult r = controller_.start(runReq);
            writeJson(resp, Map.of("runId", r.runId));
        }

        private void handleSetSecret(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            Map<?, ?> body = json_.readValue(req.getInputStream(), Map.class);
            controller_.setSecretApiKey(body.get("value") == null ? null : body.get("value").toString());
            resp.setStatus(204);
        }

        private void handleChoosePath(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            Map<?, ?> body = json_.readValue(req.getInputStream(), Map.class);
            String type = body.get("type") == null ? null : body.get("type").toString();
            String start = body.get("start") == null ? null : body.get("start").toString();
            String chosen = "folder".equals(type) ? NativePathChooser.chooseFolder(start) : NativePathChooser.chooseFile(start);
            if (chosen != null) {
                writeJson(resp, Map.of("path", chosen));
            } else {
                writeJson(resp, new HashMap<>());
            }
        }

        private void handleUpload(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            try {
                java.nio.file.Path saved = uploads_.save(req.getParameter("name"), req.getInputStream());
                writeJson(resp, Map.of("path", saved.toString()));
            } catch (UploadStore.InvalidNameException e) {
                resp.setStatus(400); writeJson(resp, Map.of("error", e.getMessage()));
            } catch (UploadStore.TooLargeException e) {
                resp.setStatus(413); writeJson(resp, Map.of("error", e.getMessage()));
            }
        }

        private void writeJson(HttpServletResponse resp, Map<?, ?> body) throws IOException {
            resp.setContentType("application/json");
            json_.writeValue(resp.getOutputStream(), body);
        }

        private Map<String, Object> snapshotToMap(RunState s) {
            if (s instanceof RunState.Idle) return Map.of("kind", "idle");
            if (s instanceof RunState.Running) {
                RunState.Running r = (RunState.Running) s;
                return Map.of("kind", "running", "runId", r.runId, "tests", r.tests);
            }
            if (s instanceof RunState.Done) {
                RunState.Done d = (RunState.Done) s;
                return Map.of("kind", "done", "runId", d.runId, "tests", d.tests,
                              "passed", d.passed, "failed", d.failed, "durationMs", d.durationMs);
            }
            return Map.of("kind", "unknown");
        }
    }

    private static final class SseServlet extends HttpServlet {
        private final RunController controller_;

        SseServlet(RunController c) { this.controller_ = c; }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            resp.setContentType("text/event-stream");
            resp.setCharacterEncoding("UTF-8");
            resp.setHeader("Cache-Control", "no-store");
            resp.flushBuffer();
            AsyncContext async = req.startAsync();
            async.setTimeout(0);
            CountDownLatch ready = new CountDownLatch(1);
            controller_.stream().addClient(resp.getWriter(), () -> async.complete(), ready);
        }
    }

    /** Renders a small thumbnail for a status-row source file (image or first PDF page). */
    private static final class PreviewServlet extends HttpServlet {
        // Sized for a 96px (24 * 4 for hi-DPI) status-row thumbnail — big enough to eyeball
        // the actual page/image content, not just confirm a file loaded.
        private static final int THUMB_MAX_DIMENSION = 400;
        private static final float PDF_RENDER_DPI = 90f;

        private final RunController controller_;

        PreviewServlet(RunController c) { this.controller_ = c; }

        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
            String raw = req.getParameter("path");
            if (raw == null || raw.isEmpty()) { resp.setStatus(400); return; }

            Path resolved;
            try {
                resolved = Paths.get(raw).toAbsolutePath().toRealPath();
            } catch (IOException | java.nio.file.InvalidPathException e) {
                resp.setStatus(404); return;
            }

            // Only serve files under the currently running/last-run source root (folder/file mode)
            // or one of the exact doc1/doc2 paths from the last compare-mode run (compare mode has
            // no shared root — doc1/doc2 can live in unrelated directories) — the path parameter
            // otherwise lets any local client read arbitrary files off disk.
            Path root = controller_.sourceRoot();
            boolean underSourceRoot = root != null && resolved.startsWith(root);
            Set<Path> comparePaths = controller_.compareModePaths();
            boolean isAllowedComparePath = comparePaths != null && comparePaths.contains(resolved);
            if ((!underSourceRoot && !isAllowedComparePath) || !Files.isRegularFile(resolved)) {
                resp.setStatus(403); return;
            }

            BufferedImage thumb;
            try {
                thumb = renderThumbnail(resolved.toFile());
            } catch (Exception e) {
                thumb = null;
            }
            if (thumb == null) { resp.setStatus(404); return; }

            resp.setContentType("image/png");
            resp.setHeader("Cache-Control", "private, max-age=3600");
            ImageIO.write(thumb, "png", resp.getOutputStream());
        }

        private static BufferedImage renderThumbnail(File file) throws IOException {
            String name = file.getName().toLowerCase();
            BufferedImage full;
            if (name.endsWith(".pdf")) {
                try (PDDocument doc = PDDocument.load(file)) {
                    if (doc.getNumberOfPages() == 0) return null;
                    full = new PDFRenderer(doc).renderImageWithDPI(0, PDF_RENDER_DPI);
                }
            } else {
                full = ImageIO.read(file);
            }
            if (full == null) return null;

            int maxSide = Math.max(full.getWidth(), full.getHeight());
            if (maxSide <= THUMB_MAX_DIMENSION) return full;
            return Scalr.resize(full, Scalr.Method.SPEED, Scalr.Mode.AUTOMATIC, THUMB_MAX_DIMENSION);
        }
    }
}
