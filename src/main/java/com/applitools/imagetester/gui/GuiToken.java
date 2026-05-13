package com.applitools.imagetester.gui;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class GuiToken {

    private final String value_;
    private final byte[] bytes_;

    private GuiToken(String value) {
        this.value_ = value;
        this.bytes_ = value.getBytes();
    }

    public static GuiToken generate() {
        byte[] raw = new byte[32]; // 256-bit
        new SecureRandom().nextBytes(raw);
        return new GuiToken(Base64.getUrlEncoder().withoutPadding().encodeToString(raw));
    }

    public String value() { return value_; }

    public boolean verify(String candidate) {
        if (candidate == null || candidate.isEmpty()) return false;
        return MessageDigest.isEqual(bytes_, candidate.getBytes());
    }
}
