package com.applitools.imagetester.gui;

import java.util.regex.Pattern;

public final class LogRedactor {

    private static final Pattern PROXY_CREDS = Pattern.compile(
        "(https?://)([^/:@\\s]+):([^/@\\s]+)@"
    );

    private LogRedactor() {}

    public static String redact(String line, String apiKey) {
        String out = line;
        if (apiKey != null && !apiKey.isEmpty()) {
            // literal replace (no regex) — apiKey may contain regex metacharacters
            out = out.replace(apiKey, "***");
        }
        out = PROXY_CREDS.matcher(out).replaceAll("$1***:***@");
        return out;
    }
}
