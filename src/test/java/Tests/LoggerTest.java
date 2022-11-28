package Tests;

import org.junit.Test;

import com.applitools.imagetester.lib.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;

public class LoggerTest {

    @Test
    public void reportException() {
        Logger logger = new Logger();
        logger.reportException(new Exception());
        logger.reportException(new IOException());
        logger.reportException(new UnsatisfiedLinkError());
        logger.reportException(new FileNotFoundException());
    }
}