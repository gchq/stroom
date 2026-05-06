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

package stroom.proxy.app.pipeline.stage.forward;

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
 * Configuration for the forward pipeline stage.
 * <p>
 * The forward stage is the terminal stage — it consumes aggregated file
 * groups from its input queue and forwards them to downstream destinations.
 * It has no output queue and no file store (it reads via
 * {@code FileStoreRegistry} and does not write).
 * </p>
 */
@JsonPropertyOrder(alphabetic = true)
@NotInjectableConfig
public class ForwardStageConfig extends AbstractConfig implements IsProxyConfig {

    private final boolean enabled;
    private final String inputQueue;
    private final ConsumerStageThreadsConfig threads;

    public ForwardStageConfig() {
        this(false, null, new ConsumerStageThreadsConfig());
    }

    @JsonCreator
    public ForwardStageConfig(
            @JsonProperty("enabled") final Boolean enabled,
            @JsonProperty("inputQueue") final String inputQueue,
            @JsonProperty("threads") final ConsumerStageThreadsConfig threads) {

        this.enabled = Objects.requireNonNullElse(enabled, false);
        this.inputQueue = normaliseOptional(inputQueue);
        this.threads = Objects.requireNonNullElseGet(threads, ConsumerStageThreadsConfig::new);
    }

    @JsonProperty
    @JsonPropertyDescription("Whether the forward stage is enabled on this proxy process.")
    public boolean isEnabled() {
        return enabled;
    }

    @JsonProperty
    @JsonPropertyDescription("Logical input queue name (e.g. forwardingInput).")
    public String getInputQueue() {
        return inputQueue;
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
