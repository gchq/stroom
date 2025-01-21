package stroom.config.app;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class SessionConfig extends AbstractConfig implements IsStroomConfig {

    public static final String PROP_NAME_MAX_INACTIVE_INTERVAL = "maxInactiveInterval";

    public static final StroomDuration DEFAULT_MAX_INACTIVE_INTERVAL = StroomDuration.ofDays(1);

    private final StroomDuration maxInactiveInterval;

    public SessionConfig() {
        this.maxInactiveInterval = DEFAULT_MAX_INACTIVE_INTERVAL;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public SessionConfig(
            @JsonProperty(PROP_NAME_MAX_INACTIVE_INTERVAL) final StroomDuration maxInactiveInterval) {
        this.maxInactiveInterval = maxInactiveInterval;
    }

    @RequiresRestart(RestartScope.UI)
    @JsonProperty(PROP_NAME_MAX_INACTIVE_INTERVAL)
    @JsonPropertyDescription("The maximum time interval between the last access of a HTTP session and " +
                             "it being considered expired. Set to null for sessions that never expire, " +
                             "however this will causes sessions to be held and build up in memory indefinitely, " +
                             "so is best avoided.")
    public StroomDuration getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    @Override
    public String toString() {
        return "SessionConfig{" +
               "maxInactiveInterval=" + maxInactiveInterval +
               '}';
    }
}
