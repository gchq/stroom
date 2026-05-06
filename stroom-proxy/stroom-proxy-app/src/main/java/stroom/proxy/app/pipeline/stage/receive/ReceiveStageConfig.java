/*
 * Copyright 2016-2026 Crown Copyright
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
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.Valid;

import java.util.Objects;

/**
 * Configuration for the receive pipeline stage.
 * <p>
 * The receive stage is HTTP-driven (no input queue). It writes received
 * file groups to a file store and publishes a reference message to the
 * output queue. Optionally, multi-feed zips can be routed to a separate
 * split-zip queue.
 * </p>
 */
@JsonPropertyOrder(alphabetic = true)
@NotInjectableConfig
public class ReceiveStageConfig extends AbstractConfig implements IsProxyConfig {

    private final boolean enabled;
    private final String outputQueue;
    private final String splitZipQueue;
    private final String fileStore;
    private final ReceiveStageThreadsConfig threads;

    public ReceiveStageConfig() {
        this(false, null, null, null, new ReceiveStageThreadsConfig());
    }

    @JsonCreator
    public ReceiveStageConfig(
            @JsonProperty("enabled") final Boolean enabled,
            @JsonProperty("outputQueue") final String outputQueue,
            @JsonProperty("splitZipQueue") final String splitZipQueue,
            @JsonProperty("fileStore") final String fileStore,
            @JsonProperty("threads") final ReceiveStageThreadsConfig threads) {

        this.enabled = Objects.requireNonNullElse(enabled, false);
        this.outputQueue = normaliseOptional(outputQueue);
        this.splitZipQueue = normaliseOptional(splitZipQueue);
        this.fileStore = normaliseOptional(fileStore);
        this.threads = Objects.requireNonNullElseGet(threads, ReceiveStageThreadsConfig::new);
    }

    @JsonProperty
    @JsonPropertyDescription("Whether the receive stage is enabled on this proxy process.")
    public boolean isEnabled() {
        return enabled;
    }

    @JsonProperty
    @JsonPropertyDescription("Logical output queue name (e.g. preAggregateInput).")
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

    @Valid
    @JsonProperty
    public ReceiveStageThreadsConfig getThreads() {
        return threads;
    }

    private static String normaliseOptional(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
