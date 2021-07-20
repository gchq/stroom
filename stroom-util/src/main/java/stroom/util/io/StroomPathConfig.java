package stroom.util.io;

import stroom.util.config.annotations.ReadOnly;
import stroom.util.shared.validation.ValidDirectoryPath;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.annotation.Nonnull;
import javax.inject.Singleton;

@Singleton
public class StroomPathConfig extends PathConfig {

    private static final String DEFAULT_HOME_DIR = ".";
    private static final String DEFAULT_TEMP_DIR = "/tmp/stroom";

    public StroomPathConfig() {
        super(DEFAULT_HOME_DIR, DEFAULT_TEMP_DIR);
    }

    @Override
    @ReadOnly
    @ValidDirectoryPath
    @JsonPropertyDescription("By default, unless configured otherwise, all other configured paths " +
            "(except stroom.path.temp) will be relative to this directory. If this value is null then" +
            "Stroom will use either of the following to derive stroom.path.home: the directory of the Stroom " +
            "application JAR file or ~/.stroom. Should only be set per node in application YAML configuration file")
    public String getHome() {
        return super.getHome();
    }

    @Override
    @Nonnull
    @ReadOnly
    @ValidDirectoryPath
    @JsonPropertyDescription("This directory is used by stroom to write any temporary file to. " +
            "Should only be set per node in application YAML configuration file.")
    public String getTemp() {
        return super.getTemp();
    }
}
