package Tests;

import infra.TestBase;
import org.junit.Test;

import com.applitools.imagetester.ImageTester;

/**
 * End-to-end Eyes integration tests. Requires APPLITOOLS_API_KEY env var.
 * Run with: mvn test -Peyes-tests
 */
public class EyesIntegrationTest extends TestBase {

    // --- Single file tests ---

    @Test
    public void singlePdf() {
        ImageTester.main("-f TestData/b/c/JustPDF/Lorem2.pdf".split(" "));
    }

    @Test
    public void singleImage() {
        ImageTester.main("-f TestData/b/c/googleforgoogle.png".split(" "));
    }

    // --- Folder tests ---

    @Test
    public void folderWithMixedContent() {
        ImageTester.main("-f TestData/b/c -th 10".split(" "));
    }

    @Test
    public void imageFolder() {
        ImageTester.main("-f TestData/a/".split(" "));
    }

    @Test
    public void pdfFolder() {
        ImageTester.main("-f TestData/b/c/JustPDF/".split(" "));
    }

    // --- Split mode ---

    @Test
    public void pdfSplitMode() {
        ImageTester.main("-f TestData/b/c/JustPDF/Lorem3.pdf -st -th 10".split(" "));
    }

    // --- Page selection ---

    @Test
    public void pdfPageSelection() {
        ImageTester.main("-f TestData/b/c/JustPDF/Lorem3.pdf -sp 1-2 -th 10".split(" "));
    }

    // --- Forced name ---

    @Test
    public void forcedName() {
        ImageTester.main("-f TestData/b/Lorem1.pdf -fn MyForcedName".split(" "));
    }

    // --- Flat batch ---

    @Test
    public void flatBatchWithId() {
        ImageTester.main("-f TestData/b/Lorem1.pdf -fb TestBatch<>testBatchId".split(" "));
    }

    // --- Batch notifications ---

    @Test
    public void batchNotification() {
        ImageTester.main("-f TestData/b/c/JustPDF/Lorem2.pdf -nc".split(" "));
    }

    // --- Image scaling ---

    @Test
    public void imageScaling() {
        ImageTester.main("-f TestData/a/ -ms 1000x1000".split(" "));
    }

    // --- Image cut ---

    @Test
    public void imageCut() {
        ImageTester.main("-f TestData/a/ -ic 10,20,30,40".split(" "));
    }

    // --- Ordering ---

    @Test
    public void imageOrderAlphabetic() {
        ImageTester.main("-f TestData/jpegs/alphabetic".split(" "));
    }

    @Test
    public void imageOrderMixed() {
        ImageTester.main("-f TestData/jpegs/mixed".split(" "));
    }

    // --- Accessibility ---

    @Test
    public void accessibilityWithRegions() {
        ImageTester.main("-f TestData/b/Lorem1.pdf -ac AAA -ari \"100,100,100,100\" -arr \"200,200,200,200\"".split(" "));
    }

    // --- Regions ---

    @Test
    public void ignoreContentLayoutRegions() {
        ImageTester.main("-f TestData/b/Lorem1.pdf -ir \"100,100,100,100\" -cr \"200,200,200,200\" -lr \"300,300,300,300\"".split(" "));
    }

    // --- Whitebox: multithreaded folder ---

    @Test
    public void multithreadedFolderTraversal() {
        runWhitebox("FolderTestsApp", "TestData/b");
    }
}
