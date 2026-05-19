package com.applitools.imagetester.lib;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;

/**
 * Translates a PDF content-stream token list into a list of position-invariant
 * shape hashes — one per drawn path. Two paths with identical operator
 * sequences and identical relative coordinate offsets produce the same hash
 * regardless of where they are stamped on the page.
 */
public final class PathFingerprint {

    private static final Set<String> PATH_OPS = new HashSet<>(Arrays.asList(
            "m", "l", "c", "v", "y", "h", "re"));
    private static final Set<String> PAINT_OPS = new HashSet<>(Arrays.asList(
            "S", "s", "f", "F", "f*", "B", "B*", "b", "b*", "n"));

    private PathFingerprint() {
    }

    public static final class PathHashes {
        public final String coordHash;
        public final String opSeqHash;
        public final int opCount;

        public PathHashes(String coordHash, String opSeqHash, int opCount) {
            this.coordHash = coordHash;
            this.opSeqHash = opSeqHash;
            this.opCount = opCount;
        }
    }

    public static List<String> hashesFor(List<Object> tokens) {
        List<String> hashes = new ArrayList<>();
        for (PathHashes ph : pathHashesFor(tokens)) hashes.add(ph.coordHash);
        return hashes;
    }

    public static List<PathHashes> pathHashesFor(List<Object> tokens) {
        List<PathHashes> hashes = new ArrayList<>();
        List<Object> currentPath = new ArrayList<>();
        List<Object> argBuffer = new ArrayList<>();
        boolean inPath = false;

        for (Object t : tokens) {
            if (t instanceof Operator) {
                String op = ((Operator) t).getName();
                if (PATH_OPS.contains(op)) {
                    currentPath.addAll(argBuffer);
                    currentPath.add(t);
                    inPath = true;
                } else if (PAINT_OPS.contains(op) && inPath) {
                    currentPath.addAll(argBuffer);
                    currentPath.add(t);
                    hashes.add(new PathHashes(hashPath(currentPath), opSeqOf(currentPath), opCount(currentPath)));
                    currentPath.clear();
                    inPath = false;
                } else {
                    currentPath.clear();
                    inPath = false;
                }
                argBuffer.clear();
            } else {
                argBuffer.add(t);
            }
        }
        return hashes;
    }

    private static String opSeqOf(List<Object> path) {
        StringBuilder sb = new StringBuilder();
        for (Object t : path) {
            if (t instanceof Operator) {
                sb.append(((Operator) t).getName()).append(' ');
            }
        }
        return sha256(sb.toString().trim());
    }

    private static int opCount(List<Object> path) {
        int count = 0;
        for (Object t : path) if (t instanceof Operator) count++;
        return count;
    }

    private static String hashPath(List<Object> path) {
        float originX = 0f;
        float originY = 0f;
        boolean originFound = false;

        List<Object> args = new ArrayList<>();
        for (Object t : path) {
            if (t instanceof Operator) {
                String op = ((Operator) t).getName();
                if ("m".equals(op) && args.size() >= 2) {
                    originX = toFloat(args.get(args.size() - 2));
                    originY = toFloat(args.get(args.size() - 1));
                    originFound = true;
                    break;
                }
                args.clear();
            } else {
                args.add(t);
            }
        }

        StringBuilder sb = new StringBuilder();
        args.clear();
        for (Object t : path) {
            if (t instanceof Operator) {
                String op = ((Operator) t).getName();
                for (int k = 0; k < args.size(); k++) {
                    float v = toFloat(args.get(k));
                    if (originFound && isPositional(op, k)) {
                        v -= (k % 2 == 0) ? originX : originY;
                    }
                    sb.append(round2(v)).append(' ');
                }
                sb.append(op).append(' ');
                args.clear();
            } else {
                args.add(t);
            }
        }
        return sha256(sb.toString().trim());
    }

    private static boolean isPositional(String op, int argIndex) {
        if ("re".equals(op)) return argIndex < 2;
        return true;
    }

    private static float toFloat(Object o) {
        if (o instanceof COSFloat) return ((COSFloat) o).floatValue();
        if (o instanceof COSInteger) return ((COSInteger) o).floatValue();
        if (o instanceof Number) return ((Number) o).floatValue();
        throw new IllegalArgumentException("Not a numeric token: " + o);
    }

    private static String round2(float v) {
        float rounded = Math.round(v * 100f) / 100f;
        if (rounded == 0f) rounded = 0f;
        return String.format(Locale.ROOT, "%.2f", rounded);
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
