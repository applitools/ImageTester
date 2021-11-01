package Tests;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

public class TestAsProcess {
    @Test
    public void test_running_as_process() throws IOException, InterruptedException {
        String jarfile = "out/ImageTester_1.4.6.jar";
        String target = "TestData/b/Lorem1.pdf";
        // Invoke the process
        Process proc = Runtime.getRuntime().exec(String.format("java -jar %s -f %s", jarfile, target));
        // Wait for the process to finish
        int exitCode = proc.waitFor();
        // Print the output
        InputStream is = proc.getInputStream();
        byte b[] = new byte[is.available()];
        is.read(b, 0, b.length);
        System.out.println(new String(b));
        // Ensure ran successfully
        assert exitCode == 0;
    }
}
