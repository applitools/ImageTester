package com.applitools.imagetester.lib;

import com.applitools.imagetester.ImageTester;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * Checks the GitHub releases/latest feed for a newer version and resolves the
 * installer asset for the running OS. Failure policy: every problem (network,
 * JSON, rate limit) returns empty, silently — the tool must never get slower
 * or noisier because this feature exists.
 */
public final class UpdateChecker {

    public interface Fetcher {
        String fetch(String url) throws IOException;
    }

    public static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/applitools/ImageTester/releases/latest";
    public static final String SKIP_ENV = "IMAGETESTER_SKIP_UPDATE_CHECK";
    public static final String CHECKSUM_ASSET = "SHA256SUMS.txt";

    private static final Pattern STRICT_SEMVER = Pattern.compile("\\d+\\.\\d+\\.\\d+");
    private static final int CONNECT_TIMEOUT_MS = 2000;
    private static final int READ_TIMEOUT_MS = 3000;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Fetcher fetcher_;
    private final String currentVersion_;
    private final String osName_;
    private final String osArch_;
    private final UnaryOperator<String> env_;

    /** Public so tests and GUI wiring in other packages can construct it directly. */
    public UpdateChecker(Fetcher fetcher, String currentVersion, String osName, String osArch, UnaryOperator<String> env) {
        this.fetcher_ = fetcher;
        this.currentVersion_ = currentVersion;
        this.osName_ = osName;
        this.osArch_ = osArch;
        this.env_ = env;
    }

    public Optional<UpdateInfo> check() {
        if ("1".equals(env_.apply(SKIP_ENV))) return Optional.empty();
        try {
            JsonNode release = JSON.readTree(fetcher_.fetch(LATEST_RELEASE_URL));
            String remote = release.path("tag_name").asText("").replaceFirst("^v", "");
            if (!isNewer(remote, currentVersion_)) return Optional.empty();

            String assetName = "ImageTester-" + remote + assetSuffix();
            String downloadUrl = assetUrl(release, assetName);
            return Optional.of(new UpdateInfo(
                    remote,
                    assetName,
                    downloadUrl,
                    assetUrl(release, CHECKSUM_ASSET),
                    release.path("html_url").asText("")));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Runs check() on a daemon thread; invokes the consumer only when an update exists. */
    public void checkAsync(Consumer<UpdateInfo> onUpdate) {
        Thread t = new Thread(() -> check().ifPresent(onUpdate), "update-check");
        t.setDaemon(true);
        t.start();
    }

    public static UpdateChecker production() {
        return new UpdateChecker(
                UpdateChecker::httpGet,
                ImageTester.CUR_VER,
                System.getProperty("os.name", ""),
                System.getProperty("os.arch", ""),
                System::getenv);
    }

    private static String httpGet(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        // GitHub's API rejects requests without a User-Agent.
        conn.setRequestProperty("User-Agent", "ImageTester/" + ImageTester.CUR_VER);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        try (InputStream in = conn.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            drainErrorStream(conn);
            throw e;
        } finally {
            conn.disconnect();
        }
    }

    /** Best-effort: draining a non-2xx response body lets the connection be reused by keep-alive pooling. */
    private static void drainErrorStream(HttpURLConnection conn) {
        try (InputStream err = conn.getErrorStream()) {
            if (err != null) err.readAllBytes();
        } catch (IOException ignored) {
            // cleanup only — must never mask the original fetch failure
        }
    }

    private boolean isNewer(String remote, String current) {
        if (!STRICT_SEMVER.matcher(remote).matches() || !STRICT_SEMVER.matcher(current).matches()) return false;
        String[] r = remote.split("\\.");
        String[] c = current.split("\\.");
        for (int i = 0; i < 3; i++) {
            int diff = Integer.parseInt(r[i]) - Integer.parseInt(c[i]);
            if (diff != 0) return diff > 0;
        }
        return false;
    }

    private String assetSuffix() {
        String os = osName_.toLowerCase();
        if (os.contains("win")) return "-Windows.msi";
        if (os.contains("mac")) return "aarch64".equals(osArch_) ? "-macOS-AppleSilicon.dmg" : "-macOS-Intel.dmg";
        return "-Linux.deb";
    }

    private String assetUrl(JsonNode release, String name) {
        for (JsonNode asset : release.path("assets")) {
            if (name.equals(asset.path("name").asText())) return asset.path("browser_download_url").asText("");
        }
        return "";
    }
}
