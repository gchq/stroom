package stroom.proxy.app;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

public class ProxyReceiptPolicyConfig extends AbstractConfig implements IsProxyConfig {

    public static final StroomDuration DEFAULT_SYNC_FREQUENCY = StroomDuration.ofMinutes(1);

    private final String receiveDataRulesUrl;
    private final StroomDuration syncFrequency;
    private final String apiKey;

    public ProxyReceiptPolicyConfig() {
        receiveDataRulesUrl = null;
        syncFrequency = DEFAULT_SYNC_FREQUENCY;
        apiKey = null;
    }

    public ProxyReceiptPolicyConfig(final String receiveDataRulesUrl,
                                    final StroomDuration syncFrequency,
                                    final String apiKey) {
        this.receiveDataRulesUrl = receiveDataRulesUrl;
        this.syncFrequency = Objects.requireNonNullElse(syncFrequency, DEFAULT_SYNC_FREQUENCY);
        this.apiKey = apiKey;
    }

    private ProxyReceiptPolicyConfig(final Builder builder) {
        receiveDataRulesUrl = builder.receiveDataRulesUrl;
        syncFrequency = builder.syncFrequency;
        apiKey = builder.apiKey;
    }

    public static Builder copy(final ProxyReceiptPolicyConfig copy) {
        final Builder builder = new Builder();
        builder.receiveDataRulesUrl = copy.getReceiveDataRulesUrl();
        builder.syncFrequency = copy.getSyncFrequency();
        builder.apiKey = copy.getApiKey();
        return builder;
    }

    public Builder copy() {
        final Builder builder = copy(new ProxyReceiptPolicyConfig());
        builder.receiveDataRulesUrl = this.getReceiveDataRulesUrl();
        builder.syncFrequency = this.getSyncFrequency();
        builder.apiKey = this.getApiKey();
        return builder;
    }

    @JsonProperty("receiveDataRulesUrl")
    @JsonPropertyDescription("The base url for the remote stroom/proxy that will provide the rules." +
                             "If the remote is a proxy, it must also be configured to use receipt policy rules " +
                             "so that it can obtain them from another stroom/proxy downstream to it.")
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

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ProxyReceiptPolicyConfig that = (ProxyReceiptPolicyConfig) o;
        return Objects.equals(receiveDataRulesUrl, that.receiveDataRulesUrl) && Objects.equals(
                syncFrequency,
                that.syncFrequency) && Objects.equals(apiKey, that.apiKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(receiveDataRulesUrl, syncFrequency, apiKey);
    }

    @Override
    public String toString() {
        return "ProxyReceiptPolicyConfig{" +
               "receiveDataRulesUrl='" + receiveDataRulesUrl + '\'' +
               ", syncFrequency=" + syncFrequency +
               ", apiKey='" + apiKey + '\'' +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private String receiveDataRulesUrl;
        private StroomDuration syncFrequency;
        private String apiKey;

        private Builder() {
        }

        public Builder withReceiveDataRulesUrl(final String receiveDataRulesUrl) {
            this.receiveDataRulesUrl = receiveDataRulesUrl;
            return this;
        }

        public Builder withSyncFrequency(final StroomDuration syncFrequency) {
            this.syncFrequency = syncFrequency;
            return this;
        }

        public Builder withApiKey(final String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public ProxyReceiptPolicyConfig build() {
            return new ProxyReceiptPolicyConfig(this);
        }
    }
}
