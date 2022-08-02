package com.yanirta.TestObjects;

import com.yanirta.lib.Config;
import com.yanirta.lib.Utils;

import java.io.File;
import java.util.List;

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
