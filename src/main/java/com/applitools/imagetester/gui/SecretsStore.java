package com.applitools.imagetester.gui;

import com.github.javakeyring.Keyring;
import com.github.javakeyring.PasswordAccessException;

public final class SecretsStore {

    private static final String SERVICE = "Applitools ImageTester";
    private static final String ACCOUNT = "apiKey";

    public interface Backend {
        String get() throws Exception;
        void set(String value) throws Exception;
        void delete() throws Exception;
    }

    private final Backend backend_;

    private SecretsStore(Backend backend) { this.backend_ = backend; }

    public static SecretsStore forProduction() {
        return new SecretsStore(new KeyringBackend());
    }

    public static SecretsStore inMemoryForTest() {
        return new SecretsStore(new InMemoryBackend());
    }

    public boolean hasApiKey() {
        try {
            String v = backend_.get();
            return v != null && !v.isEmpty();
        } catch (Exception e) { return false; }
    }

    public String getApiKey() {
        try { return backend_.get(); } catch (Exception e) { return null; }
    }

    public void setApiKey(String value) {
        if (value == null || value.isEmpty()) {
            deleteApiKey();
            return;
        }
        try { backend_.set(value); } catch (Exception e) {
            throw new RuntimeException("Could not save API key: " + e.getMessage(), e);
        }
    }

    public void deleteApiKey() {
        try { backend_.delete(); } catch (Exception ignored) {}
    }

    private static final class KeyringBackend implements Backend {
        @Override public String get() throws Exception {
            try (Keyring kr = Keyring.create()) {
                try { return kr.getPassword(SERVICE, ACCOUNT); }
                catch (PasswordAccessException e) { return null; }
            }
        }
        @Override public void set(String value) throws Exception {
            try (Keyring kr = Keyring.create()) { kr.setPassword(SERVICE, ACCOUNT, value); }
        }
        @Override public void delete() throws Exception {
            try (Keyring kr = Keyring.create()) {
                try { kr.deletePassword(SERVICE, ACCOUNT); }
                catch (PasswordAccessException ignored) {}
            }
        }
    }

    private static final class InMemoryBackend implements Backend {
        private String value_;
        @Override public synchronized String get() { return value_; }
        @Override public synchronized void set(String value) { this.value_ = value; }
        @Override public synchronized void delete() { this.value_ = null; }
    }
}
