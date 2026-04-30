package com.applitools.imagetester.lib.converters;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public interface ProcessRunner {

    int run(List<String> command, long timeoutSeconds) throws IOException;

    /**
     * Returns the runner best suited to the current OS. On Windows this wraps
     * child processes in a hidden desktop so cross-process system modals (e.g.
     * the Print Spooler's "Please wait for printer connection" dialog that
     * soffice.exe triggers at startup) never surface on the user's screen.
     * On any other OS, or if the hidden-desktop setup fails, falls back to
     * the plain {@link Default} runner.
     */
    static ProcessRunner forPlatform() {
        if (isWindows()) {
            try {
                return new WindowsHiddenDesktopRunner();
            } catch (Throwable t) {
                System.err.println("[ImageTester] hidden desktop unavailable; "
                        + "falling back to default process launcher: " + t.getMessage());
            }
        }
        return new Default();
    }

    static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    final class Default implements ProcessRunner {
        @Override public int run(List<String> command, long timeoutSeconds) throws IOException {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try {
                boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return -1;
                }
                return process.exitValue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                throw new IOException("Interrupted waiting for " + command.get(0), e);
            }
        }
    }
}
