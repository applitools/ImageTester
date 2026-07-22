package com.applitools.imagetester.gui;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.applitools.imagetester.lib.UpdateInfo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

public class UpdateInstallerTest {

    @Rule public TemporaryFolder downloads = new TemporaryFolder();

    private static final byte[] INSTALLER_BYTES = "fake-msi-content".getBytes(StandardCharsets.UTF_8);
    private static final String ASSET = "ImageTester-3.16.0-Windows.msi";

    private static final UpdateInfo INFO = new UpdateInfo(
            "3.16.0", ASSET,
            "https://example.invalid/dl/" + ASSET,
            "https://example.invalid/dl/SHA256SUMS.txt",
            "https://example.invalid/releases/v3.16.0");

    private static String sha256Hex(byte[] data) throws Exception {
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private UpdateInstaller.UrlOpener opener(byte[] installerBytes, String checksums) {
        return url -> {
            byte[] body = url.endsWith("SHA256SUMS.txt")
                    ? checksums.getBytes(StandardCharsets.UTF_8)
                    : installerBytes;
            return new ByteArrayInputStream(body);
        };
    }

    @Test
    public void verifiedDownloadIsRenamedToFinalName() throws Exception {
        String sums = sha256Hex(INSTALLER_BYTES) + "  " + ASSET + "\n";
        UpdateInstaller installer = new UpdateInstaller(opener(INSTALLER_BYTES, sums), p -> {}, downloads.getRoot().toPath());
        Path result = installer.downloadAndLaunch(INFO);
        assertArrayEquals(INSTALLER_BYTES, Files.readAllBytes(result));
    }

    @Test
    public void launcherReceivesTheFinalFileNotThePartFile() throws Exception {
        String sums = sha256Hex(INSTALLER_BYTES) + "  " + ASSET + "\n";
        AtomicReference<Path> launched = new AtomicReference<>();
        UpdateInstaller installer = new UpdateInstaller(opener(INSTALLER_BYTES, sums), launched::set, downloads.getRoot().toPath());
        installer.downloadAndLaunch(INFO);
        assertEquals(ASSET, launched.get().getFileName().toString());
    }

    @Test
    public void checksumMismatchThrows() throws Exception {
        String sums = sha256Hex("different content".getBytes(StandardCharsets.UTF_8)) + "  " + ASSET + "\n";
        UpdateInstaller installer = new UpdateInstaller(opener(INSTALLER_BYTES, sums),
                p -> { throw new AssertionError("must not launch on mismatch"); }, downloads.getRoot().toPath());
        assertThrows(IOException.class, () -> installer.downloadAndLaunch(INFO));
    }

    @Test
    public void checksumMismatchDeletesThePartFile() throws Exception {
        String sums = sha256Hex("different content".getBytes(StandardCharsets.UTF_8)) + "  " + ASSET + "\n";
        UpdateInstaller installer = new UpdateInstaller(opener(INSTALLER_BYTES, sums), p -> {}, downloads.getRoot().toPath());
        try { installer.downloadAndLaunch(INFO); } catch (IOException expected) { /* asserted elsewhere */ }
        assertFalse(Files.exists(downloads.getRoot().toPath().resolve(ASSET + ".part")));
    }

    @Test
    public void missingChecksumEntryThrows() {
        UpdateInstaller installer = new UpdateInstaller(opener(INSTALLER_BYTES, "abc  other-file.msi\n"),
                p -> { throw new AssertionError("must not launch unverified"); }, downloads.getRoot().toPath());
        assertThrows(IOException.class, () -> installer.downloadAndLaunch(INFO));
    }

    @Test
    public void noPartFileRemainsAfterSuccess() throws Exception {
        String sums = sha256Hex(INSTALLER_BYTES) + "  " + ASSET + "\n";
        UpdateInstaller installer = new UpdateInstaller(opener(INSTALLER_BYTES, sums), p -> {}, downloads.getRoot().toPath());
        installer.downloadAndLaunch(INFO);
        assertFalse(Files.exists(downloads.getRoot().toPath().resolve(ASSET + ".part")));
    }
}
