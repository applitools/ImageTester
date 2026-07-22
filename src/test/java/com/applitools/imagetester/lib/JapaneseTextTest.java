package com.applitools.imagetester.lib;

import static org.junit.Assert.*;

import org.junit.Test;

public class JapaneseTextTest {

    @Test
    public void should_detect_hiragana() {
        assertTrue(JapaneseText.containsJapanese("ひらがな"));
    }

    @Test
    public void should_detect_katakana() {
        assertTrue(JapaneseText.containsJapanese("カタカナ"));
    }

    @Test
    public void should_detect_kanji() {
        assertTrue(JapaneseText.containsJapanese("変更手続"));
    }

    @Test
    public void should_detect_cjk_punctuation() {
        assertTrue(JapaneseText.containsJapanese("「こんにちは」"));
        assertTrue(JapaneseText.containsJapanese("〓"));
    }

    @Test
    public void should_detect_fullwidth_forms() {
        assertTrue(JapaneseText.containsJapanese("２０２５"));
    }

    @Test
    public void should_detect_japanese_mixed_with_latin() {
        assertTrue(JapaneseText.containsJapanese("2025年10月31日"));
    }

    @Test
    public void should_not_detect_pure_ascii() {
        assertFalse(JapaneseText.containsJapanese("Hello World 2025000013"));
    }

    @Test
    public void should_not_detect_accented_latin() {
        assertFalse(JapaneseText.containsJapanese("café naïve Zürich"));
    }

    @Test
    public void should_return_false_for_null_and_empty() {
        assertFalse(JapaneseText.containsJapanese(null));
        assertFalse(JapaneseText.containsJapanese(""));
    }
}
