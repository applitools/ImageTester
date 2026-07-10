package com.applitools.imagetester.lib;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;

/**
 * Detects the trim rectangle of a print-production PDF page from its crop marks:
 * short, axis-aligned stroked hairlines in the page margins whose positions mark
 * the cut lines. Vector-based (content-stream parsing), so the result is exact in
 * PDF points and independent of render DPI. Returns null whenever the marks do
 * not form an unambiguous rectangle — never guesses.
 */
public final class CropMarkDetector {

    private static final float AXIS_TOLERANCE_PT = 0.5f;
    private static final float CLUSTER_TOLERANCE_PT = 1.0f;
    private static final float MIN_MARK_LENGTH_PT = 4f;
    private static final float MAX_MARK_LENGTH_PT = 40f;
    private static final float MIN_MARGIN_PT = 1f;
    // A trim box smaller than half the page is more likely a false positive than a real cut line.
    private static final float MIN_TRIM_FRACTION = 0.5f;

    private CropMarkDetector() {
    }

    public static PDRectangle detect(PDPage page) {
        List<Segment> segments;
        try {
            SegmentCollector collector = new SegmentCollector(page);
            collector.processPage(page);
            segments = collector.markCandidates();
        } catch (IOException e) {
            return null;
        }

        List<Segment> vertical = new ArrayList<>();
        List<Segment> horizontal = new ArrayList<>();
        for (Segment s : segments) {
            if (s.isVertical()) vertical.add(s);
            else if (s.isHorizontal()) horizontal.add(s);
        }

        List<Cluster> xClusters = pairedClusters(cluster(vertical, true));
        List<Cluster> yClusters = pairedClusters(cluster(horizontal, false));

        // Print files often nest mark sets (bleed marks outside crop marks), so try every
        // edge combination and keep the smallest box that validates: the innermost set of
        // marks that all sit in the margins is the cut line.
        PDRectangle best = null;
        for (Cluster left : xClusters) {
            for (Cluster right : xClusters) {
                if (right.position <= left.position) continue;
                for (Cluster bottom : yClusters) {
                    for (Cluster top : yClusters) {
                        if (top.position <= bottom.position) continue;
                        PDRectangle box = new PDRectangle(
                                left.position, bottom.position,
                                right.position - left.position, top.position - bottom.position);
                        if (!isPlausibleTrimBox(box, page.getMediaBox(), left, right, bottom, top)) continue;
                        if (best == null || area(box) < area(best)) best = box;
                    }
                }
            }
        }
        return best;
    }

    private static float area(PDRectangle box) {
        return box.getWidth() * box.getHeight();
    }

    /** Crop marks always come in pairs per edge — lone segments are content, not marks. */
    private static List<Cluster> pairedClusters(List<Cluster> clusters) {
        List<Cluster> paired = new ArrayList<>();
        for (Cluster c : clusters) {
            if (c.segments.size() >= 2) paired.add(c);
        }
        return paired;
    }

    private static List<Cluster> cluster(List<Segment> segments, boolean byX) {
        List<Cluster> clusters = new ArrayList<>();
        for (Segment s : segments) {
            float position = byX ? s.x1 : s.y1;
            Cluster match = null;
            for (Cluster c : clusters) {
                if (Math.abs(c.position - position) <= CLUSTER_TOLERANCE_PT) { match = c; break; }
            }
            if (match == null) {
                match = new Cluster(position);
                clusters.add(match);
            }
            match.segments.add(s);
        }
        return clusters;
    }

    private static boolean isPlausibleTrimBox(PDRectangle box, PDRectangle media,
                                              Cluster left, Cluster right, Cluster bottom, Cluster top) {
        if (box.getLowerLeftX() < media.getLowerLeftX() + MIN_MARGIN_PT) return false;
        if (box.getLowerLeftY() < media.getLowerLeftY() + MIN_MARGIN_PT) return false;
        if (box.getUpperRightX() > media.getUpperRightX() - MIN_MARGIN_PT) return false;
        if (box.getUpperRightY() > media.getUpperRightY() - MIN_MARGIN_PT) return false;
        if (box.getWidth() < media.getWidth() * MIN_TRIM_FRACTION) return false;
        if (box.getHeight() < media.getHeight() * MIN_TRIM_FRACTION) return false;

        // Real crop marks live in the margins: vertical marks span y outside the box,
        // horizontal marks span x outside it. Anything inside means we mis-clustered.
        for (Cluster c : new Cluster[] { left, right }) {
            for (Segment s : c.segments) {
                if (overlaps(Math.min(s.y1, s.y2), Math.max(s.y1, s.y2),
                        box.getLowerLeftY(), box.getUpperRightY())) return false;
            }
        }
        for (Cluster c : new Cluster[] { bottom, top }) {
            for (Segment s : c.segments) {
                if (overlaps(Math.min(s.x1, s.x2), Math.max(s.x1, s.x2),
                        box.getLowerLeftX(), box.getUpperRightX())) return false;
            }
        }
        return true;
    }

    private static boolean overlaps(float min, float max, float rangeMin, float rangeMax) {
        return max > rangeMin && min < rangeMax;
    }

    private static final class Segment {
        final float x1, y1, x2, y2;

        Segment(float x1, float y1, float x2, float y2) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
        }

        boolean isVertical() { return Math.abs(x1 - x2) <= AXIS_TOLERANCE_PT; }
        boolean isHorizontal() { return Math.abs(y1 - y2) <= AXIS_TOLERANCE_PT; }

        float length() { return (float) Math.hypot(x2 - x1, y2 - y1); }

        boolean isMarkLength() {
            float len = length();
            return len >= MIN_MARK_LENGTH_PT && len <= MAX_MARK_LENGTH_PT;
        }
    }

    /** Replays the content stream and keeps only stroked, mark-length line segments. */
    private static final class SegmentCollector extends PDFGraphicsStreamEngine {

        private final List<Segment> stroked = new ArrayList<>();
        private final List<Segment> pendingPath = new ArrayList<>();
        private Point2D currentPoint = new Point2D.Float();

        SegmentCollector(PDPage page) {
            super(page);
        }

        List<Segment> markCandidates() {
            List<Segment> candidates = new ArrayList<>();
            for (Segment s : stroked) {
                if (s.isMarkLength() && (s.isVertical() || s.isHorizontal())) candidates.add(s);
            }
            return candidates;
        }

        @Override
        public void moveTo(float x, float y) {
            currentPoint = new Point2D.Float(x, y);
        }

        @Override
        public void lineTo(float x, float y) {
            pendingPath.add(new Segment((float) currentPoint.getX(), (float) currentPoint.getY(), x, y));
            currentPoint = new Point2D.Float(x, y);
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
            currentPoint = new Point2D.Float(x3, y3);
        }

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
            currentPoint = p0;
        }

        @Override
        public Point2D getCurrentPoint() {
            return currentPoint;
        }

        @Override
        public void strokePath() {
            stroked.addAll(pendingPath);
            pendingPath.clear();
        }

        @Override
        public void fillPath(int windingRule) {
            pendingPath.clear();
        }

        @Override
        public void fillAndStrokePath(int windingRule) {
            stroked.addAll(pendingPath);
            pendingPath.clear();
        }

        @Override
        public void closePath() {
        }

        @Override
        public void endPath() {
            pendingPath.clear();
        }

        @Override
        public void clip(int windingRule) {
        }

        @Override
        public void drawImage(PDImage image) {
        }

        @Override
        public void shadingFill(COSName shadingName) {
        }
    }

    private static final class Cluster {
        final float position;
        final List<Segment> segments = new ArrayList<>();

        Cluster(float position) {
            this.position = position;
        }
    }
}
