package com.applitools.imagetester.BatchMapper;

import org.junit.Test;
import static org.junit.Assert.*;

// NOTE: setLayoutRegions(), setIgnoreRegions(), and setContentRegions() are no-arg
// setters — they do not accept a value parameter. Calling them does not update the
// field. Round-trip tests for those three fields therefore verify that the field
// remains null after calling the broken setter, reflecting actual behavior.
// See FIXME in BatchMapPojo: setters should accept a String parameter.
public class BatchMapPojoTest {

    @Test
    public void getterSetterRoundTrip_filePath() {
        BatchMapPojo pojo = new BatchMapPojo();
        pojo.setFilePath("/path/to/file.pdf");
        assertEquals("/path/to/file.pdf", pojo.getFilePath());
    }

    @Test
    public void getterSetterRoundTrip_testName() {
        BatchMapPojo pojo = new BatchMapPojo();
        pojo.setTestName("My Test");
        assertEquals("My Test", pojo.getTestName());
    }

    @Test
    public void getterSetterRoundTrip_app() {
        BatchMapPojo pojo = new BatchMapPojo();
        pojo.setApp("MyApp");
        assertEquals("MyApp", pojo.getApp());
    }

    @Test
    public void getterSetterRoundTrip_os() {
        BatchMapPojo pojo = new BatchMapPojo();
        pojo.setOs("Windows");
        assertEquals("Windows", pojo.getOs());
    }

    @Test
    public void getterSetterRoundTrip_browser() {
        BatchMapPojo pojo = new BatchMapPojo();
        pojo.setBrowser("Chrome");
        assertEquals("Chrome", pojo.getBrowser());
    }

    @Test
    public void getterSetterRoundTrip_viewport() {
        BatchMapPojo pojo = new BatchMapPojo();
        pojo.setViewport("1024x768");
        assertEquals("1024x768", pojo.getViewport());
    }

    @Test
    public void getterSetterRoundTrip_matchsize() {
        BatchMapPojo pojo = new BatchMapPojo();
        pojo.setMatchsize("1000x");
        assertEquals("1000x", pojo.getMatchsize());
    }

    @Test
    public void getterSetterRoundTrip_pages() {
        BatchMapPojo pojo = new BatchMapPojo();
        pojo.setPages("1-5");
        assertEquals("1-5", pojo.getPages());
    }

    @Test
    public void getterSetterRoundTrip_matchLevel() {
        BatchMapPojo pojo = new BatchMapPojo();
        pojo.setMatchLevel("Strict");
        assertEquals("Strict", pojo.getMatchLevel());
    }

    // CONCERN: setLayoutRegions() is a no-arg setter — it ignores its caller's value
    // and leaves the field null. The field can only be set by direct assignment or
    // via Jackson's public-field access. The test below documents actual behavior.
    @Test
    public void setter_layoutRegions_isNoArgAndLeavesFieldNull() {
        BatchMapPojo pojo = new BatchMapPojo();
        pojo.setLayoutRegions();
        assertNull(pojo.getLayoutRegions());
    }

    // CONCERN: same broken no-arg setter for ignoreRegions.
    @Test
    public void setter_ignoreRegions_isNoArgAndLeavesFieldNull() {
        BatchMapPojo pojo = new BatchMapPojo();
        pojo.setIgnoreRegions();
        assertNull(pojo.getIgnoreRegions());
    }

    // CONCERN: same broken no-arg setter for contentRegions.
    @Test
    public void setter_contentRegions_isNoArgAndLeavesFieldNull() {
        BatchMapPojo pojo = new BatchMapPojo();
        pojo.setContentRegions();
        assertNull(pojo.getContentRegions());
    }

    @Test
    public void equals_sameValues_returnsTrue() {
        BatchMapPojo a = createPojo("path", "test", "app");
        BatchMapPojo b = createPojo("path", "test", "app");
        assertEquals(a, b);
    }

    @Test
    public void equals_differentValues_returnsFalse() {
        BatchMapPojo a = createPojo("path1", "test", "app");
        BatchMapPojo b = createPojo("path2", "test", "app");
        assertNotEquals(a, b);
    }

    @Test
    public void equals_null_returnsFalse() {
        BatchMapPojo a = createPojo("path", "test", "app");
        assertNotEquals(a, null);
    }

    // NOTE: toString() only includes filePath, testName, app, os, browser, viewport,
    // matchsize, pages, and matchLevel — not layoutRegions, contentRegions, or ignoreRegions.
    @Test
    public void toString_containsFieldValues() {
        BatchMapPojo pojo = createPojo("/my/path", "MyTest", "MyApp");
        String str = pojo.toString();
        assertTrue(str.contains("/my/path"));
        assertTrue(str.contains("MyTest"));
        assertTrue(str.contains("MyApp"));
    }

    private BatchMapPojo createPojo(String path, String testName, String app) {
        BatchMapPojo pojo = new BatchMapPojo();
        pojo.setFilePath(path);
        pojo.setTestName(testName);
        pojo.setApp(app);
        return pojo;
    }
}
