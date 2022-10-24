package Tests;

import com.yanirta.ImageTester;
import org.junit.Test;

public class TestFullCommands {

    @Test
    public void testPDFforcedNamePromptNew() {
        ImageTester.main("-f TestData/b/Lorem1.pdf -fn MyNewName -pn".split(" "));
    }

    @Test
    public void testPDFforcedName() {
        ImageTester.main("-f TestData/b/Lorem1.pdf -fn MyForcedName".split(" "));
    }

    @Test
    public void testPDFflatBatchName() {
        ImageTester.main("-f TestData/b/Lorem1.pdf -fb MyFlatBatch".split(" "));
    }

    @Test
    public void testFolderSimple() {
        ImageTester.main("-f TestData/ -th 10 -di 200".split(" "));
    }

    @Test
    public void testFolderSimpleDebug() {
        ImageTester.main("-f TestData/ -debug".split(" "));
    }


    @Test
    public void testPDFSimple() {
        ImageTester.main("-f TestData/b/c/JustPDF/Lorem2.pdf".split(" "));
    }

    @Test
    public void testPDFFolder() {
        ImageTester.main("-f TestData/b/c/JustPDF/".split(" "));
    }


    @Test
    public void testPDFSimpleDebug() {
        ImageTester.main("-f TestData/b/c/JustPDF/Lorem2.pdf -debug".split(" "));
    }

    @Test
    public void testPDFSplit() {
        ImageTester.main("-f TestData/b/c/JustPDF/Lorem3.pdf -th 10 -debug -st".split(" "));
    }

    @Test
    public void PDFSplitWithUtilities() {
        ImageTester.main("-f TestData/diffs/ -th 10 -debug -st -gg -of Artifacts".split(" "));
    }

    @Test
    public void testPDFPages() {
        ImageTester.main("-f TestData/b/c/JustPDF/Lorem3.pdf -th 10 -sp 1,2,4-5 -debug".split(" "));
    }

    @Test
    public void testPDFPagesSplit() {
        ImageTester.main("-f TestData/b/c/JustPDF/Lorem3.pdf -th 10 -sp 1,2,4-5 -debug -st".split(" "));
    }

    @Test
    public void testWithSecureProxy() {
        ImageTester.main("-f TestData/b/c/JustPDF/Lorem3.pdf -th 10 -sp 1,2,4-5 -pr http://my.proxy.com:8080,user,pass -debug -st".split(" "));
    }

    @Test
    public void testBatchNotifications() {
        ImageTester.main("-f TestData/b/c/JustPDF/Lorem2.pdf -nc".split(" "));
    }

    @Test
    public void testBatchNotificationsLongFlag() {
        ImageTester.main("-f TestData/b/c/JustPDF/Lorem2.pdf --notifyCompletion".split(" "));
    }

    @Test
    public void testPDFFolderWithBatchNotifications() {
        ImageTester.main("-f TestData/b/c/JustPDF/ -nc".split(" "));
    }

    //to add id use the following:
    //-fb BATCH_NAME_HERE<>BATCH_ID_HERE

    @Test
    public void testBatchNotificationsWithFlatBatchAndId() {
        ImageTester.main("-f TestData/b/c/JustPDF/Lorem2.pdf -fb EmailNotificationBatch<>customBatchID -nc".split(" "));
    }

    @Test
    public void testBatchNotificationsWithAll() {
        ImageTester.main("-f TestData/ -nc".split(" "));
    }

    @Test
    public void testBatchNotificationsMultibatch() {
        ImageTester.main("-f TestData/b/c -nc".split(" "));
    }


    @Test
    public void testImageScaling1() {
        ImageTester.main("-f TestData/a/ -ms 1000".split(" "));
    }

    @Test
    public void testImageScaling2() {
        ImageTester.main("-f TestData/a/ -ms x1000".split(" "));
    }

    @Test
    public void testImageScaling3() {
        ImageTester.main("-f TestData/a/ -ms 1000x1000".split(" "));
    }

    @Test
    public void testImageScaling4() {
        ImageTester.main("-f TestData/a/ -ms 1000x".split(" "));
    }

    @Test
    public void testImageCutFull() {
        ImageTester.main("-f TestData/a/ -ic 10,20,30,40".split(" "));
    }

    @Test
    public void testImageCutWidth() {
        ImageTester.main("-f TestData/a/ -ic 10".split(" "));
    }

    @Test
    public void testImageCutHeight() {
        ImageTester.main("-f TestData/a/ -ic ,20,".split(" "));
    }

    @Test
    public void testImageCutMixed() {
        ImageTester.main("-f TestData/a/ -ic ,50,,100".split(" "));
    }

    @Test
    public void testOrderNumeric() {
        ImageTester.main("-f TestData/jpegs/numeric -debug".split(" "));
    }

    @Test
    public void testOrderAlphabetic() {
        ImageTester.main("-f TestData/jpegs/alphabetic -debug".split(" "));
    }

    @Test
    public void testMixedOrder() {
        ImageTester.main("-f TestData/jpegs/mixed -debug".split(" "));
    }


    @Test
    public void testOrderNumericLegacy() {
        ImageTester.main("-f TestData/jpegs/numeric -lo -debug".split(" "));
    }

    @Test
    public void testOrderAlphabeticLegacy() {
        ImageTester.main("-f TestData/jpegs/alphabetic -lo -debug".split(" "));
    }

    @Test
    public void testAccessibility(){
        ImageTester.main("-f TestData/b/c/JustPDF/Lorem2.pdf -ac".split(" "));
    }

    @Test
    public void testAccessibilityWCAG21(){
        ImageTester.main("-f TestData/b/Lorem1.pdf -ac :WCAG21".split(" "));
    }

    @Test
    public void testAccessibilityAAA(){
        ImageTester.main("-f TestData/b/Lorem1.pdf -ac AAA".split(" "));
    }

    @Test
    public void testAccessibilityAAA_WCAG21() {
        ImageTester.main("-f TestData/b/Lorem1.pdf -ac AAA:WCAG21".split(" "));
    }

    @Test
    public void testRegions() {
        ImageTester.main("-f TestData/b/Lorem1.pdf -ir \"100,100,100,100\" -cr \"200,200,200,200\" -lr \"300,300,300,300\"".split(" "));
    }

    @Test
    public void testAccessibilityRegions() {
        ImageTester.main("-f TestData/b/Lorem1.pdf -ac -ari \"100,100,100,100|150,150,150,150\" -arr \"200,200,200,200\" -arl \"250,250,250,250\" -arb \"300,300,300,300\" -arg \"350,350,350,350\" ".split(" "));
    }

    @Test
    public void testAccessibilityRegions_FullPage() {
        ImageTester.main("-f TestData/b/Lorem1.pdf -ac -arr".split(" "));
    }



}
