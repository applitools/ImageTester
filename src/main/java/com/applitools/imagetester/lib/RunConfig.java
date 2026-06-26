package com.applitools.imagetester.lib;

public final class RunConfig {
    public final Config config;
    public final EyesFactory factory;
    public final int threads;

    public RunConfig(Config config, EyesFactory factory, int threads) {
        this.config = config;
        this.factory = factory;
        this.threads = threads;
    }
}
