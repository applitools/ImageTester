package com.applitools.imagetester.lib;

import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;

/**
 * Walks a content-stream token list and records every (cm, Do) stamp site:
 * the FormXObject name being drawn, paired with the most recent transformation
 * matrix that positions it. A `Do` not preceded by a `cm` (or with `q`/`Q`
 * intervening, which discards the matrix) is skipped — its position cannot be
 * compared across documents.
 */
public final class FormStampLocator {

    public static final class StampSite {
        public final String formName;
        public final float[] cm;

        public StampSite(String formName, float[] cm) {
            this.formName = formName;
            this.cm = cm;
        }
    }

    private FormStampLocator() {
    }

    public static List<StampSite> locate(List<Object> tokens) {
        List<StampSite> sites = new ArrayList<>();
        float[] lastCm = null;
        List<Object> argBuffer = new ArrayList<>();

        for (Object t : tokens) {
            if (t instanceof Operator) {
                String op = ((Operator) t).getName();
                if ("cm".equals(op) && argBuffer.size() == 6) {
                    lastCm = new float[6];
                    for (int i = 0; i < 6; i++) lastCm[i] = toFloat(argBuffer.get(i));
                } else if ("Do".equals(op) && argBuffer.size() >= 1) {
                    Object name = argBuffer.get(argBuffer.size() - 1);
                    if (name instanceof COSName && lastCm != null) {
                        sites.add(new StampSite(((COSName) name).getName(), lastCm.clone()));
                    }
                    lastCm = null;
                } else if ("q".equals(op) || "Q".equals(op)) {
                    lastCm = null;
                }
                argBuffer.clear();
            } else {
                argBuffer.add(t);
            }
        }
        return sites;
    }

    private static float toFloat(Object o) {
        if (o instanceof COSFloat) return ((COSFloat) o).floatValue();
        if (o instanceof COSInteger) return ((COSInteger) o).floatValue();
        if (o instanceof Number) return ((Number) o).floatValue();
        throw new IllegalArgumentException("Not numeric: " + o);
    }
}
