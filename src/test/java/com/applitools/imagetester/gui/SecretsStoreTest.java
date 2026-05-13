package com.applitools.imagetester.gui;

import org.junit.Test;
import static org.junit.Assert.*;

public class SecretsStoreTest {

    @Test
    public void roundTripPutGetDelete() {
        SecretsStore store = SecretsStore.inMemoryForTest();
        assertFalse(store.hasApiKey());
        store.setApiKey("sk_live_xyz");
        assertTrue(store.hasApiKey());
        assertEquals("sk_live_xyz", store.getApiKey());
        store.deleteApiKey();
        assertFalse(store.hasApiKey());
    }

    @Test
    public void setOverwrites() {
        SecretsStore store = SecretsStore.inMemoryForTest();
        store.setApiKey("a");
        store.setApiKey("b");
        assertEquals("b", store.getApiKey());
    }
}
