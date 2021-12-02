package stroom.proxy.app.handler;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class FeedStatusConfig extends AbstractConfig implements IsProxyConfig {

    private final String feedStatusUrl;
    private final String apiKey;

    public FeedStatusConfig() {
        feedStatusUrl = null;
        apiKey = null;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public FeedStatusConfig(@JsonProperty("url") final String feedStatusUrl,
                            @JsonProperty("apiKey") final String apiKey) {
        this.feedStatusUrl = feedStatusUrl;
        this.apiKey = apiKey;
    }

    @JsonProperty("url")
    public String getFeedStatusUrl() {
        return feedStatusUrl;
    }

    @JsonProperty("apiKey")
    public String getApiKey() {
        return apiKey;
    }
}
