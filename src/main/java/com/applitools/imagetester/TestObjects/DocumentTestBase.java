package com.applitools.imagetester.TestObjects;

import java.io.File;
import java.util.List;

import com.applitools.imagetester.lib.Config;
import com.applitools.imagetester.lib.Utils;

public abstract class DocumentTestBase extends TestBase {
    protected List<Integer> pageList_;

    public DocumentTestBase(File file, Config conf) {
        super(file, conf);
        this.pageList_ = Utils.parsePagesNotation(conf.pages);
    }

    @Override
    public String name() {
    	String testName = super.name();
        String pagesText = config().pages != null && config().includePageNumbers ? String.format(" pages [%s]", config().pages) : "";
        return testName + pagesText;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }
}
