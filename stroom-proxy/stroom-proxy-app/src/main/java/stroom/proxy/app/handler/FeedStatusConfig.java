package stroom.proxy.app.handler;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder({
        "feedStatusUrl",
        "apiKey"
})
public class FeedStatusConfig {

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
