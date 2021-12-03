package stroom.proxy.app.handler;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;

@JsonPropertyOrder(alphabetic = true)
public class ForwardStreamConfig extends AbstractConfig implements IsProxyConfig {

    private final boolean isForwardingEnabled;
    private final String userAgent;
    private final List<ForwardDestinationConfig> forwardDestinations;

    public ForwardStreamConfig() {
        isForwardingEnabled = false;
        userAgent = null;
        forwardDestinations = new ArrayList<>();
    }

    @JsonCreator
    public ForwardStreamConfig(
            @JsonProperty("forwardingEnabled") final boolean isForwardingEnabled,
            @JsonProperty("userAgent") final String userAgent,
            @JsonProperty("forwardDestinations") final List<ForwardDestinationConfig> forwardDestinations) {

        this.isForwardingEnabled = isForwardingEnabled;
        this.userAgent = userAgent;
        this.forwardDestinations = List.copyOf(forwardDestinations);
    }

    /**
     * True if received streams should be forwarded to another stroom(-proxy) instance.
     */
    @JsonProperty("forwardingEnabled")
    public boolean isForwardingEnabled() {
        return isForwardingEnabled;
    }

    /**
     * The string to use for the User-Agent request property when forwarding data.
     */
    @JsonProperty
    public String getUserAgent() {
        return userAgent;
    }

    /**
     * A list of destinations to forward each batch of data to
     */
    @JsonProperty
    public List<ForwardDestinationConfig> getForwardDestinations() {
        return forwardDestinations;
    }

    public ForwardStreamConfig withForwardingEnabled(final boolean isForwardingEnabled) {
        return new ForwardStreamConfig(isForwardingEnabled, userAgent, forwardDestinations);
    }

    public ForwardStreamConfig withForwardDestinations(final List<ForwardDestinationConfig> forwardDestinations) {
        return new ForwardStreamConfig(isForwardingEnabled, userAgent, forwardDestinations);
    }
}
