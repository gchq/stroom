package stroom.util.io;

import stroom.util.config.annotations.ReadOnly;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class PathConfig extends AbstractConfig {
    private String home;// = "~/stroom"; //System.getProperty("user.home") + File.separator + ".stroom";
    private String temp = "/tmp";//System.getProperty("java.io.tmpdir");

    @ReadOnly
    @JsonPropertyDescription("Home folder to write stuff to. Should only be set per node in application property file")
    public String getHome() {
        return home;
    }

    public void setHome(final String home) {
        this.home = home;
    }

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
                "home='" + home + '\'' +
                ", temp='" + temp + '\'' +
                '}';
    }
}
