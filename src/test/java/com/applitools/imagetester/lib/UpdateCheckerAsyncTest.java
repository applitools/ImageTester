package com.applitools.imagetester.lib;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UpdateCheckerAsyncTest {

    private static String fixture() {
        try (InputStream in = UpdateCheckerAsyncTest.class.getResourceAsStream("/update/latest-release.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("9.9.9", "3.16.0");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void checkAsyncDeliversUpdateOnAnotherThread() throws Exception {
        UpdateChecker c = new UpdateChecker(url -> fixture(), "3.15.0", "Windows 11", "amd64", name -> null);
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<UpdateInfo> seen = new AtomicReference<>();
        c.checkAsync(update -> { seen.set(update); delivered.countDown(); });
        assertTrue("update never delivered", delivered.await(5, TimeUnit.SECONDS));
        assertEquals("3.16.0", seen.get().version);
    }
}
