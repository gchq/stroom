package stroom.pipeline.destination;

import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;


@JsonPropertyOrder(alphabetic = true)
public class AppenderConfig extends AbstractConfig {

    private static final int DEFAULT_MAX_ACTIVE_DESTINATIONS = 100;

    private final int maxActiveDestinations;

    public AppenderConfig() {
        maxActiveDestinations = DEFAULT_MAX_ACTIVE_DESTINATIONS;
    }

    @JsonCreator
    public AppenderConfig(@JsonProperty("maxActiveDestinations") final int maxActiveDestinations) {
        this.maxActiveDestinations = maxActiveDestinations;
    }

    @JsonPropertyDescription("The maximum number active destinations that Stroom will allow rolling appenders to be " +
            "writing to at any one time.")
    public int getMaxActiveDestinations() {
        return maxActiveDestinations;
    }

    @Override
    public String toString() {
        return "AppenderConfig{" +
                "maxActiveDestinations=" + maxActiveDestinations +
                '}';
    }
}
