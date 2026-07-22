package com.applitools.imagetester.gui;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.applitools.imagetester.lib.UpdateChecker;
import com.applitools.imagetester.lib.UpdateInfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class UpdateServiceTest {

    private static final String FEED =
            "{\"tag_name\":\"v3.16.0\",\"html_url\":\"https://example.invalid/rel\",\"assets\":["
            + "{\"name\":\"ImageTester-3.16.0-Windows.msi\",\"browser_download_url\":\"https://example.invalid/msi\"},"
            + "{\"name\":\"SHA256SUMS.txt\",\"browser_download_url\":\"https://example.invalid/sums\"}]}";

    private static UpdateChecker checkerWithUpdate() {
        return new UpdateChecker(url -> FEED, "3.15.0", "Windows 11", "amd64", name -> null);
    }

    private static UpdateChecker checkerWithout() {
        return new UpdateChecker(url -> { throw new IOException("offline"); }, "3.15.0", "Windows 11", "amd64", name -> null);
    }

    /** Installer whose work blocks until released — lets tests observe the downloading state. */
    private static final class BlockingInstaller extends UpdateInstaller {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final boolean fail;
        BlockingInstaller(boolean fail) {
            super(url -> { throw new AssertionError("unused"); }, p -> {}, java.nio.file.Paths.get("."));
            this.fail = fail;
        }
        @Override public java.nio.file.Path downloadAndLaunch(UpdateInfo info) throws IOException {
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (fail) throw new IOException("boom");
            return java.nio.file.Paths.get(".");
        }
    }

    private static UpdateService started(UpdateChecker checker, UpdateInstaller installer) throws Exception {
        UpdateService s = new UpdateService(checker, installer);
        s.startBackgroundCheck();
        for (int i = 0; i < 100 && Boolean.FALSE.equals(s.statusJson().get("available")); i++) Thread.sleep(50);
        return s;
    }

    @Test
    public void noUpdateReportsUnavailableIdle() {
        UpdateService s = new UpdateService(checkerWithout(), new BlockingInstaller(false));
        s.startBackgroundCheck();
        assertEquals(Boolean.FALSE, s.statusJson().get("available"));
    }

    @Test
    public void updateReportsVersion() throws Exception {
        assertEquals("3.16.0", started(checkerWithUpdate(), new BlockingInstaller(false)).statusJson().get("version"));
    }

    @Test
    public void updateWithAssetAndChecksumIsOneClickable() throws Exception {
        assertEquals(Boolean.TRUE, started(checkerWithUpdate(), new BlockingInstaller(false)).statusJson().get("canOneClick"));
    }

    @Test
    public void installMovesStateToDownloading() throws Exception {
        BlockingInstaller installer = new BlockingInstaller(false);
        UpdateService s = started(checkerWithUpdate(), installer);
        s.startInstall();
        assertTrue("installer never entered", installer.entered.await(5, TimeUnit.SECONDS));
        assertEquals("downloading", s.statusJson().get("state"));
        installer.release.countDown();
    }

    @Test
    public void secondInstallWhileDownloadingThrows() throws Exception {
        BlockingInstaller installer = new BlockingInstaller(false);
        UpdateService s = started(checkerWithUpdate(), installer);
        s.startInstall();
        assertTrue(installer.entered.await(5, TimeUnit.SECONDS));
        assertThrows(UpdateService.InstallInProgressException.class, s::startInstall);
        installer.release.countDown();
    }

    @Test
    public void successfulInstallEndsInLaunchedState() throws Exception {
        BlockingInstaller installer = new BlockingInstaller(false);
        UpdateService s = started(checkerWithUpdate(), installer);
        s.startInstall();
        installer.release.countDown();
        for (int i = 0; i < 100 && "downloading".equals(s.statusJson().get("state")); i++) Thread.sleep(50);
        assertEquals("launched", s.statusJson().get("state"));
    }

    @Test
    public void failedInstallEndsInErrorState() throws Exception {
        BlockingInstaller installer = new BlockingInstaller(true);
        UpdateService s = started(checkerWithUpdate(), installer);
        s.startInstall();
        installer.release.countDown();
        for (int i = 0; i < 100 && "downloading".equals(s.statusJson().get("state")); i++) Thread.sleep(50);
        assertEquals("error", s.statusJson().get("state"));
    }

    @Test
    public void freshServiceReportsIdleState() {
        UpdateService s = new UpdateService(checkerWithout(), new BlockingInstaller(false));
        s.startBackgroundCheck();
        assertEquals("idle", s.statusJson().get("state"));
    }

    @Test
    public void disabledServiceReportsUnavailable() {
        assertEquals(Boolean.FALSE, UpdateService.disabled().statusJson().get("available"));
    }

    @Test
    public void installWithoutUpdateThrows() {
        UpdateService s = new UpdateService(checkerWithout(), new BlockingInstaller(false));
        s.startBackgroundCheck();
        assertThrows(IllegalStateException.class, s::startInstall);
    }
}
