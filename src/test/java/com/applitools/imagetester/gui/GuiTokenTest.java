package com.applitools.imagetester.gui;

import org.junit.Test;
import static org.junit.Assert.*;

public class GuiTokenTest {

    @Test
    public void generatedTokenIs43OrMoreUrlSafeChars() {
        GuiToken t = GuiToken.generate();
        assertTrue(t.value().length() >= 43);
        assertTrue(t.value().matches("[A-Za-z0-9_-]+"));
    }

    @Test
    public void twoGeneratedTokensAreUnequal() {
        assertNotEquals(GuiToken.generate().value(), GuiToken.generate().value());
    }

    @Test
    public void verifyAcceptsMatchingToken() {
        GuiToken t = GuiToken.generate();
        assertTrue(t.verify(t.value()));
    }

    @Test
    public void verifyRejectsDifferentToken() {
        GuiToken t = GuiToken.generate();
        assertFalse(t.verify("not-the-token"));
    }

    @Test
    public void verifyRejectsNullOrEmpty() {
        GuiToken t = GuiToken.generate();
        assertFalse(t.verify(null));
        assertFalse(t.verify(""));
    }
}
