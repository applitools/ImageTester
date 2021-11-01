package com.yanirta.lib;

import java.util.regex.Pattern;

/**
 * Created by yanir on 01/09/2016.
 */
public abstract class Patterns {
    private static final String IMAGE_EXT = "(\\.(?i)(jpeg|jpg|png|gif|bmp))$";
    public static final Pattern IMAGE = Pattern.compile("(.+)" + IMAGE_EXT);

    private static final String PDF_EXT = "(?i)(\\.pdf)$";
    public static final Pattern PDF = Pattern.compile("(.+)" + PDF_EXT);

    private static final String PS_EXT = "(?i)(\\.ps)$";
    public static final Pattern POSTSCRIPT = Pattern.compile("(.+)" + PS_EXT);
}
