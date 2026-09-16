/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.pipeline.stage.receive;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Configuration for the receive stage. Receipt runs on its callers' threads - Jetty's, the
 * scanner's, the event store's - so it has no threads of its own to configure; it writes received
 * file groups to a file store and publishes a message to the output queue, or to the split-zip
 * queue for a zip holding more than one feed.
 */
@JsonPropertyOrder(alphabetic = true)
@NotInjectableConfig
public class ReceiveStageConfig extends AbstractConfig implements IsProxyConfig {

    private final boolean enabled;
    @JsonIgnore
    private final boolean enabledSpecified;
    private final String outputQueue;
    private final String splitZipQueue;
    private final String fileStore;

    public ReceiveStageConfig() {
        this(true, null, null, null);
    }

    @JsonCreator
    public ReceiveStageConfig(
            @JsonProperty("enabled") final Boolean enabled,
            @JsonProperty("outputQueue") final String outputQueue,
            @JsonProperty("splitZipQueue") final String splitZipQueue,
            @JsonProperty("fileStore") final String fileStore) {

        // `enabled` must be stated explicitly - the validator rejects a stage without it.
        // The fallback is disabled rather than enabled so that if validation is ever
        // bypassed the failure mode is an idle process, not one silently doing work it
        // was not asked to do.
        this.enabled = Objects.requireNonNullElse(enabled, false);
        this.enabledSpecified = enabled != null;
        this.outputQueue = normaliseOptional(outputQueue);
        this.splitZipQueue = normaliseOptional(splitZipQueue);
        this.fileStore = normaliseOptional(fileStore);
    }

    @JsonProperty
    @JsonPropertyDescription("Whether the receive stage is enabled on this proxy process.")
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @return true if {@code enabled} was present in configuration. Absence is a validation
     * error rather than a default, so that a stage block never means something the operator
     * did not write.
     */
    @JsonIgnore
    public boolean isEnabledSpecified() {
        return enabledSpecified;
    }

    @JsonProperty
    @JsonPropertyDescription("Logical output queue name (e.g. aggregateInput).")
    public String getOutputQueue() {
        return outputQueue;
    }

    @JsonProperty
    @JsonPropertyDescription("Optional logical queue name for split-zip work emitted by receive.")
    public String getSplitZipQueue() {
        return splitZipQueue;
    }

    @JsonProperty
    @JsonPropertyDescription("Named file store for data written by the receive stage.")
    public String getFileStore() {
        return fileStore;
    }

    private static String normaliseOptional(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
