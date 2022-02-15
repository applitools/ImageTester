package com.yanirta.BatchMapper;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({ "directory", "fileName", "pages", "testName", "os", "app", "batchName"})
public class BatchMapPojo {
    public String directory;
    public String fileName;
    public String pages;
    public String testName;
    public String os;
    public String app;
    public String batchName;

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public String getPages() {
        return pages;
    }

    public void setPages(String pages) {
        this.pages = pages;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public String getBatchName() {
        return batchName;
    }

    public void setBatchName(String batchName) {
        this.batchName = batchName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BatchMapPojo that = (BatchMapPojo) o;
        return Objects.equals(directory, that.directory) && Objects.equals(fileName, that.fileName) &&
               Objects.equals(pages, that.pages) && Objects.equals(testName, that.testName) &&
               Objects.equals(os, that.os) && Objects.equals(app, that.app) &&
               Objects.equals(batchName, that.batchName);
    }

    @Override
    public String toString() {
        return  "\n---------------------------------- \n" +
                "Test being run: \n" +
                "directory='" + directory + "'\n" +
                "fileName='" + fileName + "'\n" +
                "pages='" + pages + "'\n" +
                "testName='" + testName + "'\n" +
                "os='" + os + "'\n" +
                "app='" + app + "'\n" +
                "batchName='" + batchName + "'\n" +
                "---------------------------------- \n";
    }
}
