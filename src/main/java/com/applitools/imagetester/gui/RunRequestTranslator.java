package com.applitools.imagetester.gui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RunRequestTranslator {

    // Excluded by decision/necessity: -mp (separate flow), -te (would System.exit the server).
    private static final Set<String> BLOCKED = new HashSet<>();
    static { Collections.addAll(BLOCKED, "mp", "te", "f"); }

    private RunRequestTranslator() {}

    public static String[] toArgv(RunRequest req) {
        List<String> argv = new ArrayList<>();
        if (req.doc1Path != null && req.doc2Path != null) {
            argv.add("-doc1");
            argv.add(req.doc1Path);
            argv.add("-doc2");
            argv.add(req.doc2Path);
        } else if (req.sourcePath != null) {
            argv.add("-f");
            argv.add(req.sourcePath);
        }
        if (req.options != null) {
            for (Map.Entry<String, Object> e : req.options.entrySet()) {
                String flag = e.getKey();
                Object value = e.getValue();
                if (flag == null || BLOCKED.contains(flag) || value == null) continue;
                if (value instanceof Boolean) {
                    if ((Boolean) value) argv.add("-" + flag);
                    continue;
                }
                String s = value.toString();
                if (s.isEmpty()) continue;
                argv.add("-" + flag);
                argv.add(s);
            }
        }
        return argv.toArray(new String[0]);
    }
}
