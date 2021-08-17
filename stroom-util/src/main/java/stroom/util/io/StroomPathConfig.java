package stroom.util.io;

import stroom.util.config.annotations.ReadOnly;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.annotation.Nonnull;
import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder(alphabetic = true)
public class StroomPathConfig extends PathConfig {

    @Override
    @ReadOnly
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
    @JsonPropertyDescription("This directory is used by stroom to write any temporary file to. " +
            "Should only be set per node in application YAML configuration file.")
    public String getTemp() {
        return super.getTemp();
    }
}
