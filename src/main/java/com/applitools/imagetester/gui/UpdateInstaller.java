package com.applitools.imagetester.gui;

import com.applitools.imagetester.lib.UpdateInfo;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Downloads an installer into the user's Downloads folder (NOT temp: enterprise
 * lockdown tooling commonly blocks executing from %TEMP%, while Downloads is the
 * path every existing install already proved allowed), verifies it against the
 * release's SHA256SUMS.txt, then hands off to the OS installer UI.
 *
 * The file streams to "<name>.part" and is renamed only after the digest matches,
 * so a partial or unverified file never exists under a runnable name.
 */
public class UpdateInstaller {

    public interface UrlOpener {
        InputStream open(String url) throws IOException;
    }

    public interface AppLauncher {
        void launch(Path installer) throws IOException;
    }

    private static final String PART_SUFFIX = ".part";
    private static final int DOWNLOAD_CONNECT_TIMEOUT_MS = 5000;
    private static final int DOWNLOAD_READ_TIMEOUT_MS = 60000;

    private final UrlOpener opener_;
    private final AppLauncher launcher_;
    private final Path downloadsDir_;

    public UpdateInstaller(UrlOpener opener, AppLauncher launcher, Path downloadsDir) {
        this.opener_ = opener;
        this.launcher_ = launcher;
        this.downloadsDir_ = downloadsDir;
    }

    public static UpdateInstaller production() {
        return new UpdateInstaller(UpdateInstaller::openUrl, UpdateInstaller::launchInstaller, DownloadsFolder.resolve());
    }

    public Path downloadAndLaunch(UpdateInfo info) throws IOException {
        String expectedDigest = digestFor(info);
        Path part = downloadsDir_.resolve(info.assetName + PART_SUFFIX);
        try {
            try (InputStream in = opener_.open(info.downloadUrl)) {
                Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
            }
            String actual = sha256Hex(part);
            if (!expectedDigest.equalsIgnoreCase(actual)) {
                throw new IOException("Checksum mismatch for " + info.assetName
                        + ": expected " + expectedDigest + ", got " + actual);
            }
            Path installer = downloadsDir_.resolve(info.assetName);
            Files.move(part, installer, StandardCopyOption.REPLACE_EXISTING);
            launcher_.launch(installer);
            return installer;
        } finally {
            Files.deleteIfExists(part);
        }
    }

    private String digestFor(UpdateInfo info) throws IOException {
        String sums;
        try (InputStream in = opener_.open(info.checksumUrl)) {
            sums = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        for (String line : sums.split("\\r?\\n")) {
            String[] parts = line.trim().split("\\s+", 2);
            if (parts.length == 2 && parts[1].equals(info.assetName)) return parts[0];
        }
        throw new IOException("No SHA256SUMS.txt entry for " + info.assetName);
    }

    private static String sha256Hex(Path file) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 unavailable", e);
        }
    }

    private static InputStream openUrl(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(DOWNLOAD_CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(DOWNLOAD_READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(true);
        return conn.getInputStream();
    }

    private static void launchInstaller(Path installer) throws IOException {
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            new ProcessBuilder("msiexec", "/i", installer.toString()).start();
        } else {
            Desktop.getDesktop().open(installer.toFile());
        }
    }
}
