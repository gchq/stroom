package stroom.util.io;

import stroom.util.config.annotations.ReadOnly;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.annotation.Nonnull;
import javax.inject.Singleton;

@BootStrapConfig
@Singleton
@JsonPropertyOrder(alphabetic = true)
public class StroomPathConfig extends PathConfig implements IsStroomConfig {

    public StroomPathConfig() {
        super();
    }

    @JsonCreator
    public StroomPathConfig(@JsonProperty("home") final String home,
                            @JsonProperty("temp") final String temp) {
        super(home, temp);
    }

//    private static final String DEFAULT_HOME_DIR = ".";
//    private static final String DEFAULT_TEMP_DIR = "/tmp/stroom";
//
//    public StroomPathConfig() {
//        super(DEFAULT_HOME_DIR, DEFAULT_TEMP_DIR);
//    }

    /**
     * Will be created on boot in App
     */
    @Override
    @ReadOnly
    @RequiresRestart(RestartScope.SYSTEM)
    @JsonPropertyDescription("By default, unless configured otherwise, all other configured paths " +
            "(except stroom.path.temp) will be relative to this directory. If this value is null then" +
            "Stroom will use either of the following to derive stroom.path.home: the directory of the Stroom " +
            "application JAR file or ~/.stroom. Should only be set per node in application YAML configuration file")
    public String getHome() {
        return super.getHome();
    }

    /**
     * Will be created on boot in App
     */
    @Override
    @Nonnull
    @ReadOnly
    @RequiresRestart(RestartScope.SYSTEM)
    @JsonPropertyDescription("This directory is used by stroom to write any temporary file to. " +
            "Should only be set per node in application YAML configuration file.")
    public String getTemp() {
        return super.getTemp();
    }
}
