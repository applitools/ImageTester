package com.applitools.imagetester.gui;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;

public class ShadeServicesSmokeIT {

    @Test
    public void shadedJarStartsCliWithoutClassLoaderErrors() throws Exception {
        String jar = System.getProperty("imagetester.jar", "jars/ImageTester_3.10.0.jar");
        Process p = new ProcessBuilder("java", "-jar", jar).redirectErrorStream(true).start();
        List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) lines.add(line);
        }
        p.waitFor();
        String combined = String.join("\n", lines);
        assertTrue("CLI did not print version line:\n" + combined,
            combined.contains("ImageTester version"));
        assertTrue("CLI surfaced ClassNotFoundException / NoSuchProviderException:\n" + combined,
            !combined.contains("NoSuchProviderException") && !combined.contains("Provider not found"));
    }
}
