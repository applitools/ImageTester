package Tests;

import infra.TestBase;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

/**
 * End-to-end Eyes integration tests. Requires APPLITOOLS_API_KEY env var.
 * Every user-facing CLI flag that can run end-to-end has a scenario here, so a
 * dependency or SDK update that breaks a flag turns this suite red (#49 shipped
 * because -ac had no such scenario).
 *
 * Against compiled code:  mvn test -Peyes-tests
 * Against packaged JAR:   mvn test -Peyes-tests -Djar=jars/ImageTester_<version>.jar
 *
 * Not covered here (see CliContractTest for their parse contracts):
 * -p needs a live proxy server; -dv globally disables SSL validation for the JVM;
 * -gd/-gi/-gg run only when APPLITOOLS_VIEW_KEY is set (enterprise view key).
 */
public class EyesIntegrationTest extends TestBase {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

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

    @Test
    public void pdfPageSelectionWithPageNumbers() {
        runImageTester("-f TestData/b/c/JustPDF/Lorem3.pdf -sp 1-2 -pn -fn PageNumbersE2E");
    }

    // --- Naming and identity ---

    @Test
    public void forcedName() {
        runImageTester("-f TestData/b/Lorem1.pdf -fn MyForcedName");
    }

    @Test
    public void appName() {
        runImageTester("-f TestData/a/wikipedia.png -a AppNameE2E");
    }

    @Test
    public void sequenceName() {
        runImageTester("-f TestData/a/wikipedia.png -sq E2ESequence -fn SequenceE2E");
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

    @Test
    public void dontCloseBatch() {
        runImageTester("-f TestData/a/wikipedia.png -dcb -fn DontCloseBatchE2E");
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

    @Test
    public void legacyFileOrder() {
        runImageTester("-f TestData/jpegs/alphabetic -lo -fn LegacyOrderE2E");
    }

    // --- Accessibility ---

    @Test
    public void accessibilityWithRegions() {
        runImageTester("-f TestData/b/Lorem1.pdf -ac AAA -ari 100,100,100,100 -arr 200,200,200,200");
    }

    @Test
    public void accessibilityLargeBoldGraphicsRegions() {
        runImageTester("-f TestData/b/Lorem1.pdf -ac -arl 50,50,100,100 -arb 150,150,100,100 -arg 250,250,100,100 -fn AccessibilityRegionsE2E");
    }

    // --- Regions ---

    @Test
    public void ignoreContentLayoutRegions() {
        runImageTester("-f TestData/b/Lorem1.pdf -ir 100,100,100,100 -cr 200,200,200,200 -lr 300,300,300,300");
    }

    @Test
    public void regionCapture() {
        runImageTester("-f TestData/a/wikipedia.png -rc 0,0,400,300 -fn RegionCaptureE2E");
    }

    // --- Matching ---

    @Test
    public void matchLevelLayout() {
        runImageTester("-f TestData/a/wikipedia.png -ml Layout -fn MatchLevelE2E");
    }

    @Test
    public void matchTimeout() {
        runImageTester("-f TestData/a/wikipedia.png -mt 2000 -fn MatchTimeoutE2E");
    }

    @Test
    public void ignoreDisplacement() {
        runImageTester("-f TestData/a/wikipedia.png -id -fn IgnoreDisplacementE2E");
    }

    @Test
    public void throwExceptionsOnFailure() {
        runImageTester("-f TestData/a/wikipedia.png -te -fn ThrowExceptionsE2E");
    }

    @Test
    public void promptNewTests() {
        runImageTester("-f TestData/a/wikipedia.png -pt -fn PromptNewE2E");
    }

    // --- Baseline selection ---

    @Test
    public void branchName() {
        runImageTester("-f TestData/a/wikipedia.png -br e2e-branch -fn BranchE2E");
    }

    @Test
    public void parentBranchName() {
        runImageTester("-f TestData/a/wikipedia.png -br e2e-child -pb e2e-branch -fn ParentBranchE2E");
    }

    @Test
    public void baselineEnvName() {
        runImageTester("-f TestData/a/wikipedia.png -bn E2EBaselineEnv -fn BaselineEnvE2E");
    }

    @Test
    public void baselineBranchName() {
        // A -bb pointing at a branch the server doesn't know now fails the run (exit 1)
        // instead of silently uploading nothing, so establish the branch first: running
        // with -br creates it (and saves this test's baseline on it) if it's missing.
        runImageTester("-f TestData/a/wikipedia.png -br e2e-baseline-branch -fn BaselineBranchE2E");
        runImageTester("-f TestData/a/wikipedia.png -bb e2e-baseline-branch -fn BaselineBranchE2E");
    }

    // --- Environment metadata ---

    @Test
    public void viewportSize() {
        runImageTester("-f TestData/a/wikipedia.png -vs 1000x600 -fn ViewportE2E");
    }

    @Test
    public void hostOs() {
        runImageTester("-f TestData/a/wikipedia.png -os Windows11 -fn HostOsE2E");
    }

    @Test
    public void hostApp() {
        runImageTester("-f TestData/a/wikipedia.png -ap Chrome -fn HostAppE2E");
    }

    @Test
    public void environmentName() {
        runImageTester("-f TestData/a/wikipedia.png -en E2EEnv -fn EnvNameE2E");
    }

    @Test
    public void deviceName() {
        runImageTester("-f TestData/a/wikipedia.png -dn PixelE2E -fn DeviceNameE2E");
    }

    @Test
    public void eyesProperties() {
        runImageTester("-f TestData/a/wikipedia.png -pr env:ci|suite:e2e -fn PropertiesE2E");
    }

    // --- Connection ---

    @Test
    public void explicitApiKey() {
        runImageTester("-f TestData/a/wikipedia.png -k " + System.getenv("APPLITOOLS_API_KEY") + " -fn ExplicitKeyE2E");
    }

    @Test
    public void explicitServerUrl() {
        String server = System.getenv("APPLITOOLS_SERVER_URL");
        if (server == null) server = "https://eyesapi.applitools.com";
        runImageTester("-f TestData/a/wikipedia.png -s " + server + " -fn ExplicitServerE2E");
    }

    // --- PDF handling ---

    @Test
    public void pdfDpi() {
        runImageTester("-f TestData/b/Lorem1.pdf -di 150 -fn DpiE2E");
    }

    @Test
    public void pdfTrimAuto() {
        runImageTester("-f TestData/b/Lorem1.pdf -tp -fn PdfTrimE2E");
    }

    @Test
    public void pdfPassword() {
        runImageTester("-f src/test/resources/fixtures/password-protected.pdf -pp test123 -fn PasswordPdfE2E");
    }

    @Test
    public void pdfRenderThreads() {
        runImageTester("-f TestData/b/c/JustPDF/Lorem3.pdf -rt 2 -fn RenderThreadsE2E");
    }

    @Test
    public void normalizeFont() {
        runImageTester("-f TestData/b/Lorem1.pdf -nf -fn NormalizeFontE2E");
    }

    @Test
    public void regexFileNameFilter() {
        runImageTester("-f TestData/b/c/JustPDF -rf Lorem2.* -fn RegexFilterE2E");
    }

    // --- Logging (deprecated/no-op flags still must not break a run) ---

    @Test
    public void debugPrints() {
        runImageTester("-f TestData/a/wikipedia.png -debug -fn DebugE2E");
    }

    @Test
    public void logPrints() {
        runImageTester("-f TestData/a/wikipedia.png -log -fn LogE2E");
    }

    @Test
    public void deprecatedLogFileFlag() {
        runImageTester("-f TestData/a/wikipedia.png -lf ignored.log -fn LogFileE2E");
    }

    // --- Compare mode ---

    @Test
    public void compareTwoDocuments() {
        runImageTester("-doc1 TestData/diffs/base/lorem_20.pdf -doc2 TestData/diffs/actual/lorem_20.pdf -fn CompareModeE2E");
    }

    // --- Batch mapper ---

    @Test
    public void batchMapper() {
        runImageTester("-mp src/test/resources/fixtures/batchmap-e2e.csv");
    }

    // --- Watermark removal ---

    @Test
    public void removeWatermarkStandaloneOut() throws IOException {
        File inputDir = watermarkedPdfDir();
        File outDir = tempFolder.newFolder("rwo-out");
        runImageTester("-rw WATERMARK -rwo " + outDir.getAbsolutePath() + " -f " + inputDir.getAbsolutePath());
    }

    @Test
    public void removeWatermarkThenUpload() throws IOException {
        File inputDir = watermarkedPdfDir();
        runImageTester("-rw WATERMARK -f " + inputDir.getAbsolutePath() + " -fn WatermarkRemoveE2E");
    }

    @Test
    public void removeWatermarkAutoThenUpload() throws IOException {
        File inputDir = tempFolder.newFolder("rwauto-input");
        buildPdf(new File(inputDir, "one.pdf"), "First synthetic report page");
        buildPdf(new File(inputDir, "two.pdf"), "Second synthetic report page");
        runImageTester("-rwauto -f " + inputDir.getAbsolutePath() + " -fn RwAutoE2E");
    }

    // --- EyesUtilities (needs enterprise view key) ---

    @Test
    public void downloadDiffs() throws IOException {
        Assume.assumeTrue("APPLITOOLS_VIEW_KEY not set", System.getenv("APPLITOOLS_VIEW_KEY") != null);
        File outDir = tempFolder.newFolder("diffs-out");
        runImageTester("-f TestData/a/wikipedia.png -gd -vk " + System.getenv("APPLITOOLS_VIEW_KEY")
                + " -of " + outDir.getAbsolutePath() + " -fn GetDiffsE2E");
    }

    // --- Whitebox: multithreaded folder (always runs in-process) ---

    @Test
    public void multithreadedFolderTraversal() {
        runWhitebox("FolderTestsApp", "TestData/b");
    }

    private File watermarkedPdfDir() throws IOException {
        File inputDir = tempFolder.newFolder("rw-input");
        buildPdf(new File(inputDir, "report.pdf"), "Quarterly report body text", "WATERMARK");
        return inputDir;
    }

    private static void buildPdf(File file, String bodyText) throws IOException {
        buildPdf(file, bodyText, null);
    }

    private static void buildPdf(File file, String bodyText, String watermarkText) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(72, 700);
                cs.showText(bodyText);
                cs.endText();
                if (watermarkText != null) {
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 36);
                    cs.newLineAtOffset(140, 400);
                    cs.showText(watermarkText);
                    cs.endText();
                }
            }
            doc.save(file);
        }
    }
}
