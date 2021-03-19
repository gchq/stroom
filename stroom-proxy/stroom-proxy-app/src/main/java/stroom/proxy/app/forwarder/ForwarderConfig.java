package stroom.proxy.app.forwarder;

import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder({
        "forwardingEnabled",
        "userAgent",
        "forwardDestinations"
})
public class ForwarderConfig {

    private boolean forwardingEnabled = false;
    private String userAgent;
    private List<ForwardDestinationConfig> forwardDestinations = new ArrayList<>();
    private StroomDuration retryFrequency = StroomDuration.of(Duration.ofMinutes(1));

    /**
     * True if received streams should be forwarded to another stroom(-proxy) instance.
     */
    @JsonProperty
    public boolean isForwardingEnabled() {
        return forwardingEnabled;
    }

    @JsonProperty
    public void setForwardingEnabled(final boolean forwardingEnabled) {
        this.forwardingEnabled = forwardingEnabled;
    }

    /**
     * The string to use for the User-Agent request property when forwarding data.
     */
    @JsonProperty
    public String getUserAgent() {
        return userAgent;
    }

    @JsonProperty
    public void setUserAgent(final String userAgent) {
        this.userAgent = userAgent;
    }

    /**
     * A list of destinations to forward each batch of data to
     */
    @JsonProperty
    public List<ForwardDestinationConfig> getForwardDestinations() {
        return forwardDestinations;
    }

    @JsonProperty
    public void setForwardDestinations(final List<ForwardDestinationConfig> forwardDestinations) {
        this.forwardDestinations = forwardDestinations;
    }

    @JsonPropertyDescription("How often do we want to retry forwarding data that fails to forward?")
    @JsonProperty
    public StroomDuration getRetryFrequency() {
        return retryFrequency;
    }

    public void setRetryFrequency(final StroomDuration retryFrequency) {
        this.retryFrequency = retryFrequency;
    }
}
