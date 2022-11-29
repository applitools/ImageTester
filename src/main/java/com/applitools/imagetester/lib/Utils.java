package com.applitools.imagetester.lib;

import com.applitools.eyes.TestResults;
import com.applitools.commands.AnimatedDiffs;
import com.applitools.commands.DownloadDiffs;
import com.applitools.commands.DownloadImages;
import org.apache.commons.lang3.StringUtils;

import javax.net.ssl.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class Utils {

    public static void disableCertValidation() throws KeyManagementException, NoSuchAlgorithmException {

        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return null;
        }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }
        };

        // Install the all-trusting trust manager
        SSLContext sc = SSLContext.getInstance("SSL");
        sc.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

        // Create all-trusting host name verifier
        HostnameVerifier allHostsValid = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };

        // Install the all-trusting host verifier
        HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
    }

    public static <T extends Enum<T>> T parseEnum(Class<T> c, String string) {
        if (c != null && string != null) {
            try {
                return Enum.valueOf(c, string.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
            }
        }
        throw new RuntimeException(String.format("Unable to parse value %s for enum %s", string, c.getName()));
    }


    public static <T extends Enum<T>> T parseEnum(Class<T> c, String s, String avoid_chars) {
        T[] values = c.getEnumConstants();
        for (T value : values) {
            String ename = value.name().replace(avoid_chars, "");
            if (ename.equalsIgnoreCase(s.replace(avoid_chars, "")))
                return value;
        }

        throw new RuntimeException(String.format("Unable to parse value %s for enum %s", s, c.getName()));
    }

    public static String getEnumValues(Class type) {
        StringBuilder sb = new StringBuilder();
        for (Object val : EnumSet.allOf(type)) {
            sb.append(StringUtils.capitalize(val.toString().toLowerCase()));
            sb.append('|');
        }
        return sb.substring(0, sb.length() - 1);
    }

    public static List<Integer> parsePagesNotation(String input) {
        if (input == null || input.isEmpty()) return null;
        ArrayList<Integer> pagesToInclude = new ArrayList<>();
        String[] inputPages = input.split(",");
        for (String inputPage : inputPages) {
            inputPage = inputPage.replaceAll("\\s", "");
            if (inputPage.contains("-")) {
                String[] splits = inputPage.split("-");
                int left = Integer.parseInt(splits[0]);
                int right = Integer.parseInt(splits[1]);
                if (left <= right)
                    for (int j = left; j <= right; pagesToInclude.add(j++)) ;
                else
                    for (int j = left; j >= right; pagesToInclude.add(j--)) ;
            } else
                pagesToInclude.add(Integer.valueOf(inputPage));
        }
        return pagesToInclude;
    }

    public static List<Integer> generateRanage(int range, int start) {
        List<Integer> retRange = new ArrayList<>(range);
        for (int i = start; i < range; ++i)
            retRange.add(i);
        return retRange;
    }

    public static void handleResultsDownload(EyesUtilitiesConfig config, TestResults results) throws Exception {
        if (config == null) return;
        if (config.getDownloadDiffs() || config.getGetGifs() || config.getGetImages()) {
            if (config.getViewKey() == null) throw new RuntimeException("The view-key cannot be null");
            if (config.getDownloadDiffs() && !results.isNew() && !results.isPassed())
                new DownloadDiffs(results.getUrl(), config.getDestinationFolder(), config.getViewKey()).run();
            if (config.getGetGifs() && !results.isNew() && !results.isPassed())
                new AnimatedDiffs(results.getUrl(), config.getDestinationFolder(), config.getViewKey()).run();
            if (config.getGetImages())
                new DownloadImages(results.getUrl(), config.getDestinationFolder(), config.getViewKey(), false, false).run();
        }
    }
}
