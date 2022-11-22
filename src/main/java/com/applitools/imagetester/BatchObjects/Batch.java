package com.applitools.imagetester.BatchObjects;

import com.applitools.eyes.BatchInfo;
import com.applitools.imagetester.lib.Config;

import java.io.File;

public class Batch extends BatchBase {

    public Batch(File file) {
        super(new BatchInfo(file.getName()));
    }

    public Batch(String name) {
        super(new BatchInfo(name));
    }

    public Batch() {
        super(new BatchInfo());
    }
}