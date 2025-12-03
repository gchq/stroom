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
