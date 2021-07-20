package stroom.proxy.app.handler;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder(alphabetic = true)
public class ForwardStreamConfig extends AbstractConfig implements IsProxyConfig {

    private boolean isForwardingEnabled = false;
    private String userAgent;
    private List<ForwardDestinationConfig> forwardDestinations = new ArrayList<>();

    /**
     * True if received streams should be forwarded to another stroom(-proxy) instance.
     */
    @JsonProperty
    public boolean isForwardingEnabled() {
        return isForwardingEnabled;
    }

    @JsonProperty
    public void setForwardingEnabled(final boolean forwardingEnabled) {
        isForwardingEnabled = forwardingEnabled;
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
}
