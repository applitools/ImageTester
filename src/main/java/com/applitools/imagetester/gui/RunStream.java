package com.applitools.imagetester.gui;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class RunStream {

    // 16k absorbs verbose Eyes-SDK / PDF upload bursts so log lines aren't shed under load.
    private static final int DEFAULT_CAPACITY = 16384;
    // Replay buffer: enough for a long run's worth of log lines so a tab opened mid-run still sees history.
    private static final int REPLAY_CAPACITY = 16384;
    private static final long LIFECYCLE_PUT_TIMEOUT_MS = 100;

    private final int capacity_;
    private final List<Client> clients_ = new CopyOnWriteArrayList<>();
    private final ObjectMapper json_ = new ObjectMapper();
    private final AtomicInteger droppedLogLines_ = new AtomicInteger();
    private final Deque<SseEvent> replayBuffer_ = new ArrayDeque<>(REPLAY_CAPACITY);

    public RunStream() { this(DEFAULT_CAPACITY); }

    public RunStream(int capacity) { this.capacity_ = capacity; }

    public void addClient(PrintWriter writer, Runnable onClose, CountDownLatch ready) {
        List<SseEvent> snapshot;
        synchronized (replayBuffer_) {
            snapshot = new ArrayList<>(replayBuffer_);
        }
        Client c = new Client(writer, onClose, snapshot);
        clients_.add(c);
        c.start(ready);
    }

    public int activeClientCount() { return clients_.size(); }

    public int droppedLogLineCount() { return droppedLogLines_.get(); }

    public void emit(SseEvent event) {
        synchronized (replayBuffer_) {
            replayBuffer_.addLast(event);
            while (replayBuffer_.size() > REPLAY_CAPACITY) replayBuffer_.removeFirst();
        }
        for (Client c : clients_) c.offer(event);
    }

    /** Wipes replay history; call at the start of a new run so a fresh tab doesn't see the previous run's tail. */
    public void resetReplay() {
        synchronized (replayBuffer_) { replayBuffer_.clear(); }
    }

    public void close() {
        for (Client c : clients_) c.stop();
        clients_.clear();
    }

    private final class Client {
        private final PrintWriter writer_;
        private final Runnable onClose_;
        private final BlockingQueue<SseEvent> queue_ = new ArrayBlockingQueue<>(capacity_);
        private final List<SseEvent> replay_;
        private volatile boolean alive_ = true;
        private Thread drainer_;

        Client(PrintWriter w, Runnable onClose, List<SseEvent> replay) {
            this.writer_ = w;
            this.onClose_ = onClose;
            this.replay_ = replay;
        }

        void start(CountDownLatch ready) {
            drainer_ = new Thread(() -> {
                ready.countDown();
                try {
                    // Replay buffered events first so a freshly-attached client sees the run's history,
                    // then enter the normal poll loop for live events.
                    for (SseEvent ev : replay_) {
                        String line = "data: " + json_.writeValueAsString(ev) + "\n\n";
                        writer_.write(line);
                    }
                    if (!replay_.isEmpty()) writer_.flush();

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
