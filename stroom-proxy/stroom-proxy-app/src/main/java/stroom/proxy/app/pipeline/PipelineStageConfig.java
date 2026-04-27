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

package stroom.proxy.app.pipeline;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.Valid;

import java.util.Objects;

/**
 * Common stage configuration for the reference-message pipeline.
 */
@JsonPropertyOrder(alphabetic = true)
public class PipelineStageConfig extends AbstractConfig implements IsProxyConfig {

    private final boolean enabled;
    private final String inputQueue;
    private final String outputQueue;
    private final String splitZipQueue;
    private final String fileStore;
    private final PipelineStageThreadsConfig threads;

    public PipelineStageConfig() {
        this(false, null, null, null, null, new PipelineStageThreadsConfig());
    }

    @JsonCreator
    public PipelineStageConfig(
            @JsonProperty("enabled") final Boolean enabled,
            @JsonProperty("inputQueue") final String inputQueue,
            @JsonProperty("outputQueue") final String outputQueue,
            @JsonProperty("splitZipQueue") final String splitZipQueue,
            @JsonProperty("fileStore") final String fileStore,
            @JsonProperty("threads") final PipelineStageThreadsConfig threads) {

        this.enabled = Objects.requireNonNullElse(enabled, false);
        this.inputQueue = normaliseOptional(inputQueue);
        this.outputQueue = normaliseOptional(outputQueue);
        this.splitZipQueue = normaliseOptional(splitZipQueue);
        this.fileStore = normaliseOptional(fileStore);
        this.threads = Objects.requireNonNullElseGet(threads, PipelineStageThreadsConfig::new);
    }

    @JsonProperty
    @JsonPropertyDescription("Whether this stage is enabled on this proxy process.")
    public boolean isEnabled() {
        return enabled;
    }

    @JsonProperty
    @JsonPropertyDescription("Logical input queue name for queue-consuming stages.")
    public String getInputQueue() {
        return inputQueue;
    }

    @JsonProperty
    @JsonPropertyDescription("Logical output queue name for stages that publish to another stage.")
    public String getOutputQueue() {
        return outputQueue;
    }

    @JsonProperty
    @JsonPropertyDescription("Logical queue name for split zip work emitted by receive.")
    public String getSplitZipQueue() {
        return splitZipQueue;
    }

    @JsonProperty
    @JsonPropertyDescription("Named file store used for data written by this stage.")
    public String getFileStore() {
        return fileStore;
    }

    @Valid
    @JsonProperty
    public PipelineStageThreadsConfig getThreads() {
        return threads;
    }

    private static String normaliseOptional(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
