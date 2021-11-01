package com.yanirta.lib;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by liranbarokas on 08/05/2017.
 */
public class EyesUtilitiesConfig {
    private final String DEFAULT_DEST_PATH_TEMPL = "{workdir_root}/Artifacts/{batch_id}/{test_id}/file:{step_index}_{step_tag}_{artifact_type}.{file_ext}";
    private String viewKey_;
    private String destinationFolder_;
    private Boolean downloadDiffs_ = false;
    private Boolean getImages_ = false;
    private Boolean getGifs_ = false;


    private EyesUtilitiesConfig(String viewKey, String destinationFolder, Boolean downloadDiffs, Boolean getImages, Boolean getGifs) {
        viewKey_ = viewKey;
        destinationFolder_ = destinationFolder;
        downloadDiffs_ = downloadDiffs;
        getImages_ = getImages;
        getGifs_ = getGifs;
    }

    public EyesUtilitiesConfig(CommandLine cmd) throws ParseException {
        if (cmd.hasOption("gd") || cmd.hasOption("gi") || cmd.hasOption("gg")) {
            viewKey_ = cmd.getOptionValue("vk", System.getenv("APPLITOOLS_VIEW_KEY"));
            if (viewKey_ == null || StringUtils.isEmpty(viewKey_))
                throw new ParseException("gd|gi|gg must be called with enterprise view-key (vk)");
            destinationFolder_ = cmd.getOptionValue("of", DEFAULT_DEST_PATH_TEMPL);
            downloadDiffs_ = cmd.hasOption("gd");
            getImages_ = cmd.hasOption("gi");
            getGifs_ = cmd.hasOption("gg");
        }
    }

    public static void injectOptions(Options options) {
        options.addOption(Option.builder("vk")
                .longOpt("viewKey")
                .desc("Specify enterprise view-key for additional api functions")
                .hasArg()
                .argName("key")
                .build()
        );

        options.addOption(Option.builder("of")
                .longOpt("outFolder")
                .hasArg()
                .desc("Specify the output target folder for the images results")
                .argName("folder")
                .build()
        );

        options.addOption(Option.builder("gd")
                .longOpt("getDiffs")
                .desc("Download diffs")
                .hasArg(false)
                .build()
        );

        options.addOption(Option.builder("gi")
                .longOpt("getImages")
                .desc("Download baseline and actual images")
                .hasArg(false)
                .build()
        );

        options.addOption(Option.builder("gg")
                .longOpt("getGifs")
                .desc("Download animated gif of the results")
                .hasArg(false)
                .build()
        );
    }

    public String getViewKey() {
        return viewKey_;
    }

    public void setViewKey(String viewKey) {
        this.viewKey_ = viewKey;
    }

    public String getDestinationFolder() {
        return destinationFolder_;
    }

    public void setDestinationFolder(String destinationFolder) {
        this.destinationFolder_ = destinationFolder;
    }

    public Boolean getDownloadDiffs() {
        return downloadDiffs_;
    }

    public void setDownloadDiffs(Boolean downloadDiffs) {
        this.downloadDiffs_ = downloadDiffs;
    }

    public Boolean getGetImages() {
        return getImages_;
    }

    public void setGetImages(Boolean getImages) {
        this.getImages_ = getImages;
    }

    public Boolean getGetGifs() {
        return getGifs_;
    }

    public void setGetGifs(Boolean getGifs) {
        this.getGifs_ = getGifs;
    }
    
}
