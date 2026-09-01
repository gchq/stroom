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

package stroom.proxy.app.pipeline.config;

import stroom.proxy.app.pipeline.runtime.PipelineStageName;
import stroom.proxy.app.pipeline.stage.aggregate.AggregateStageConfig;
import stroom.proxy.app.pipeline.stage.forward.ForwardStageConfig;
import stroom.proxy.app.pipeline.stage.preaggregate.PreAggregateStageConfig;
import stroom.proxy.app.pipeline.stage.receive.ReceiveStageConfig;
import stroom.proxy.app.pipeline.stage.splitzip.SplitZipStageConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.Valid;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Stage definitions for independently enabled proxy pipeline stages.
 * <p>
 * Each stage has its own typed configuration class containing only the
 * fields relevant to that stage.
 * </p>
 * <h2>Omitted stages</h2>
 * <p>
 * Omitting a stage from an explicit {@code stages} block is <em>ambiguous</em>.
 * A single-process proxy naming one stage to tune a thread count means "leave
 * the rest alone"; a single-purpose node in a distributed deployment naming one
 * stage means "run only this one". Guessing either way is wrong half the time,
 * and wrong silently - the proxy starts and quietly does too little or too much.
 * </p>
 * <p>
 * So this class does not guess. Omitted stages are recorded as unconfigured, and
 * {@code ProxyPipelineConfigValidator} raises an error naming each one, which
 * halts startup. A {@code stages} block must list all five stages; the standard
 * full pipeline is still what you get by omitting the block entirely.
 * </p>
 * <p>
 * The getters fall back to a <em>disabled</em> stage rather than a wired one, so
 * that if validation is ever bypassed the failure mode is an idle process rather
 * than one silently doing work it was not meant to do.
 * </p>
 */
@JsonPropertyOrder(alphabetic = true)
public class PipelineStagesConfig extends AbstractConfig implements IsProxyConfig {

    private final ReceiveStageConfig receive;
    private final SplitZipStageConfig splitZip;
    private final PreAggregateStageConfig preAggregate;
    private final AggregateStageConfig aggregate;
    private final ForwardStageConfig forward;

    /**
     * The stages that were explicitly present in configuration, as opposed to
     * defaulted. Used by validation to reject an incomplete {@code stages} block.
     */
    @JsonIgnore
    private final Set<PipelineStageName> configuredStages;

    public PipelineStagesConfig() {
        this(null, null, null, null, null);
    }

    @JsonCreator
    public PipelineStagesConfig(
            @JsonProperty("receive") final ReceiveStageConfig receive,
            @JsonProperty("splitZip") final SplitZipStageConfig splitZip,
            @JsonProperty("preAggregate") final PreAggregateStageConfig preAggregate,
            @JsonProperty("aggregate") final AggregateStageConfig aggregate,
            @JsonProperty("forward") final ForwardStageConfig forward) {

        final EnumSet<PipelineStageName> configured = EnumSet.noneOf(PipelineStageName.class);
        if (receive != null) {
            configured.add(PipelineStageName.RECEIVE);
        }
        if (splitZip != null) {
            configured.add(PipelineStageName.SPLIT_ZIP);
        }
        if (preAggregate != null) {
            configured.add(PipelineStageName.PRE_AGGREGATE);
        }
        if (aggregate != null) {
            configured.add(PipelineStageName.AGGREGATE);
        }
        if (forward != null) {
            configured.add(PipelineStageName.FORWARD);
        }
        this.configuredStages = Collections.unmodifiableSet(configured);

        // Omitted stages fall back to disabled - see the class javadoc. Validation
        // rejects them before this fallback can take effect in a running proxy.
        this.receive = Objects.requireNonNullElseGet(
                receive, () -> new ReceiveStageConfig(false, null, null, null, null));
        this.splitZip = Objects.requireNonNullElseGet(
                splitZip, () -> new SplitZipStageConfig(false, null, null, null, null));
        this.preAggregate = Objects.requireNonNullElseGet(
                preAggregate, () -> new PreAggregateStageConfig(false, null, null, null, null));
        this.aggregate = Objects.requireNonNullElseGet(
                aggregate, () -> new AggregateStageConfig(false, null, null, null, null));
        this.forward = Objects.requireNonNullElseGet(
                forward, () -> new ForwardStageConfig(false, null, null));
    }

    /**
     * @return An instance with no stage configured, so every stage reads as disabled. Used where a
     * {@code stages} block is absent, so that nothing dereferences null before validation reports it.
     */
    public static PipelineStagesConfig unconfigured() {
        return new PipelineStagesConfig(null, null, null, null, null);
    }

    /**
     * @return The stages that were explicitly present in configuration. Anything
     * absent from this set was defaulted, and validation will reject it.
     */
    @JsonIgnore
    public Set<PipelineStageName> getConfiguredStages() {
        return configuredStages;
    }

    @Valid
    @JsonProperty
    public ReceiveStageConfig getReceive() {
        return receive;
    }

    @Valid
    @JsonProperty
    public SplitZipStageConfig getSplitZip() {
        return splitZip;
    }

    @Valid
    @JsonProperty
    public PreAggregateStageConfig getPreAggregate() {
        return preAggregate;
    }

    @Valid
    @JsonProperty
    public AggregateStageConfig getAggregate() {
        return aggregate;
    }

    @Valid
    @JsonProperty
    public ForwardStageConfig getForward() {
        return forward;
    }
}
