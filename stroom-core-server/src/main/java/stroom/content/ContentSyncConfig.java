package stroom.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.servicediscovery.ResourcePaths;

public class ContentSyncConfig {
    private String upstreamUrl;
    private long syncFrequency;
    private String apiKey;

    @JsonProperty
    public String getUpstreamUrl() {
        return upstreamUrl;
    }

    @JsonProperty
    public void setUpstreamUrl(final String upstreamUrl) {
        this.upstreamUrl = upstreamUrl;
    }

    @JsonProperty
    public long getSyncFrequency() {
        return syncFrequency;
    }

    @JsonProperty
    public void setSyncFrequency(final long syncFrequency) {
        this.syncFrequency = syncFrequency;
    }

    @JsonProperty
    public String getApiKey() {
        return apiKey;
    }

    @JsonProperty
    public void setApiKey(final String apiKey) {
        this.apiKey = apiKey;
    }
}
