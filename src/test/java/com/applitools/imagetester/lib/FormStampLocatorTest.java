package com.applitools.imagetester.lib;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSName;
import org.junit.Test;

public class FormStampLocatorTest {

    @Test
    public void picks_up_simple_cm_then_Do_pair() {
        List<Object> tokens = Arrays.asList(
                new COSFloat(1f), new COSFloat(0f),
                new COSFloat(0f), new COSFloat(1f),
                new COSFloat(100f), new COSFloat(200f),
                Operator.getOperator("cm"),
                COSName.getPDFName("R0"),
                Operator.getOperator("Do")
        );

        List<FormStampLocator.StampSite> sites = FormStampLocator.locate(tokens);

        assertEquals(1, sites.size());
        FormStampLocator.StampSite s = sites.get(0);
        assertEquals("R0", s.formName);
        assertArrayEquals(new float[] { 1f, 0f, 0f, 1f, 100f, 200f }, s.cm, 0.001f);
    }

    @Test
    public void multiple_stamps_each_capture_their_own_cm() {
        List<Object> tokens = new java.util.ArrayList<>();
        tokens.addAll(cmDo(1f, 0f, 0f, 1f, 100f, 200f, "R"));
        tokens.addAll(cmDo(2f, 0f, 0f, 2f, 50f, 75f, "R0"));

        List<FormStampLocator.StampSite> sites = FormStampLocator.locate(tokens);

        assertEquals(2, sites.size());
        assertEquals("R", sites.get(0).formName);
        assertEquals("R0", sites.get(1).formName);
        assertArrayEquals(new float[] { 1f, 0f, 0f, 1f, 100f, 200f }, sites.get(0).cm, 0.001f);
        assertArrayEquals(new float[] { 2f, 0f, 0f, 2f, 50f, 75f }, sites.get(1).cm, 0.001f);
    }

    @Test
    public void Do_without_preceding_cm_is_skipped() {
        List<Object> tokens = Arrays.asList(
                COSName.getPDFName("Logo"),
                Operator.getOperator("Do")
        );

        List<FormStampLocator.StampSite> sites = FormStampLocator.locate(tokens);

        assertTrue("Do without a preceding cm has no measurable position", sites.isEmpty());
    }

    private List<Object> cmDo(float a, float b, float c, float d, float e, float f, String formName) {
        return Arrays.asList(
                new COSFloat(a), new COSFloat(b),
                new COSFloat(c), new COSFloat(d),
                new COSFloat(e), new COSFloat(f),
                Operator.getOperator("cm"),
                COSName.getPDFName(formName),
                Operator.getOperator("Do")
        );
    }
}
