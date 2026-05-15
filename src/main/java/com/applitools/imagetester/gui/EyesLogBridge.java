package com.applitools.imagetester.gui;

import com.applitools.eyes.LogHandler;
import com.applitools.eyes.logging.ClientEvent;
import com.applitools.eyes.logging.TraceLevel;
import com.applitools.imagetester.lib.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Routes Applitools Eyes SDK trace events into our Logger so they reach the GUI log pane
 * (and the listener fan-out) instead of being silently dropped.
 */
public final class EyesLogBridge extends LogHandler {

    private final Logger logger_;
    private final ObjectMapper json_ = new ObjectMapper();

    public EyesLogBridge(Logger logger) {
        super(TraceLevel.Notice);
        this.logger_ = logger;
    }

    @Override public void open() {}
    @Override public void close() {}
    @Override public boolean isOpen() { return true; }

    @Override
    public void onMessageInner(ClientEvent event) {
        try {
            String body;
            Object payload = event.getEvent();
            // Strings render readably; complex events go through JSON so structure isn't lost.
            body = (payload instanceof String) ? (String) payload : json_.writeValueAsString(payload);
            logger_.printMessage(String.format("[eyes %s] %s%n", event.getLevel(), body));
        } catch (Throwable ignored) { /* never let the SDK's log path crash a test */ }
    }
}
