package com.applitools.imagetester.gui;

import com.applitools.imagetester.lib.UpdateChecker;
import com.applitools.imagetester.lib.UpdateInfo;

import java.util.HashMap;
import java.util.Map;

/** Launch-time update check result plus the install state machine behind /api/update. */
public final class UpdateService {

    public static final class InstallInProgressException extends RuntimeException {
        InstallInProgressException() { super("An update download is already in progress"); }
    }

    private enum State { IDLE, DOWNLOADING, LAUNCHED, ERROR }

    private final UpdateChecker checker_;
    private final UpdateInstaller installer_;

    private volatile UpdateInfo update_ = null;
    private State state_ = State.IDLE;
    private String error_ = null;

    public UpdateService(UpdateChecker checker, UpdateInstaller installer) {
        this.checker_ = checker;
        this.installer_ = installer;
    }

    /** For test mode: never checks, never reports an update. */
    public static UpdateService disabled() {
        return new UpdateService(null, null);
    }

    public void startBackgroundCheck() {
        if (checker_ == null) return;
        checker_.checkAsync(update -> update_ = update);
    }

    public synchronized Map<String, Object> statusJson() {
        Map<String, Object> body = new HashMap<>();
        UpdateInfo u = update_;
        body.put("available", u != null);
        body.put("state", state_.name().toLowerCase());
        if (u != null) {
            body.put("version", u.version);
            body.put("releasePageUrl", u.releasePageUrl);
            body.put("canOneClick", !u.downloadUrl.isEmpty() && !u.checksumUrl.isEmpty());
        } else {
            body.put("canOneClick", false);
        }
        if (error_ != null) body.put("error", error_);
        return body;
    }

    public synchronized void startInstall() {
        UpdateInfo u = update_;
        if (u == null) throw new IllegalStateException("No update available");
        if (state_ == State.DOWNLOADING) throw new InstallInProgressException();
        state_ = State.DOWNLOADING;
        error_ = null;
        Thread worker = new Thread(() -> runInstall(u), "update-install");
        worker.setDaemon(true);
        worker.start();
    }

    private void runInstall(UpdateInfo u) {
        try {
            installer_.downloadAndLaunch(u);
            synchronized (this) { state_ = State.LAUNCHED; }
        } catch (Throwable e) {
            synchronized (this) {
                state_ = State.ERROR;
                error_ = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }
        }
    }
}
