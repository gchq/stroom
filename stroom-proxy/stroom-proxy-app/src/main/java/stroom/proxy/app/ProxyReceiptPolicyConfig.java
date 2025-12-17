/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.proxy.app;

import stroom.receive.rules.shared.ReceiveDataRuleSetResource;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.ResourcePaths;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

public class ProxyReceiptPolicyConfig extends AbstractConfig implements IsProxyConfig {

    public static final StroomDuration DEFAULT_SYNC_FREQUENCY = StroomDuration.ofMinutes(1);

    public static final String DEFAULT_URL_PATH = ResourcePaths.buildAuthenticatedApiPath(
            ReceiveDataRuleSetResource.BASE_RESOURCE_PATH,
            ReceiveDataRuleSetResource.FETCH_HASHED_PATH_PART);

    private final String receiveDataRulesUrl;
    private final StroomDuration syncFrequency;

    public ProxyReceiptPolicyConfig() {
        receiveDataRulesUrl = null;
        syncFrequency = DEFAULT_SYNC_FREQUENCY;
    }

    @JsonCreator
    public ProxyReceiptPolicyConfig(@JsonProperty("receiveDataRulesUrl") final String receiveDataRulesUrl,
                                    @JsonProperty("syncFrequency") final StroomDuration syncFrequency) {
        this.receiveDataRulesUrl = receiveDataRulesUrl;
        this.syncFrequency = Objects.requireNonNullElse(syncFrequency, DEFAULT_SYNC_FREQUENCY);
    }

    private ProxyReceiptPolicyConfig(final Builder builder) {
        receiveDataRulesUrl = builder.receiveDataRulesUrl;
        syncFrequency = builder.syncFrequency;
    }

    public static Builder copy(final ProxyReceiptPolicyConfig copy) {
        final Builder builder = new Builder();
        builder.receiveDataRulesUrl = copy.getReceiveDataRulesUrl();
        builder.syncFrequency = copy.getSyncFrequency();
        return builder;
    }

    public Builder copy() {
        final Builder builder = copy(new ProxyReceiptPolicyConfig());
        builder.receiveDataRulesUrl = this.getReceiveDataRulesUrl();
        builder.syncFrequency = this.getSyncFrequency();
        return builder;
    }

    @JsonProperty("receiveDataRulesUrl")
    @JsonPropertyDescription(
            "The full url for the remote stroom/proxy that will provide the receipt policy rules. " +
            "If the remote is a proxy, it must also be configured to use receipt policy rules " +
            "so that it can obtain them from another stroom/proxy downstream to it. " +
            "If not set the downstreamHost configuration will be combined with the default API " +
            "path. Only set this property if you wish to use a non-default path " +
            "or you want to use a different host/port/scheme to that defined in downstreamHost.")
    public String getReceiveDataRulesUrl() {
        return receiveDataRulesUrl;
    }

    @NotNull
    @JsonProperty
    public StroomDuration getSyncFrequency() {
        return syncFrequency;
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
        return Objects.equals(receiveDataRulesUrl, that.receiveDataRulesUrl)
               && Objects.equals(syncFrequency, that.syncFrequency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(receiveDataRulesUrl, syncFrequency);
    }

    @Override
    public String toString() {
        return "ProxyReceiptPolicyConfig{" +
               "receiveDataRulesUrl='" + receiveDataRulesUrl + '\'' +
               ", syncFrequency=" + syncFrequency +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private String receiveDataRulesUrl;
        private StroomDuration syncFrequency;

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

        public ProxyReceiptPolicyConfig build() {
            return new ProxyReceiptPolicyConfig(this);
        }
    }
}
