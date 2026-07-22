package com.applitools.imagetester.lib;

/**
 * Classifies text runs for per-script font normalization. A run counts as
 * Japanese if it contains at least one code point from the Japanese-relevant
 * Unicode blocks (spec: docs/superpowers/specs/2026-07-22-japanese-font-normalization-design.md).
 */
public final class JapaneseText {

    private JapaneseText() {
    }

    public static boolean containsJapanese(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (isJapanese(cp)) {
                return true;
            }
            i += Character.charCount(cp);
        }
        return false;
    }

    private static boolean isJapanese(int cp) {
        return (cp >= 0x3000 && cp <= 0x303F)   // CJK symbols & punctuation (includes 〓)
            || (cp >= 0x3040 && cp <= 0x309F)   // Hiragana
            || (cp >= 0x30A0 && cp <= 0x30FF)   // Katakana
            || (cp >= 0x31F0 && cp <= 0x31FF)   // Katakana phonetic extensions
            || (cp >= 0x3400 && cp <= 0x4DBF)   // CJK Unified Ideographs Extension A
            || (cp >= 0x4E00 && cp <= 0x9FFF)   // CJK Unified Ideographs
            || (cp >= 0xFF00 && cp <= 0xFFEF);  // Fullwidth and halfwidth forms
    }
}
