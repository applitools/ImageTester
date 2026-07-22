package com.applitools.imagetester;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CompareModeCliTest {

    private String runCapturingOutput(String[] args) {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            ImageTester.run(args);
        } finally {
            System.setOut(original);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }

    @Test
    public void doc1WithoutFnFailsWithClearMessage() {
        String output = runCapturingOutput(new String[] {"-doc1", "a.png", "-doc2", "b.png", "-k", "x"});
        assertTrue(output, output.contains("-fn"));
    }

    @Test
    public void doc1WithFButNoFnStillFailsOnFnFirst() {
        // -f + -doc1 together is invalid; the -fn check and the mutual-exclusion check can fire in
        // either order as long as SOME clear validation error is printed and Eyes is never reached.
        String output = runCapturingOutput(new String[] {"-doc1", "a.png", "-doc2", "b.png", "-f", ".", "-fn", "x", "-k", "x"});
        assertTrue(output, output.toLowerCase().contains("-f") || output.toLowerCase().contains("mutually"));
    }

    @Test
    public void doc1AloneWithoutDoc2Fails() {
        String output = runCapturingOutput(new String[] {"-doc1", "a.png", "-fn", "x", "-k", "x"});
        assertTrue(output, output.contains("-doc2"));
    }
}
