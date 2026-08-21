package com.applitools.imagetester.lib;

import java.util.regex.Pattern;

public abstract class Patterns {
    private static final String IMAGE_EXT = "(\\.(?i)(jpeg|jpg|png|gif|bmp|tif|tiff))$";
    public static final Pattern IMAGE = Pattern.compile("(.+)" + IMAGE_EXT);

    private static final String PDF_EXT = "(?i)(\\.pdf)$";
    public static final Pattern PDF = Pattern.compile("(.+)" + PDF_EXT);

    public static final Pattern TEXT = Pattern.compile("(.+)(\\.(?i)(txt))$");
    public static final Pattern MARKDOWN = Pattern.compile("(.+)(\\.(?i)(md))$");
    public static final Pattern RTF = Pattern.compile("(.+)(\\.(?i)(rtf))$");

    public static final Pattern WORD = Pattern.compile("(.+)(\\.(?i)(doc|dot|docx|docm|dotx|dotm))$");
    public static final Pattern POWERPOINT = Pattern.compile("(.+)(\\.(?i)(ppt|pptx|pptm|pps|ppsx|ppsm|pot|potx|potm))$");
    public static final Pattern SPREADSHEET = Pattern.compile("(.+)(\\.(?i)(xls|xlsx|xlsm|xlt|xltx|xltm|ods|csv))$");
    public static final Pattern VECTOR = Pattern.compile("(.+)(\\.(?i)(ps|eps|xps))$");
    // The VECTOR formats LibreOffice cannot actually import (no PS/XPS filter — it falls
    // back to a plain-text Writer import of the raw source). EPS is excluded: Draw has a
    // real EPS import filter.
    public static final Pattern POSTSCRIPT_XPS = Pattern.compile("(.+)(\\.(?i)(ps|xps))$");
}
