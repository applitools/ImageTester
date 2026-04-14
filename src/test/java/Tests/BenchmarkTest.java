package Tests;

import com.applitools.imagetester.ImageTester;
import org.junit.Assume;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Split-mode throughput benchmark. Generates N synthetic PNGs, runs ImageTester
 * in split mode (each image = 1 test) with two thread counts, reports wall time.
 *
 *   mvn -P eyes-tests test -Dtest=BenchmarkTest -Dbench=true \
 *       [-Dbench.count=40] [-Dbench.threadsA=3] [-Dbench.threadsB=16] \
 *       [-Dbench.warmup=1] [-Dbench.iters=2]
 */
public class BenchmarkTest {

    private static final String APP = "ImageTesterBenchmark";
    private static final int IMG_W = 800;
    private static final int IMG_H = 600;

    @Test
    public void threadingBenchmark() throws IOException {
        Assume.assumeTrue(
                "skipped unless -Dbench=true",
                "true".equalsIgnoreCase(System.getProperty("bench"))
        );

        int count = intProp("bench.count", 40);
        int threadsA = intProp("bench.threadsA", 3);
        int threadsB = intProp("bench.threadsB", Runtime.getRuntime().availableProcessors() * 2);
        int warmup = intProp("bench.warmup", 1);
        int iters = intProp("bench.iters", 2);

        System.out.printf(
                "BENCH config: count=%d threadsA=%d threadsB=%d warmup=%d iters=%d%n",
                count, threadsA, threadsB, warmup, iters
        );

        Path dir = generateFixtures(count);
        System.out.printf("BENCH fixtures: %s%n", dir);
        try {
            for (int i = 0; i < warmup; i++) {
                System.out.printf("BENCH warmup %d/%d (th=%d)%n", i + 1, warmup, threadsA);
                runOnce(dir, threadsA);
            }
            long[] a = new long[iters];
            long[] b = new long[iters];
            for (int i = 0; i < iters; i++) {
                System.out.printf("BENCH iter %d/%d A(th=%d)%n", i + 1, iters, threadsA);
                a[i] = runOnce(dir, threadsA);
                System.out.printf("BENCH iter %d/%d B(th=%d)%n", i + 1, iters, threadsB);
                b[i] = runOnce(dir, threadsB);
            }
            System.out.println("BENCH results:");
            report("A (th=" + threadsA + ")", a);
            report("B (th=" + threadsB + ")", b);
        } finally {
            deleteTree(dir);
        }
    }

    private long runOnce(Path dir, int threads) {
        long t0 = System.nanoTime();
        ImageTester.run(new String[]{
                "-f", dir.toString(),
                "-a", APP,
                "-st",
                "-th", String.valueOf(threads)
        });
        return System.nanoTime() - t0;
    }

    private Path generateFixtures(int count) throws IOException {
        Path dir = Files.createTempDirectory("imagetester-bench-");
        for (int i = 0; i < count; i++) {
            BufferedImage img = new BufferedImage(IMG_W, IMG_H, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            float hue = (i * 37f) % 360f / 360f;
            g.setPaint(new GradientPaint(
                    0, 0, Color.getHSBColor(hue, 0.55f, 0.92f),
                    IMG_W, IMG_H, Color.WHITE
            ));
            g.fillRect(0, 0, IMG_W, IMG_H);
            g.setColor(Color.BLACK);
            g.setFont(new Font("SansSerif", Font.BOLD, 48));
            g.drawString("img-" + i, 50, IMG_H / 2);
            g.dispose();
            File out = new File(dir.toFile(), String.format("img-%04d.png", i));
            ImageIO.write(img, "png", out);
        }
        return dir;
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        Files.walk(root)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    private static int intProp(String key, int fallback) {
        String v = System.getProperty(key);
        return (v == null || v.isEmpty()) ? fallback : Integer.parseInt(v);
    }

    private static void report(String name, long[] ns) {
        long sum = 0, min = Long.MAX_VALUE, max = 0;
        for (long v : ns) {
            sum += v;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        double meanMs = sum / (double) ns.length / 1_000_000.0;
        System.out.printf(
                "BENCH %s: mean=%.0fms min=%.0fms max=%.0fms iters=%d%n",
                name, meanMs, min / 1e6, max / 1e6, ns.length
        );
    }
}
