package stroom.util.io;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

// Can be injected either as PathConfig or one of its sub-classes
@JsonPropertyOrder(alphabetic = true)
public abstract class PathConfig extends AbstractConfig implements IsProxyConfig, IsStroomConfig {

    @JsonProperty
    // TODO 01/12/2021 AT: make final
    private String home;

    @JsonProperty
    // TODO 01/12/2021 AT: make final
    private String temp;

    public PathConfig() {
        home = null;
        temp = null;
    }

    @JsonCreator
    public PathConfig(@JsonProperty("home") final String home,
                      @JsonProperty("temp") final String temp) {
        this.home = home;
        this.temp = temp;
    }

    public String getHome() {
        return home;
    }

    @Deprecated(forRemoval = true)
    public void setHome(final String home) {
        this.home = home;
    }

    public String getTemp() {
        return temp;
    }

    @Deprecated(forRemoval = true)
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
