package stroom.proxy.app;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;

import java.util.Collections;
import java.util.Map;
import javax.validation.constraints.NotNull;

@JsonPropertyOrder(alphabetic = true)
public class ContentSyncConfig extends AbstractConfig implements IsProxyConfig {

    private final boolean isContentSyncEnabled;
    private final Map<String, String> upstreamUrl;
    private final StroomDuration syncFrequency;
    private final String apiKey;

    public ContentSyncConfig() {
        isContentSyncEnabled = false;
        upstreamUrl = null;
        syncFrequency = StroomDuration.ofMinutes(1);
        apiKey = null;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ContentSyncConfig(@JsonProperty("contentSyncEnabled") final boolean isContentSyncEnabled,
                             @JsonProperty("upstreamUrl") final Map<String, String> upstreamUrl,
                             @JsonProperty("syncFrequency") final StroomDuration syncFrequency,
                             @JsonProperty("apiKey") final String apiKey) {
        this.isContentSyncEnabled = isContentSyncEnabled;
        this.upstreamUrl = upstreamUrl != null
                ? Collections.unmodifiableMap(upstreamUrl)
                : null;
        this.syncFrequency = syncFrequency;
        this.apiKey = apiKey;
    }

    @JsonProperty("contentSyncEnabled")
    public boolean isContentSyncEnabled() {
        return isContentSyncEnabled;
    }

    @JsonPropertyDescription("A map of document types (e.g. 'Dictionary' or 'ReceiveDataRuleSet') to the URLs. " +
            "Content for each type will be downloaded from the associated URL.")
    @JsonProperty
    public Map<String, String> getUpstreamUrl() {
        return upstreamUrl;
    }

    @NotNull
    @JsonProperty
    public StroomDuration getSyncFrequency() {
        return syncFrequency;
    }

    @JsonProperty
    public String getApiKey() {
        return apiKey;
    }

    @JsonIgnore
    @SuppressWarnings("unused")
    @ValidationMethod(message = "Content sync is enabled but no upstreamUrls have been provided in 'upstreamUrl'")
    public boolean isUpstreamUrlPresent() {
        return !isContentSyncEnabled
                || (upstreamUrl != null && !upstreamUrl.isEmpty());
    }

    public void validateConfiguration() {
        if (!isUpstreamUrlPresent()) {
            throw new RuntimeException(
                    "Content sync is enabled but no upstreamUrls have been provided in 'upstreamUrl'");
        }
    }
}
