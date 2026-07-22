package com.applitools.imagetester.lib;

import com.applitools.eyes.RectangleSize;
import com.applitools.eyes.TestResults;
import com.applitools.eyes.TestResultsStatus;
import com.applitools.eyes.exceptions.DiffsFoundException;
import com.applitools.eyes.images.Eyes;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CompareRunnerTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static File pngFile(TemporaryFolder tmp, String name) throws IOException {
        File f = tmp.newFile(name);
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(img, "png", f);
        return f;
    }

    private static Config config(String forcedName) {
        Config c = new Config();
        c.forcedName = forcedName;
        c.logger = new Logger();
        return c;
    }

    private static TestResults passedResult() {
        TestResults r = mock(TestResults.class);
        when(r.isDifferent()).thenReturn(false);
        when(r.getStatus()).thenReturn(TestResultsStatus.Passed);
        return r;
    }

    private static TestResults failedResult() {
        TestResults r = mock(TestResults.class);
        when(r.isDifferent()).thenReturn(true);
        when(r.getStatus()).thenReturn(TestResultsStatus.Unresolved);
        return r;
    }

    /** Records open()/close() call order across however many Eyes instances the factory hands out. */
    private static final class OrderingFactory {
        final List<String> log = new ArrayList<>();
        final List<TestResults> closeResults;
        int built = 0;

        OrderingFactory(List<TestResults> closeResultsInOrder) { this.closeResults = closeResultsInOrder; }

        EyesFactory factory() {
            EyesFactory factory = mock(EyesFactory.class, RETURNS_DEEP_STUBS);
            when(factory.build()).thenAnswer(inv -> {
                int index = built++;
                Eyes eyes = mock(Eyes.class);
                when(eyes.getIsOpen()).thenReturn(false);
                org.mockito.Mockito.doAnswer(a -> { log.add("open-" + index); return null; })
                        .when(eyes).open(anyString(), anyString(), any(RectangleSize.class));
                when(eyes.close(org.mockito.ArgumentMatchers.anyBoolean()))
                        .thenAnswer(a -> { log.add("close-" + index); return closeResults.get(index); });
                return eyes;
            });
            return factory;
        }
    }

    @Test
    public void logsBothDocumentsAndTheirResults() throws Exception {
        File doc1 = pngFile(tmp, "contract-v2.png");
        File doc2 = pngFile(tmp, "contract-v3.png");
        Config c = config("cmp");
        List<String> lines = new ArrayList<>();
        c.logger.addListener(lines::add);
        OrderingFactory of = new OrderingFactory(java.util.Arrays.asList(passedResult(), passedResult()));

        CompareRunner.run(doc1, doc2, c, of.factory());

        String log = String.join("", lines);
        assertTrue("expected Doc 1's file name in the log, got: " + log, log.contains("contract-v2.png"));
        assertTrue("expected Doc 2's file name in the log, got: " + log, log.contains("contract-v3.png"));
        assertTrue("expected at least 2 result lines (one per document), got: " + log,
                lines.stream().filter(l -> l.contains("[Passed]")).count() >= 2);
    }

    @Test
    public void doc1OpensAndClosesBeforeDoc2Opens() throws Exception {
        File doc1 = pngFile(tmp, "doc1.png");
        File doc2 = pngFile(tmp, "doc2.png");
        OrderingFactory of = new OrderingFactory(java.util.Arrays.asList(passedResult(), passedResult()));

        CompareRunner.run(doc1, doc2, config("cmp"), of.factory());

        int close0 = of.log.indexOf("close-0");
        int open1 = of.log.indexOf("open-1");
        assertTrue("doc1 must close before doc2 opens", close0 >= 0 && open1 >= 0 && close0 < open1);
    }

    @Test
    public void resultCarriesDoc2sTestResults() throws Exception {
        File doc1 = pngFile(tmp, "doc1.png");
        File doc2 = pngFile(tmp, "doc2.png");
        TestResults doc2Result = passedResult();
        OrderingFactory of = new OrderingFactory(java.util.Arrays.asList(passedResult(), doc2Result));

        CompareRunner.CompareResult result = CompareRunner.run(doc1, doc2, config("cmp"), of.factory());

        assertEquals(doc2Result, result.doc2Result);
    }

    @Test
    public void throwsDiffsFoundWhenShouldThrowAndDoc2Differs() throws Exception {
        File doc1 = pngFile(tmp, "doc1.png");
        File doc2 = pngFile(tmp, "doc2.png");
        Config c = config("cmp");
        c.shouldThrowException = true;
        OrderingFactory of = new OrderingFactory(java.util.Arrays.asList(passedResult(), failedResult()));

        assertThrows(DiffsFoundException.class, () -> CompareRunner.run(doc1, doc2, c, of.factory()));
    }

    @Test
    public void doesNotThrowWhenShouldThrowFalseEvenIfDoc2Differs() throws Exception {
        File doc1 = pngFile(tmp, "doc1.png");
        File doc2 = pngFile(tmp, "doc2.png");
        Config c = config("cmp");
        c.shouldThrowException = false;
        OrderingFactory of = new OrderingFactory(java.util.Arrays.asList(passedResult(), failedResult()));

        CompareRunner.CompareResult result = CompareRunner.run(doc1, doc2, c, of.factory());
        assertTrue(result.doc2Result.isDifferent());
    }

    @Test
    public void selectedPagesExceedingDoc1PageCountThrowsBeforeAnyEyesCall() throws Exception {
        File doc1 = new File(getClass().getResource("/fixtures/valid-2-page.pdf").getFile());
        File doc2 = new File(getClass().getResource("/fixtures/valid-10-page.pdf").getFile());
        Config c = config("cmp");
        c.pages = "1-5";
        OrderingFactory of = new OrderingFactory(new ArrayList<>());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> CompareRunner.run(doc1, doc2, c, of.factory()));
        assertTrue(ex.getMessage().contains("doc1.pdf".equals(doc1.getName()) ? "doc1" : doc1.getName())
                || ex.getMessage().toLowerCase().contains("valid-2-page"));
        assertTrue(ex.getMessage().contains("2"));
        assertTrue(of.log.isEmpty());
    }

    @Test
    public void selectedPagesExceedingDoc2PageCountThrowsBeforeAnyEyesCall() throws Exception {
        File doc1 = new File(getClass().getResource("/fixtures/valid-10-page.pdf").getFile());
        File doc2 = new File(getClass().getResource("/fixtures/valid-2-page.pdf").getFile());
        Config c = config("cmp");
        c.pages = "1-5";
        OrderingFactory of = new OrderingFactory(new ArrayList<>());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> CompareRunner.run(doc1, doc2, c, of.factory()));
        assertTrue(ex.getMessage().toLowerCase().contains("valid-2-page"));
        assertTrue(of.log.isEmpty());
    }

    @Test
    public void pageCountErrorMessageStatesTheDeficit() throws Exception {
        File doc1 = new File(getClass().getResource("/fixtures/valid-2-page.pdf").getFile());
        File doc2 = new File(getClass().getResource("/fixtures/valid-10-page.pdf").getFile());
        Config c = config("cmp");
        c.pages = "1-5";
        OrderingFactory of = new OrderingFactory(new ArrayList<>());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> CompareRunner.run(doc1, doc2, c, of.factory()));

        assertTrue("message should state the deficit: " + ex.getMessage(),
                ex.getMessage().contains("short by 3"));
    }

    @Test
    public void unsupportedFileTypeThrowsClearErrorBeforeAnyEyesCall() throws Exception {
        File doc1 = tmp.newFile("doc1.txt");
        File doc2 = pngFile(tmp, "doc2.png");
        OrderingFactory of = new OrderingFactory(new ArrayList<>());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> CompareRunner.run(doc1, doc2, config("cmp"), of.factory()));

        assertTrue(ex.getMessage().contains("Unsupported file type for comparison"));
        assertTrue(ex.getMessage().contains("doc1.txt"));
        assertTrue(of.log.isEmpty());
    }
}
