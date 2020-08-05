package stroom.util.io;

import stroom.util.config.annotations.ReadOnly;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class PathConfig extends AbstractConfig {
    private String temp = "/tmp";//System.getProperty("java.io.tmpdir");

    @ReadOnly
    @JsonPropertyDescription("Temp folder to write stuff to. Should only be set per node in application property file")
    public String getTemp() {
        return temp;
    }

    public void setTemp(final String temp) {
        this.temp = temp;
    }

    @Override
    public String toString() {
        return "PathConfig{" +
                "temp='" + temp + '\'' +
                '}';
    }
}
