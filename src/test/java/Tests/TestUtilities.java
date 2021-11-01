package Tests;

import infra.TestBase;
import org.junit.Test;

public class TestUtilities extends TestBase {

    @Test
    public void getImagesTest() {
        runBlackBox("src/test/TestData/b/c/JustPDF/Lorem3.pdf", "-gi");
    }

    @Test
    public void getImagesWithFullOutputfolder(){
        runBlackBox("src/test/TestData/b/c/JustPDF/Lorem3.pdf", "-of \"output/file:{step_index}_{step_tag}_{artifact_type}.{file_ext}\" -gi -st -th 5");
    }

    @Test
    public void getImagesWithOutputfolder(){
        runBlackBox("src/test/TestData/b/c/JustPDF/Lorem3.pdf", "-of output -gi -st -th 5");
    }

    @Test
    public void getDiffs(){
        runBlackBox("TestData/diffs/actual/lorem_20.pdf", "-gd");
    }


    @Test
    public void aVoid(){
        String[] x = "x1000".split("x");
        return;
    }
}
