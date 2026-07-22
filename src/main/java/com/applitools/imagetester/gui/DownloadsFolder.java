package com.applitools.imagetester.gui;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.function.UnaryOperator;

/**
 * Resolves the user's Downloads folder. On Windows, Downloads can be redirected
 * (OneDrive, folder policy), so the honest answer lives in the User Shell Folders
 * registry key; any failure falls back to ~/Downloads.
 */
final class DownloadsFolder {

    interface CommandRunner {
        String run(String... command) throws IOException;
    }

    /** Windows known-folder GUID for Downloads. */
    private static final String DOWNLOADS_GUID = "{374DE290-123F-4565-9164-39C4925E467B}";
    private static final String USER_SHELL_FOLDERS_KEY =
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\User Shell Folders";

    private DownloadsFolder() {}

    static Path resolve() {
        return resolve(System.getProperty("os.name", ""), System::getenv, DownloadsFolder::runCommand);
    }

    static Path resolve(String osName, UnaryOperator<String> env, CommandRunner reg) {
        if (osName.toLowerCase(Locale.ROOT).contains("win")) {
            try {
                Path fromRegistry = parseRegOutput(
                        reg.run("reg", "query", USER_SHELL_FOLDERS_KEY, "/v", DOWNLOADS_GUID), env);
                if (fromRegistry != null) return fromRegistry;
            } catch (IOException | RuntimeException e) {
                // fall through to the home-dir default
            }
        }
        return Paths.get(System.getProperty("user.home"), "Downloads");
    }

    private static Path parseRegOutput(String output, UnaryOperator<String> env) {
        for (String line : output.split("\\r?\\n")) {
            if (!line.contains(DOWNLOADS_GUID)) continue;
            String[] columns = line.trim().split("\\s{2,}", 3);
            if (columns.length < 3) return null;
            String value = columns[2];
            String profile = env.apply("USERPROFILE");
            if (profile != null) value = value.replace("%USERPROFILE%", profile);
            if (value.contains("%")) return null; // an env token we can't expand
            return Paths.get(value);
        }
        return null;
    }

    private static String runCommand(String... command) throws IOException {
        Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
        try (InputStream in = p.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
