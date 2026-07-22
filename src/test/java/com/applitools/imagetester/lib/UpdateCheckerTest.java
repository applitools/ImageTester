package com.applitools.imagetester.lib;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.UnaryOperator;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateCheckerTest {

    private static final UnaryOperator<String> NO_ENV = name -> null;

    private static String fixture(String version) {
        try (InputStream in = UpdateCheckerTest.class.getResourceAsStream("/update/latest-release.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("9.9.9", version);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static UpdateChecker checker(String feedJson, String current, String osName, String osArch) {
        return new UpdateChecker(url -> feedJson, current, osName, osArch, NO_ENV);
    }

    @Test
    public void newerRemoteVersionYieldsUpdate() {
        Optional<UpdateInfo> r = checker(fixture("3.16.0"), "3.15.0", "Windows 11", "amd64").check();
        assertEquals("3.16.0", r.get().version);
    }

    @Test
    public void equalVersionYieldsEmpty() {
        assertFalse(checker(fixture("3.15.0"), "3.15.0", "Windows 11", "amd64").check().isPresent());
    }

    @Test
    public void olderRemoteVersionYieldsEmpty() {
        assertFalse(checker(fixture("3.14.2"), "3.15.0", "Windows 11", "amd64").check().isPresent());
    }

    @Test
    public void twoDigitSegmentsCompareNumericallyNotLexically() {
        assertTrue(checker(fixture("3.100.0"), "3.15.0", "Windows 11", "amd64").check().isPresent());
    }

    @Test
    public void malformedRemoteTagYieldsEmpty() {
        assertFalse(checker(fixture("3.16.0-rc1"), "3.15.0", "Windows 11", "amd64").check().isPresent());
    }

    @Test
    public void windowsPicksMsiAsset() {
        Optional<UpdateInfo> r = checker(fixture("3.16.0"), "3.15.0", "Windows 11", "amd64").check();
        assertEquals("ImageTester-3.16.0-Windows.msi", r.get().assetName);
    }

    @Test
    public void macArmPicksAppleSiliconDmg() {
        Optional<UpdateInfo> r = checker(fixture("3.16.0"), "3.15.0", "Mac OS X", "aarch64").check();
        assertEquals("ImageTester-3.16.0-macOS-AppleSilicon.dmg", r.get().assetName);
    }

    @Test
    public void macIntelPicksIntelDmg() {
        Optional<UpdateInfo> r = checker(fixture("3.16.0"), "3.15.0", "Mac OS X", "x86_64").check();
        assertEquals("ImageTester-3.16.0-macOS-Intel.dmg", r.get().assetName);
    }

    @Test
    public void linuxPicksDeb() {
        Optional<UpdateInfo> r = checker(fixture("3.16.0"), "3.15.0", "Linux", "amd64").check();
        assertEquals("ImageTester-3.16.0-Linux.deb", r.get().assetName);
    }

    @Test
    public void missingPlatformAssetYieldsEmptyDownloadUrl() {
        String feed = fixture("3.16.0").replace("ImageTester-3.16.0-Windows.msi", "renamed-away.msi");
        Optional<UpdateInfo> r = checker(feed, "3.15.0", "Windows 11", "amd64").check();
        assertEquals("", r.get().downloadUrl);
    }

    @Test
    public void missingChecksumAssetYieldsEmptyChecksumUrl() {
        String feed = fixture("3.16.0").replace("SHA256SUMS.txt", "NOPE.txt");
        Optional<UpdateInfo> r = checker(feed, "3.15.0", "Windows 11", "amd64").check();
        assertEquals("", r.get().checksumUrl);
    }

    @Test
    public void releasePageUrlComesFromHtmlUrl() {
        Optional<UpdateInfo> r = checker(fixture("3.16.0"), "3.15.0", "Windows 11", "amd64").check();
        assertEquals("https://github.com/applitools/ImageTester/releases/tag/v3.16.0", r.get().releasePageUrl);
    }

    @Test
    public void skipEnvShortCircuitsBeforeFetch() {
        UpdateChecker c = new UpdateChecker(
                url -> { throw new AssertionError("fetch must not run when skip env is set"); },
                "3.15.0", "Windows 11", "amd64",
                name -> UpdateChecker.SKIP_ENV.equals(name) ? "1" : null);
        assertFalse(c.check().isPresent());
    }

    @Test
    public void fetcherFailureYieldsEmpty() {
        UpdateChecker c = new UpdateChecker(
                url -> { throw new IOException("offline"); },
                "3.15.0", "Windows 11", "amd64", NO_ENV);
        assertFalse(c.check().isPresent());
    }

    @Test
    public void garbageJsonYieldsEmpty() {
        assertFalse(checker("<html>rate limited</html>", "3.15.0", "Windows 11", "amd64").check().isPresent());
    }
}
