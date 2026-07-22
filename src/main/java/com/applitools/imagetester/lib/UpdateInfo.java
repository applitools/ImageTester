package com.applitools.imagetester.lib;

/** A newer release found on GitHub. Fields are empty strings, never null, when a piece is missing. */
public final class UpdateInfo {
    public final String version;
    public final String assetName;
    public final String downloadUrl;
    public final String checksumUrl;
    public final String releasePageUrl;

    public UpdateInfo(String version, String assetName, String downloadUrl, String checksumUrl, String releasePageUrl) {
        this.version = version;
        this.assetName = assetName;
        this.downloadUrl = downloadUrl;
        this.checksumUrl = checksumUrl;
        this.releasePageUrl = releasePageUrl;
    }
}
