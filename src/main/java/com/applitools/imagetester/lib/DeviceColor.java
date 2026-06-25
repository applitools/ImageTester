package com.applitools.imagetester.lib;

import java.util.List;

import org.apache.pdfbox.cos.COSNumber;

/**
 * Interprets PDF non-stroking color operators (rg/g/k and sc/scn over device
 * color spaces) into normalized RGB. Pattern and named-colorspace operands that
 * can't be read as plain device components leave the color unchanged.
 */
final class DeviceColor {

    private DeviceColor() {
    }

    /** Returns the RGB a color operator sets, or {@code current} if the operator isn't a color op. */
    static float[] fromOperator(String op, List<Object> args, float[] current) {
        switch (op) {
            case "rg":
                return rgbOrCurrent(numbers(args, 3), current);
            case "g":
                float[] g = numbers(args, 1);
                return g == null ? current : new float[] {g[0], g[0], g[0]};
            case "k":
                return cmykToRgb(numbers(args, 4), current);
            case "sc":
            case "scn":
                return fromComponents(args, current);
            default:
                return current;
        }
    }

    private static float[] fromComponents(List<Object> args, float[] current) {
        int n = 0;
        for (int i = args.size() - 1; i >= 0 && args.get(i) instanceof COSNumber; i--) n++;
        switch (n) {
            case 1:
                float v = ((COSNumber) args.get(args.size() - 1)).floatValue();
                return new float[] {v, v, v};
            case 3:
                return rgbOrCurrent(numbers(args, 3), current);
            case 4:
                return cmykToRgb(numbers(args, 4), current);
            default:
                return current;
        }
    }

    private static float[] rgbOrCurrent(float[] vals, float[] current) {
        return vals == null ? current : vals;
    }

    private static float[] cmykToRgb(float[] cmyk, float[] current) {
        if (cmyk == null) return current;
        return new float[] {
                (1f - cmyk[0]) * (1f - cmyk[3]),
                (1f - cmyk[1]) * (1f - cmyk[3]),
                (1f - cmyk[2]) * (1f - cmyk[3])
        };
    }

    /** Returns the last {@code count} numeric operands, or null if too few are present. */
    private static float[] numbers(List<Object> args, int count) {
        if (args.size() < count) return null;
        float[] out = new float[count];
        int start = args.size() - count;
        for (int i = 0; i < count; i++) {
            Object o = args.get(start + i);
            if (!(o instanceof COSNumber)) return null;
            out[i] = ((COSNumber) o).floatValue();
        }
        return out;
    }
}
