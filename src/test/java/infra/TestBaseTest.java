package infra;

import org.junit.Test;

import static org.junit.Assert.assertThrows;

public class TestBaseTest {

    @Test
    public void inProcessRunFailsOnNonZeroExit() {
        TestBase base = new TestBase();
        assertThrows(AssertionError.class, () -> base.runImageTester("-zz"));
    }
}
