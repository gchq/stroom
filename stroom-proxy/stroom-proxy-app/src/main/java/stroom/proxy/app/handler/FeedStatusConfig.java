package stroom.proxy.app.handler;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class FeedStatusConfig extends AbstractConfig implements IsProxyConfig {

    private String feedStatusUrl;
    private String apiKey;

    @JsonProperty("url")
    public String getFeedStatusUrl() {
        return feedStatusUrl;
    }

    @JsonProperty("url")
    public void setFeedStatusUrl(final String feedStatusUrl) {
        this.feedStatusUrl = feedStatusUrl;
    }

    @JsonProperty("apiKey")
    public String getApiKey() {
        return apiKey;
    }

    @JsonProperty("apiKey")
    public void setApiKey(final String apiKey) {
        this.apiKey = apiKey;
    }
}
