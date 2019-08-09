package stroom.proxy.app.handler;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class ForwardStreamConfig {
    private boolean isForwardingEnabled = false;
    private String userAgent;
    private List<ForwardDestinationConfig> forwardDestinations = new ArrayList<>();

    /**
     * True if received streams should be forwarded to another stroom(-proxy) instance.
     */
    @JsonProperty
    boolean isForwardingEnabled() {
        return isForwardingEnabled;
    }

    @JsonProperty
    void setForwardingEnabled(final boolean forwardingEnabled) {
        isForwardingEnabled = forwardingEnabled;
    }

    /**
     * The string to use for the User-Agent request property when forwarding data.
     */
    @JsonProperty
    String getUserAgent() {
        return userAgent;
    }

    @JsonProperty
    void setUserAgent(final String userAgent) {
        this.userAgent = userAgent;
    }

    /**
     * A list of destinations to forward each batch of data to
     */
    @JsonProperty
    List<ForwardDestinationConfig> getForwardDestinations() {
        return forwardDestinations;
    }

    @JsonProperty
    void setForwardDestinations(final List<ForwardDestinationConfig> forwardDestinations) {
        this.forwardDestinations = forwardDestinations;
    }
}
