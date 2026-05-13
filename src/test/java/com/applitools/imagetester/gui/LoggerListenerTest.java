package com.applitools.imagetester.gui;

import com.applitools.imagetester.lib.Logger;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LoggerListenerTest {

    @Test
    public void listenerReceivesEveryMessageWrittenToStdout() {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        Logger logger = new Logger(new PrintStream(sink), false);

        List<String> received = new ArrayList<>();
        logger.addListener(received::add);

        logger.printMessage("hello");
        logger.printVersion("1.2.3");

        assertEquals(2, received.size());
        assertEquals("hello", received.get(0));
        assertTrue(received.get(1).contains("ImageTester version 1.2.3"));
        assertTrue("stdout still received output", sink.size() > 0);
    }

    @Test
    public void removedListenerNoLongerReceivesMessages() {
        Logger logger = new Logger(new PrintStream(new ByteArrayOutputStream()), false);
        List<String> received = new ArrayList<>();
        Consumer<String> l = received::add;

        logger.addListener(l);
        logger.printMessage("first");
        logger.removeListener(l);
        logger.printMessage("second");

        assertEquals(1, received.size());
        assertEquals("first", received.get(0));
    }

    @Test
    public void concurrentLoggingAndListenerMutationDoesNotThrow() throws Exception {
        Logger logger = new Logger(new PrintStream(new ByteArrayOutputStream()), false);
        CountDownLatch start = new CountDownLatch(1);

        Thread producer = new Thread(() -> {
            try { start.await(); } catch (InterruptedException ignored) {}
            for (int i = 0; i < 1000; i++) logger.printMessage("msg-" + i);
        });
        Thread mutator = new Thread(() -> {
            try { start.await(); } catch (InterruptedException ignored) {}
            for (int i = 0; i < 1000; i++) {
                Consumer<String> l = s -> { };
                logger.addListener(l);
                logger.removeListener(l);
            }
        });

        producer.start();
        mutator.start();
        start.countDown();
        producer.join(5_000);
        mutator.join(5_000);
        assertTrue(!producer.isAlive() && !mutator.isAlive());
    }
}
