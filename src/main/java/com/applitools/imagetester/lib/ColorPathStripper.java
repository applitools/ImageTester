package com.applitools.imagetester.lib;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

/**
 * Removes filled vector paths drawn in a target non-stroking (fill) color,
 * leaving everything else — body text, images, strokes, clip paths, and fills
 * of any other color — untouched.
 *
 * Watermarks of the kind this targets are stamped as filled outlines in a
 * single muted color distinct from the document's real content, so keying
 * removal on fill color strips the watermark without touching shared branding.
 */
public final class ColorPathStripper {

    private static final Set<String> PATH_CONSTRUCTION_OPS = new HashSet<>(Arrays.asList(
            "m", "l", "c", "v", "y", "h", "re", "W", "W*"));
    private static final Set<String> FILL_PAINT_OPS = new HashSet<>(Arrays.asList(
            "f", "F", "f*", "b", "b*", "B", "B*"));
    private static final Set<String> NON_FILL_PAINT_OPS = new HashSet<>(Arrays.asList(
            "S", "s", "n"));

    private ColorPathStripper() {
    }

    public static void removeFromAllPages(PDDocument doc, float[] targetRgb, float tolerance) throws IOException {
        if (targetRgb == null) return;
        for (int i = 0; i < doc.getNumberOfPages(); i++) {
            PDPage page = doc.getPage(i);
            PDFStreamParser parser = new PDFStreamParser(page);
            parser.parse();
            List<Object> cleaned = strip(parser.getTokens(), targetRgb, tolerance);

            COSStream newStream = new COSStream();
            try (OutputStream out = newStream.createOutputStream()) {
                new ContentStreamWriter(out).writeTokens(cleaned);
            }
            page.getCOSObject().setItem(COSName.CONTENTS, newStream);
        }
    }

    public static List<Object> strip(List<Object> tokens, float[] targetRgb, float tolerance) {
        List<Object> result = new ArrayList<>();
        List<Object> argBuffer = new ArrayList<>();
        List<Object> currentPath = new ArrayList<>();
        boolean inPath = false;

        Deque<float[]> stateStack = new ArrayDeque<>();
        float[] fill = {0f, 0f, 0f};

        for (Object t : tokens) {
            if (!(t instanceof Operator)) {
                argBuffer.add(t);
                continue;
            }
            String op = ((Operator) t).getName();

            if (PATH_CONSTRUCTION_OPS.contains(op)) {
                currentPath.addAll(argBuffer);
                currentPath.add(t);
                argBuffer.clear();
                inPath = true;
            } else if (inPath && (FILL_PAINT_OPS.contains(op) || NON_FILL_PAINT_OPS.contains(op))) {
                currentPath.addAll(argBuffer);
                currentPath.add(t);
                argBuffer.clear();
                boolean drop = FILL_PAINT_OPS.contains(op) && colorMatches(fill, targetRgb, tolerance);
                if (!drop) result.addAll(currentPath);
                currentPath.clear();
                inPath = false;
            } else {
                if (inPath) {
                    result.addAll(currentPath);
                    currentPath.clear();
                    inPath = false;
                }
                fill = applyColorState(op, argBuffer, fill, stateStack);
                result.addAll(argBuffer);
                result.add(t);
                argBuffer.clear();
            }
        }
        result.addAll(currentPath);
        result.addAll(argBuffer);
        return result;
    }

    private static float[] applyColorState(String op, List<Object> args, float[] fill, Deque<float[]> stack) {
        switch (op) {
            case "q":
                stack.push(fill.clone());
                return fill;
            case "Q":
                return stack.isEmpty() ? fill : stack.pop();
            default:
                return DeviceColor.fromOperator(op, args, fill);
        }
    }

    private static boolean colorMatches(float[] fill, float[] target, float tol) {
        if (target == null) return false;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(fill[i] - target[i]) > tol) return false;
        }
        return true;
    }
}
