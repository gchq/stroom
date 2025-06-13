package stroom.proxy.app;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;
import jakarta.validation.constraints.NotNull;

@JsonPropertyOrder(alphabetic = true)
public class ContentSyncConfig extends AbstractConfig implements IsProxyConfig {

    private final boolean isContentSyncEnabled;
    private final String receiveDataRulesUrl;
    private final StroomDuration syncFrequency;
    private final String apiKey;

    public ContentSyncConfig() {
        isContentSyncEnabled = false;
        receiveDataRulesUrl = null;
        syncFrequency = StroomDuration.ofMinutes(1);
        apiKey = null;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ContentSyncConfig(@JsonProperty("contentSyncEnabled") final boolean isContentSyncEnabled,
                             @JsonProperty("receiveDataRulesUrl") final String receiveDataRulesUrl,
                             @JsonProperty("syncFrequency") final StroomDuration syncFrequency,
                             @JsonProperty("apiKey") final String apiKey) {
        this.isContentSyncEnabled = isContentSyncEnabled;
        this.receiveDataRulesUrl = receiveDataRulesUrl;
        this.syncFrequency = syncFrequency;
        this.apiKey = apiKey;
    }

    @JsonProperty("contentSyncEnabled")
    public boolean isContentSyncEnabled() {
        return isContentSyncEnabled;
    }

    @JsonProperty("receiveDataRulesUrl")
    public String getReceiveDataRulesUrl() {
        return receiveDataRulesUrl;
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
               || NullSafe.isNonBlankString(receiveDataRulesUrl);
    }

    public void validateConfiguration() {
        if (!isUpstreamUrlPresent()) {
            throw new RuntimeException(
                    "Content sync is enabled but no upstreamUrls have been provided in 'upstreamUrl'");
        }
    }
}
