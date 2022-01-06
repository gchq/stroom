package stroom.proxy.app.forwarder;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@JsonPropertyOrder(alphabetic = true)
public class ForwarderConfig extends AbstractConfig implements IsProxyConfig {

    private final boolean forwardingEnabled;
    private final String userAgent;
    private final List<ForwardDestinationConfig> forwardDestinations;
    private final StroomDuration retryFrequency;

    public ForwarderConfig() {
        forwardingEnabled = false;
        userAgent = null;
        forwardDestinations = new ArrayList<>();
        retryFrequency = StroomDuration.of(Duration.ofMinutes(1));
    }

    @JsonCreator
    public ForwarderConfig(
            @JsonProperty("forwardingEnabled") final boolean forwardingEnabled,
            @JsonProperty("userAgent") final String userAgent,
            @JsonProperty("forwardDestinations") final List<ForwardDestinationConfig> forwardDestinations,
            @JsonProperty("retryFrequency") final StroomDuration retryFrequency) {

        this.forwardingEnabled = forwardingEnabled;
        this.userAgent = userAgent;
        this.forwardDestinations = List.copyOf(forwardDestinations);
        this.retryFrequency = retryFrequency;
    }

    /**
     * True if received streams should be forwarded to another stroom(-proxy) instance.
     */
    public boolean isForwardingEnabled() {
        return forwardingEnabled;
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

    @JsonPropertyDescription("How often do we want to retry forwarding data that fails to forward?")
    @JsonProperty
    public StroomDuration getRetryFrequency() {
        return retryFrequency;
    }

    public ForwarderConfig withForwardingEnabled(final boolean forwardingEnabled) {
        return new ForwarderConfig(forwardingEnabled, userAgent, forwardDestinations, retryFrequency);
    }

    public ForwarderConfig withForwardDestinations(final List<ForwardDestinationConfig> forwardDestinations) {
        return new ForwarderConfig(forwardingEnabled, userAgent, forwardDestinations, retryFrequency);
    }

    public ForwarderConfig withForwardDestinations(final ForwardDestinationConfig... forwardDestinations) {
        return new ForwarderConfig(forwardingEnabled, userAgent, List.of(forwardDestinations), retryFrequency);
    }

}
