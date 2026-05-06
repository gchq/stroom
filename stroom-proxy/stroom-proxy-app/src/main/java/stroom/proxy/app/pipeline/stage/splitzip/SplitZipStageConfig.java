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

package stroom.proxy.app.pipeline.stage.splitzip;

import stroom.proxy.app.pipeline.config.ConsumerStageThreadsConfig;
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
 * Configuration for the split-zip pipeline stage.
 * <p>
 * The split-zip stage consumes multi-feed zip file groups from its input
 * queue, splits them into per-feed file groups, writes the results to a
 * file store, and publishes onward messages to the output queue.
 * </p>
 */
@JsonPropertyOrder(alphabetic = true)
@NotInjectableConfig
public class SplitZipStageConfig extends AbstractConfig implements IsProxyConfig {

    private final boolean enabled;
    private final String inputQueue;
    private final String outputQueue;
    private final String fileStore;
    private final ConsumerStageThreadsConfig threads;

    public SplitZipStageConfig() {
        this(false, null, null, null, new ConsumerStageThreadsConfig());
    }

    @JsonCreator
    public SplitZipStageConfig(
            @JsonProperty("enabled") final Boolean enabled,
            @JsonProperty("inputQueue") final String inputQueue,
            @JsonProperty("outputQueue") final String outputQueue,
            @JsonProperty("fileStore") final String fileStore,
            @JsonProperty("threads") final ConsumerStageThreadsConfig threads) {

        this.enabled = Objects.requireNonNullElse(enabled, false);
        this.inputQueue = normaliseOptional(inputQueue);
        this.outputQueue = normaliseOptional(outputQueue);
        this.fileStore = normaliseOptional(fileStore);
        this.threads = Objects.requireNonNullElseGet(threads, ConsumerStageThreadsConfig::new);
    }

    @JsonProperty
    @JsonPropertyDescription("Whether the split-zip stage is enabled on this proxy process.")
    public boolean isEnabled() {
        return enabled;
    }

    @JsonProperty
    @JsonPropertyDescription("Logical input queue name (e.g. splitZipInput).")
    public String getInputQueue() {
        return inputQueue;
    }

    @JsonProperty
    @JsonPropertyDescription("Logical output queue name (e.g. preAggregateInput).")
    public String getOutputQueue() {
        return outputQueue;
    }

    @JsonProperty
    @JsonPropertyDescription("Named file store for data written by the split-zip stage.")
    public String getFileStore() {
        return fileStore;
    }

    @Valid
    @JsonProperty
    public ConsumerStageThreadsConfig getThreads() {
        return threads;
    }

    private static String normaliseOptional(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
