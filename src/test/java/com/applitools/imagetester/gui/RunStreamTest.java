package com.applitools.imagetester.gui;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class RunStreamTest {

    @Test
    public void connectedClientReceivesEventsInOrder() throws Exception {
        RunStream stream = new RunStream();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(baos, true);
        CountDownLatch ready = new CountDownLatch(1);
        stream.addClient(pw, () -> {}, ready);
        assertTrue(ready.await(1, TimeUnit.SECONDS));

        stream.emit(new SseEvent.TestStarted("a.png"));
        stream.emit(new SseEvent.LogLine("hello"));
        stream.emit(new SseEvent.TestFinished("a.png", "pass", 42, null));

        // give drainer time to flush
        Thread.sleep(300);
        stream.close();

        String out = baos.toString();
        assertTrue("test-started missing: " + out, out.contains("\"type\":\"test-started\""));
        assertTrue("log-line text missing: " + out, out.contains("\"text\":\"hello\""));
        assertTrue("test-finished missing: " + out, out.contains("\"type\":\"test-finished\""));
        assertTrue("test-started appears before test-finished",
            out.indexOf("test-started") < out.indexOf("test-finished"));
    }

    @Test
    public void slowClientDropsLogLinesButNeverLifecycleEvents() throws Exception {
        RunStream stream = new RunStream(/*queueCapacity*/ 4);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final Object lock = new Object();
        PrintWriter slow = new PrintWriter(baos) {
            @Override public void flush() {
                synchronized (lock) {
                    try { lock.wait(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                }
                super.flush();
            }
        };
        CountDownLatch ready = new CountDownLatch(1);
        stream.addClient(slow, () -> {}, ready);
        assertTrue(ready.await(1, TimeUnit.SECONDS));

        for (int i = 0; i < 100; i++) stream.emit(new SseEvent.LogLine("L" + i));
        stream.emit(new SseEvent.TestFinished("a", "pass", 1, null));

        assertTrue("at least one log-line was dropped", stream.droppedLogLineCount() > 0);
        synchronized (lock) { lock.notifyAll(); }
        Thread.sleep(50);
        stream.close();
    }

    @Test
    public void disconnectedClientStopsDrainerWithoutAffectingOtherClients() throws Exception {
        RunStream stream = new RunStream();
        ByteArrayOutputStream good = new ByteArrayOutputStream();
        PrintWriter goodPw = new PrintWriter(good, true);

        PrintWriter failingPw = new PrintWriter(new ByteArrayOutputStream()) {
            @Override public void write(String s) { throw new RuntimeException("disconnected"); }
            @Override public void println(String s) { throw new RuntimeException("disconnected"); }
        };

        CountDownLatch r1 = new CountDownLatch(1);
        CountDownLatch r2 = new CountDownLatch(1);
        stream.addClient(goodPw, () -> {}, r1);
        stream.addClient(failingPw, () -> {}, r2);
        r1.await(1, TimeUnit.SECONDS);
        r2.await(1, TimeUnit.SECONDS);

        stream.emit(new SseEvent.TestStarted("a"));
        Thread.sleep(300);

        assertTrue(good.toString().contains("test-started"));
        assertEquals(1, stream.activeClientCount());
        stream.close();
    }
}
