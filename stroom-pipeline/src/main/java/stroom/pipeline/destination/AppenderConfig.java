package stroom.pipeline.destination;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class AppenderConfig {
    private static final int DEFAULT_MAX_ACTIVE_DESTINATIONS = 100;

    private int maxActiveDestinations = DEFAULT_MAX_ACTIVE_DESTINATIONS;

    @JsonPropertyDescription("The maximum number active destinations that Stroom will allow rolling appenders to be writing to at any one time.")
    public int getMaxActiveDestinations() {
        return maxActiveDestinations;
    }

    public void setMaxActiveDestinations(final int maxActiveDestinations) {
        this.maxActiveDestinations = maxActiveDestinations;
    }
}
