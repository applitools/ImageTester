package com.applitools.imagetester.lib;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ConfigTest {

    private Config config;

    @Before
    public void setUp() {
        config = new Config();
    }

    @Test
    public void setViewport_validDimensions() {
        config.setViewport("1000x600");
        assertEquals(1000, config.viewport.getWidth());
        assertEquals(600, config.viewport.getHeight());
    }

    @Test
    public void setViewport_null_isNoOp() {
        config.setViewport(null);
        assertNull(config.viewport);
    }

    @Test(expected = RuntimeException.class)
    public void setViewport_missingDimension_throws() {
        config.setViewport("1000");
    }

    @Test(expected = NumberFormatException.class)
    public void setViewport_nonNumeric_throws() {
        config.setViewport("abcxdef");
    }

    @Test
    public void setMatchSize_widthOnly() {
        config.setMatchSize("1000x");
        assertEquals("1000", config.matchWidth);
        // Java split("x") on "1000x" discards trailing empty — matchHeight is never assigned
        assertNull(config.matchHeight);
    }

    @Test
    public void setMatchSize_heightOnly() {
        config.setMatchSize("x600");
        assertEquals("", config.matchWidth);
        assertEquals("600", config.matchHeight);
    }

    @Test
    public void setMatchSize_both() {
        config.setMatchSize("1000x600");
        assertEquals("1000", config.matchWidth);
        assertEquals("600", config.matchHeight);
    }

    @Test
    public void setMatchSize_null_isNoOp() {
        config.setMatchSize(null);
        assertNull(config.matchWidth);
        assertNull(config.matchHeight);
    }

    @Test
    public void setProxy_singleArg() {
        config.setProxy(new String[]{"http://proxy.example.com"});
        assertNotNull(config.proxy_settings);
    }

    @Test
    public void setProxy_threeArgs() {
        config.setProxy(new String[]{"http://proxy.example.com", "user", "pass"});
        assertNotNull(config.proxy_settings);
    }

    @Test(expected = RuntimeException.class)
    public void setProxy_twoArgs_throws() {
        config.setProxy(new String[]{"http://proxy.example.com", "user"});
    }

    @Test
    public void setProxy_null_isNoOp() {
        config.setProxy(null);
        assertNull(config.proxy_settings);
    }

    @Test
    public void setProxy_emptyArray_isNoOp() {
        config.setProxy(new String[]{});
        assertNull(config.proxy_settings);
    }

    @Test
    public void setProperties_validSingle() {
        config.setProperties("key1:val1");
        assertEquals(1, config.properties.length);
        assertEquals("key1", config.properties[0][0]);
        assertEquals("val1", config.properties[0][1]);
    }

    @Test
    public void setProperties_validMultiple() {
        config.setProperties("key1:val1|key2:val2");
        assertEquals(2, config.properties.length);
        assertEquals("key2", config.properties[1][0]);
        assertEquals("val2", config.properties[1][1]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setProperties_invalidFormat_throws() {
        config.setProperties("invalid_no_colon");
    }

    @Test
    public void setProperties_null_isNoOp() {
        config.setProperties(null);
        assertNull(config.properties);
    }

    @Test
    public void setProperties_empty_isNoOp() {
        config.setProperties("");
        assertNull(config.properties);
    }

    @Test
    public void setIgnoreRegions_singleRegion() {
        config.setIgnoreRegions("10,20,100,50");
        assertNotNull(config.ignoreRegions);
        assertEquals(1, config.ignoreRegions.length);
        assertEquals(10, config.ignoreRegions[0].getLeft());
        assertEquals(20, config.ignoreRegions[0].getTop());
        assertEquals(100, config.ignoreRegions[0].getWidth());
        assertEquals(50, config.ignoreRegions[0].getHeight());
    }

    @Test
    public void setIgnoreRegions_multipleRegions() {
        config.setIgnoreRegions("10,20,100,50|30,40,200,100");
        assertEquals(2, config.ignoreRegions.length);
    }

    @Test
    public void setIgnoreRegions_null_isNoOp() {
        config.setIgnoreRegions(null);
        assertNull(config.ignoreRegions);
    }

    @Test
    public void setIgnoreRegions_malformedDoesNotCrash() {
        config.setIgnoreRegions("10,20");
    }

    @Test
    public void setContentRegions_validRegion() {
        config.setContentRegions("10,20,100,50");
        assertNotNull(config.contentRegions);
        assertEquals(1, config.contentRegions.length);
    }

    @Test
    public void setContentRegions_null_isNoOp() {
        config.setContentRegions(null);
        assertNull(config.contentRegions);
    }

    @Test
    public void setLayoutRegions_validRegion() {
        config.setLayoutRegions("10,20,100,50");
        assertNotNull(config.layoutRegions);
        assertEquals(1, config.layoutRegions.length);
    }

    @Test
    public void setLayoutRegions_null_isNoOp() {
        config.setLayoutRegions(null);
        assertNull(config.layoutRegions);
    }

    @Test
    public void setCaptureRegion_valid() {
        config.setCaptureRegion("0,200,1000,1000");
        assertNotNull(config.captureRegion);
        assertEquals(0, config.captureRegion.getLeft());
        assertEquals(200, config.captureRegion.getTop());
        assertEquals(1000, config.captureRegion.getWidth());
        assertEquals(1000, config.captureRegion.getHeight());
    }

    @Test
    public void setCaptureRegion_null_isNoOp() {
        config.setCaptureRegion(null);
        assertNull(config.captureRegion);
    }

    @Test(expected = RuntimeException.class)
    public void setCaptureRegion_wrongCount_throws() {
        config.setCaptureRegion("0,200,1000");
    }

    @Test
    public void setBatchInfo_withName() {
        config.setBatchInfo("MyBatch", false);
        assertNotNull(config.flatBatch);
    }

    @Test
    public void setBatchInfo_withNameAndId() {
        config.setBatchInfo("MyBatch<>batch-123", false);
        assertNotNull(config.flatBatch);
        assertEquals("batch-123", config.flatBatch.getId());
    }

    @Test
    public void setBatchInfo_null_isNoOp() {
        config.setBatchInfo(null, false);
        // flatBatch may be set from env vars, so just verify no crash
    }

    @Test
    public void setBatchInfo_setsNotifyOnComplete() {
        config.setBatchInfo("MyBatch", true);
        assertTrue(config.notifyOnComplete);
    }

    @Test
    public void setPdfTrim_auto() {
        config.setPdfTrim("auto");
        assertEquals("auto", config.pdfTrim);
    }

    @Test
    public void setPdfTrim_dimensions() {
        config.setPdfTrim("603x774");
        assertEquals("603x774", config.pdfTrim);
    }

    @Test
    public void setPdfTrim_null_isNoOp() {
        config.setPdfTrim(null);
        assertNull(config.pdfTrim);
    }

    @Test(expected = RuntimeException.class)
    public void setPdfTrim_malformed_throws() {
        config.setPdfTrim("abc");
    }

    @Test(expected = RuntimeException.class)
    public void setPdfTrim_nonPositiveDimension_throws() {
        config.setPdfTrim("0x774");
    }

    @Test
    public void setMatchTimeout_storesValue() {
        config.setMatchTimeout("2000");
        assertEquals("2000", config.getMatchTimeout());
    }

    @Test
    public void setMatchTimeout_null() {
        config.setMatchTimeout(null);
        assertNull(config.getMatchTimeout());
    }
}
