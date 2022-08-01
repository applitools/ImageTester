package Tests;

import com.yanirta.lib.Logger;
import org.junit.Test;

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