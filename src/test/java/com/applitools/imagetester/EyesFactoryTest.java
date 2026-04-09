package com.applitools.imagetester;

import com.applitools.imagetester.lib.EyesFactory;
import com.applitools.imagetester.lib.Logger;
import org.junit.Test;
import static org.junit.Assert.*;

public class EyesFactoryTest {

    private EyesFactory createFactory() {
        return new EyesFactory("test", new Logger());
    }

    @Test
    public void imageCut_fourValues() {
        EyesFactory factory = createFactory().imageCut(new String[]{"10", "20", "30", "40"});
        assertNotNull(factory);
    }

    @Test
    public void imageCut_partialValues() {
        EyesFactory factory = createFactory().imageCut(new String[]{"10"});
        assertNotNull(factory);
    }

    @Test
    public void imageCut_emptyStringsBecomeZero() {
        EyesFactory factory = createFactory().imageCut(new String[]{"", "", "10", "4"});
        assertNotNull(factory);
    }

    @Test
    public void imageCut_null_isNoOp() {
        EyesFactory factory = createFactory().imageCut(null);
        assertNotNull(factory);
    }

    @Test(expected = IllegalArgumentException.class)
    public void imageCut_moreThanFourValues_throws() {
        createFactory().imageCut(new String[]{"1", "2", "3", "4", "5"});
    }

    @Test
    public void accSettings_emptyArray_usesDefaults() {
        EyesFactory factory = createFactory().accSettings(new String[]{});
        assertNotNull(factory);
    }

    @Test
    public void accSettings_singleValue() {
        EyesFactory factory = createFactory().accSettings(new String[]{"AAA"});
        assertNotNull(factory);
    }

    @Test
    public void accSettings_twoValues() {
        EyesFactory factory = createFactory().accSettings(new String[]{"AA", "WCAG_2_1"});
        assertNotNull(factory);
    }

    @Test
    public void accSettings_null_isNoOp() {
        EyesFactory factory = createFactory().accSettings(null);
        assertNotNull(factory);
    }

    @Test(expected = IllegalArgumentException.class)
    public void accSettings_moreThanTwoValues_throws() {
        createFactory().accSettings(new String[]{"AA", "WCAG_2_1", "extra"});
    }

    @Test(expected = RuntimeException.class)
    public void build_parentBranchWithoutBranch_throws() {
        createFactory()
                .apiKey("fake-key")
                .parentBranch("parent")
                .build();
    }
}
