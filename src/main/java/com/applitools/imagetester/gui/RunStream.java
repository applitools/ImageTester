package com.applitools.imagetester.gui;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class RunStream {

    private static final int DEFAULT_CAPACITY = 1024;
    private static final long LIFECYCLE_PUT_TIMEOUT_MS = 100;

    private final int capacity_;
    private final List<Client> clients_ = new CopyOnWriteArrayList<>();
    private final ObjectMapper json_ = new ObjectMapper();
    private final AtomicInteger droppedLogLines_ = new AtomicInteger();

    public RunStream() { this(DEFAULT_CAPACITY); }

    public RunStream(int capacity) { this.capacity_ = capacity; }

    public void addClient(PrintWriter writer, Runnable onClose, CountDownLatch ready) {
        Client c = new Client(writer, onClose);
        clients_.add(c);
        c.start(ready);
    }

    public int activeClientCount() { return clients_.size(); }

    public int droppedLogLineCount() { return droppedLogLines_.get(); }

    public void emit(SseEvent event) {
        for (Client c : clients_) c.offer(event);
    }

    public void close() {
        for (Client c : clients_) c.stop();
        clients_.clear();
    }

    private final class Client {
        private final PrintWriter writer_;
        private final Runnable onClose_;
        private final BlockingQueue<SseEvent> queue_ = new ArrayBlockingQueue<>(capacity_);
        private volatile boolean alive_ = true;
        private Thread drainer_;

        Client(PrintWriter w, Runnable onClose) {
            this.writer_ = w;
            this.onClose_ = onClose;
        }

        void start(CountDownLatch ready) {
            drainer_ = new Thread(() -> {
                ready.countDown();
                try {
                    while (alive_) {
                        SseEvent ev = queue_.poll(250, TimeUnit.MILLISECONDS);
                        if (ev == null) continue;
                        String line = "data: " + json_.writeValueAsString(ev) + "\n\n";
                        writer_.write(line);
                        writer_.flush();
                    }
                } catch (Throwable t) {
                    // client disconnected or writer threw — prune
                } finally {
                    alive_ = false;
                    clients_.remove(this);
                    try { onClose_.run(); } catch (Throwable ignored) {}
                }
            }, "RunStream-drainer");
            drainer_.setDaemon(true);
            drainer_.start();
        }

        void offer(SseEvent ev) {
            if (!alive_) return;
            if (ev instanceof SseEvent.LogLine) {
                if (!queue_.offer(ev)) droppedLogLines_.incrementAndGet();
            } else {
                try {
                    if (!queue_.offer(ev, LIFECYCLE_PUT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                        queue_.poll();
                        droppedLogLines_.incrementAndGet();
                        queue_.offer(ev);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        void stop() {
            alive_ = false;
            if (drainer_ != null) drainer_.interrupt();
        }
    }
}
