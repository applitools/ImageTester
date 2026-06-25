package com.applitools.imagetester.lib;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LoggerHeartbeatTest {

    @Test
    public void heartbeatLineContainsNameAndElapsedSeconds() {
        Logger logger = new Logger();
        List<String> captured = new ArrayList<>();
        logger.addListener(captured::add);

        logger.printHeartbeat("lorem_20.pdf", 60);

        assertEquals("Still running... lorem_20.pdf - 60s elapsed \n", captured.get(0));
    }
}
