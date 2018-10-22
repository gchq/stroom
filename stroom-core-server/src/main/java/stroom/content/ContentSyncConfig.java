package stroom.content;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class ContentSyncConfig {

    private boolean isContentSyncEnabled = false;
    private Map<String, String> upstreamUrl;
    private long syncFrequency;
    private String apiKey;

    @JsonProperty
    boolean isContentSyncEnabled() {
        return isContentSyncEnabled;
    }

    void setContentSyncEnabled(final boolean contentSyncEnabled) {
        isContentSyncEnabled = contentSyncEnabled;
    }

    @JsonProperty
    public Map<String, String> getUpstreamUrl() {
        return upstreamUrl;
    }

    @JsonProperty
    public void setUpstreamUrl(final Map<String, String> upstreamUrl) {
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

    public void validateConfiguration() {
        if (isContentSyncEnabled) {
            if (upstreamUrl.isEmpty()) {
                throw new RuntimeException(
                        "Content sync is enabled but no upstreamUrls have been provided in 'upstreamUrl'");
            }
        }
    }
}
