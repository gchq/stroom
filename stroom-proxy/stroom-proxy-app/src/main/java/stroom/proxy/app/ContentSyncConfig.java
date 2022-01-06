package stroom.proxy.app;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;

import java.util.Map;

@JsonPropertyOrder(alphabetic = true)
public class ContentSyncConfig extends AbstractConfig implements IsProxyConfig {

    private final boolean isContentSyncEnabled;
    private final Map<String, String> upstreamUrl;
    private final long syncFrequency;
    private final String apiKey;

    public ContentSyncConfig() {
        isContentSyncEnabled = false;
        upstreamUrl = null;
        syncFrequency = 60_000;
        apiKey = null;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ContentSyncConfig(@JsonProperty("contentSyncEnabled") final boolean isContentSyncEnabled,
                             @JsonProperty("upstreamUrl") final Map<String, String> upstreamUrl,
                             @JsonProperty("syncFrequency") final long syncFrequency,
                             @JsonProperty("apiKey") final String apiKey) {
        this.isContentSyncEnabled = isContentSyncEnabled;
        this.upstreamUrl = upstreamUrl;
        this.syncFrequency = syncFrequency;
        this.apiKey = apiKey;
    }

    @JsonProperty("contentSyncEnabled")
    public boolean isContentSyncEnabled() {
        return isContentSyncEnabled;
    }

    @JsonProperty
    public Map<String, String> getUpstreamUrl() {
        return upstreamUrl;
    }

    @JsonProperty
    public long getSyncFrequency() {
        return syncFrequency;
    }

    @JsonProperty
    public String getApiKey() {
        return apiKey;
    }

    @SuppressWarnings("unused")
    @ValidationMethod(message = "Content sync is enabled but no upstreamUrls have been provided in 'upstreamUrl'")
    public boolean areUpstreamUrlsValid() {
        return isContentSyncEnabled && (upstreamUrl == null || upstreamUrl.isEmpty());
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
