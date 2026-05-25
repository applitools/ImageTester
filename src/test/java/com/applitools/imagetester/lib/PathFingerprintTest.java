package com.applitools.imagetester.lib;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.junit.Test;

public class PathFingerprintTest {

    @Test
    public void same_shape_at_different_positions_hashes_identically() {
        List<Object> tokensA = pathTokens(100f, 200f, 110f, 210f, 120f, 220f);
        List<Object> tokensB = pathTokens(300f, 400f, 310f, 410f, 320f, 420f);

        List<String> hashesA = PathFingerprint.hashesFor(tokensA);
        List<String> hashesB = PathFingerprint.hashesFor(tokensB);

        assertEquals(1, hashesA.size());
        assertEquals(1, hashesB.size());
        assertEquals(hashesA.get(0), hashesB.get(0));
    }

    @Test
    public void different_shapes_hash_differently() {
        List<Object> straight = pathTokens(0f, 0f, 10f, 0f, 20f, 0f);
        List<Object> diagonal = pathTokens(0f, 0f, 10f, 10f, 20f, 20f);

        List<String> straightHashes = PathFingerprint.hashesFor(straight);
        List<String> diagonalHashes = PathFingerprint.hashesFor(diagonal);

        assertNotEquals(straightHashes.get(0), diagonalHashes.get(0));
    }

    @Test
    public void multiple_paths_yield_one_hash_each() {
        List<Object> tokens = new java.util.ArrayList<>();
        tokens.addAll(pathTokens(0f, 0f, 10f, 10f, 20f, 20f));
        tokens.addAll(pathTokens(50f, 50f, 60f, 60f, 70f, 70f)); // same shape, offset

        List<String> hashes = PathFingerprint.hashesFor(tokens);

        assertEquals(2, hashes.size());
        assertEquals("Same shape at different absolute positions should match",
                hashes.get(0), hashes.get(1));
    }

    /** Builds tokens for a path: m x0 y0 l x1 y1 l x2 y2 S */
    private List<Object> pathTokens(float x0, float y0, float x1, float y1, float x2, float y2) {
        List<Object> tokens = new java.util.ArrayList<>();
        tokens.add(new COSFloat(x0));
        tokens.add(new COSFloat(y0));
        tokens.add(Operator.getOperator("m"));
        tokens.add(new COSFloat(x1));
        tokens.add(new COSFloat(y1));
        tokens.add(Operator.getOperator("l"));
        tokens.add(new COSFloat(x2));
        tokens.add(new COSFloat(y2));
        tokens.add(Operator.getOperator("l"));
        tokens.add(Operator.getOperator("S"));
        return tokens;
    }
}
