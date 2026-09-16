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

package stroom.proxy.app.pipeline.stage.forward;

import stroom.proxy.app.pipeline.config.ConsumerStageThreadsConfig;
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
 * Configuration for the forward pipeline stage.
 * <p>
 * The forward stage is the terminal stage: it consumes aggregated file groups from its input
 * queue and delivers them to the enabled destinations. With one destination the stage's loop is
 * that destination's and runs with the destination's own thread count; with several, the loop
 * fans out into a {@code forward-<destination>} store and queue per destination, stated in the
 * pipeline block, and {@code threads} is the fan-out loop's count
 * ({@code designs/stages/forward.md} §4.4, §4.6).
 * </p>
 */
@JsonPropertyOrder(alphabetic = true)
@NotInjectableConfig
public class ForwardStageConfig extends AbstractConfig implements IsProxyConfig {

    private final boolean enabled;
    @JsonIgnore
    private final boolean enabledSpecified;
    private final String inputQueue;
    private final ConsumerStageThreadsConfig threads;

    public ForwardStageConfig() {
        this(true, null, new ConsumerStageThreadsConfig());
    }

    @JsonCreator
    public ForwardStageConfig(
            @JsonProperty("enabled") final Boolean enabled,
            @JsonProperty("inputQueue") final String inputQueue,
            @JsonProperty("threads") final ConsumerStageThreadsConfig threads) {

        // `enabled` must be stated explicitly - the validator rejects a stage without it.
        // The fallback is disabled rather than enabled so that if validation is ever
        // bypassed the failure mode is an idle process, not one silently doing work it
        // was not asked to do.
        this.enabled = Objects.requireNonNullElse(enabled, false);
        this.enabledSpecified = enabled != null;
        this.inputQueue = normaliseOptional(inputQueue);
        this.threads = Objects.requireNonNullElseGet(threads, ConsumerStageThreadsConfig::new);
    }

    @JsonProperty
    @JsonPropertyDescription("Whether the forward stage is enabled on this proxy process.")
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
    @JsonPropertyDescription("Logical input queue name (e.g. forwardingInput).")
    public String getInputQueue() {
        return inputQueue;
    }

    @JsonProperty
    @JsonPropertyDescription("Threads for the fan-out loop, used only when more than one forward destination " +
                             "is enabled. With one destination the stage's loop runs with that destination's " +
                             "own threads.consumerThreads.")
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
