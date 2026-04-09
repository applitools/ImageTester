package Tests;

import infra.TestBase;
import org.junit.Test;

/**
 * End-to-end Eyes integration tests. Requires APPLITOOLS_API_KEY env var.
 *
 * Against compiled code:  mvn test -Peyes-tests
 * Against packaged JAR:   mvn test -Peyes-tests -Djar=jars/ImageTester_3.9.0.jar
 */
public class EyesIntegrationTest extends TestBase {

    // --- Single file tests ---

    @Test
    public void singlePdf() {
        runImageTester("-f TestData/b/c/JustPDF/Lorem2.pdf");
    }

    @Test
    public void singleImage() {
        runImageTester("-f TestData/b/c/googleforgoogle.png");
    }

    // --- Folder tests ---

    @Test
    public void folderWithMixedContent() {
        runImageTester("-f TestData/b/c -th 10");
    }

    @Test
    public void imageFolder() {
        runImageTester("-f TestData/a/");
    }

    @Test
    public void pdfFolder() {
        runImageTester("-f TestData/b/c/JustPDF/");
    }

    // --- Split mode ---

    @Test
    public void pdfSplitMode() {
        runImageTester("-f TestData/b/c/JustPDF/Lorem3.pdf -st -th 10");
    }

    // --- Page selection ---

    @Test
    public void pdfPageSelection() {
        runImageTester("-f TestData/b/c/JustPDF/Lorem3.pdf -sp 1-2 -th 10");
    }

    // --- Forced name ---

    @Test
    public void forcedName() {
        runImageTester("-f TestData/b/Lorem1.pdf -fn MyForcedName");
    }

    // --- Flat batch ---

    @Test
    public void flatBatchWithId() {
        runImageTester("-f TestData/b/Lorem1.pdf -fb TestBatch<>testBatchId");
    }

    // --- Batch notifications ---

    @Test
    public void batchNotification() {
        runImageTester("-f TestData/b/c/JustPDF/Lorem2.pdf -nc");
    }

    // --- Image scaling ---

    @Test
    public void imageScaling() {
        runImageTester("-f TestData/a/ -ms 1000x1000");
    }

    // --- Image cut ---

    @Test
    public void imageCut() {
        runImageTester("-f TestData/a/ -ic 10,20,30,40");
    }

    // --- Ordering ---

    @Test
    public void imageOrderAlphabetic() {
        runImageTester("-f TestData/jpegs/alphabetic");
    }

    @Test
    public void imageOrderMixed() {
        runImageTester("-f TestData/jpegs/mixed");
    }

    // --- Accessibility ---

    @Test
    public void accessibilityWithRegions() {
        runImageTester("-f TestData/b/Lorem1.pdf -ac AAA -ari \"100,100,100,100\" -arr \"200,200,200,200\"");
    }

    // --- Regions ---

    @Test
    public void ignoreContentLayoutRegions() {
        runImageTester("-f TestData/b/Lorem1.pdf -ir \"100,100,100,100\" -cr \"200,200,200,200\" -lr \"300,300,300,300\"");
    }

    // --- Whitebox: multithreaded folder (always runs in-process) ---

    @Test
    public void multithreadedFolderTraversal() {
        runWhitebox("FolderTestsApp", "TestData/b");
    }
}
