package infra;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TestBaseTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void inProcessRunFailsOnNonZeroExit() {
        TestBase base = new TestBase();
        assertThrows(AssertionError.class, () -> base.runImageTester("-zz"));
    }

    @Test
    public void runImageTester_cleanExitWithoutAnyResults_fails() throws Exception {
        // The -bb E2E was green for months while every run silently failed: exit 0
        // alone cannot distinguish "tested and passed" from "uploaded nothing".
        TestBase base = new TestBase();
        String emptyFolder = tmp.newFolder("empty").getAbsolutePath();
        assertThrows(AssertionError.class, () -> base.runImageTester("-k dummy -f " + emptyFolder));
    }

    @Test
    public void containsResultLine_matchesARealResultRow() {
        assertTrue(TestBase.containsResultLine(
                "[20:45:30] [pool-1-thread-1] [45778 Msec] [Passed], Existing test "
                        + "[ steps: 2, test name: Lorem2.pdf, matches: 2, mismatches:0, missing: 0]"));
    }

    @Test
    public void containsResultLine_ignoresProgressAndLogLines() {
        assertFalse(TestBase.containsResultLine(
                "[1/5] \nStarting universal core in: /tmp/core-linux\nDiscovering folder b\n"));
    }
}
