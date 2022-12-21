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

    public static final String PROP_NAME_HOME = "home";
    public static final String PROP_NAME_TEMP = "temp";

    @JsonProperty(PROP_NAME_HOME)
    private final String home;

    @JsonProperty(PROP_NAME_TEMP)
    private final String temp;

    public PathConfig() {
        home = null;
        temp = null;
    }

    @JsonCreator
    public PathConfig(@JsonProperty(PROP_NAME_HOME) final String home,
                      @JsonProperty(PROP_NAME_TEMP) final String temp) {
        this.home = home;
        this.temp = temp;
    }

    public String getHome() {
        return home;
    }

    public String getTemp() {
        return temp;
    }

    @Override
    public String toString() {
        return "PathConfig{" +
                "home='" + home + '\'' +
                ", temp='" + temp + '\'' +
                '}';
    }
}
