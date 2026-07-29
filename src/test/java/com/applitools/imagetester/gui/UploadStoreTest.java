package com.applitools.imagetester.gui;

import org.junit.After;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

public class UploadStoreTest {

    private final UploadStore store = new UploadStore();

    @After
    public void tearDown() throws Exception {
        store.deleteAll();
    }

    private static ByteArrayInputStream bytes(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void savesBodyToDiskAtReturnedPath() throws Exception {
        Path saved = store.save("a.pdf", bytes("content"));
        assertEquals("content", new String(Files.readAllBytes(saved), StandardCharsets.UTF_8));
    }

    @Test
    public void keepsOriginalFileNameInReturnedPath() throws Exception {
        Path saved = store.save("contract.pdf", bytes("x"));
        assertEquals("contract.pdf", saved.getFileName().toString());
    }

    @Test
    public void sameNameTwiceYieldsDistinctPaths() throws Exception {
        Path first = store.save("contract.pdf", bytes("v2"));
        Path second = store.save("contract.pdf", bytes("v3"));
        assertNotEquals(first, second);
    }

    @Test
    public void rejectsNameWithForwardSlash() {
        assertThrows(UploadStore.InvalidNameException.class, () -> store.save("a/b.pdf", bytes("x")));
    }

    @Test
    public void rejectsNameWithBackslash() {
        assertThrows(UploadStore.InvalidNameException.class, () -> store.save("a\\b.pdf", bytes("x")));
    }

    @Test
    public void rejectsParentDirectoryAsName() {
        assertThrows(UploadStore.InvalidNameException.class, () -> store.save("..", bytes("x")));
    }

    @Test
    public void rejectsNameWithColon() {
        assertThrows(UploadStore.InvalidNameException.class, () -> store.save("D:evil.pdf", bytes("x")));
    }

    @Test
    public void rejectsEmptyName() {
        assertThrows(UploadStore.InvalidNameException.class, () -> store.save("", bytes("x")));
    }

    @Test
    public void rejectsNullName() {
        assertThrows(UploadStore.InvalidNameException.class, () -> store.save(null, bytes("x")));
    }

    @Test
    public void rejectsBodyLargerThanCap() throws Exception {
        UploadStore small = new UploadStore(4);
        assertThrows(UploadStore.TooLargeException.class, () -> small.save("a.pdf", bytes("12345")));
        small.deleteAll();
    }

    @Test
    public void deleteAllRemovesSavedFiles() throws Exception {
        Path saved = store.save("a.pdf", bytes("x"));
        store.deleteAll();
        assertFalse(Files.exists(saved));
    }
}
