package com.yanirta.BatchMapper;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({ "filePath", "testName", "app", "os", "browser", "viewport", "matchsize", "pages","matchLevel"})
public class BatchMapPojo {
    public String filePath;
    public String testName;
    public String app;
    public String os;
    public String browser;
    public String viewport;
    public String matchsize;
    public String pages;
    public String matchLevel;

    public String filePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }    
    
    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }
    
    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }
    
    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }
    
    public String getBrowser() {
        return browser;
    }

    public void setBrowser(String browser) {
        this.browser = browser;
    }

    public String getViewport() {
        return viewport;
    }

    public void setViewport(String viewport) {
        this.viewport = viewport;
    }

    public String getMatchsize() {
        return matchsize;
    }

    public void setMatchsize(String matchsize) {
        this.matchsize = matchsize;
    }
    
    public String getPages() {
        return pages;
    }

    public void setPages(String pages) {
        this.pages = pages;
    }
    
    public String getMatchLevel() {
        return matchLevel;
    }

    public void setMatchLevel(String matchLevel) {
        this.matchLevel = matchLevel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BatchMapPojo that = (BatchMapPojo) o;
        return Objects.equals(filePath, that.filePath) && Objects.equals(testName, that.testName) && Objects.equals(app, that.app)
        		&& Objects.equals(os, that.os) && Objects.equals(browser, that.browser) && Objects.equals(viewport, that.viewport)
        		&& Objects.equals(matchsize, that.matchsize) && Objects.equals(pages, that.pages) && Objects.equals(matchLevel, that.matchLevel);
               
    }
    
    @Override
    public String toString() {
        return  "\n---------------------------------- \n" +
                "Test being run: \n" +
                "filePath='" + filePath + "'\n" +
                "testName='" + testName + "'\n" +
                "app='" + app + "'\n" +
                "os='" + os + "'\n" +
                "browser='" + browser + "'\n" +
                "viewport='" + viewport + "'\n" +
                "matchsize='" + matchsize + "'\n" +
                "pages='" + pages + "'\n" +
                "matchLevel='" + matchLevel + "'\n" +
                "---------------------------------- \n";
    }
}
