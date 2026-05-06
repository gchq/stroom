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

package stroom.proxy.app.pipeline.config;

import stroom.proxy.app.pipeline.stage.aggregate.AggregateStageConfig;
import stroom.proxy.app.pipeline.stage.forward.ForwardStageConfig;
import stroom.proxy.app.pipeline.stage.preaggregate.PreAggregateStageConfig;
import stroom.proxy.app.pipeline.stage.receive.ReceiveStageConfig;
import stroom.proxy.app.pipeline.stage.splitzip.SplitZipStageConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.Valid;

import java.util.Objects;

/**
 * Stage definitions for independently enabled proxy pipeline stages.
 * <p>
 * Each stage has its own typed configuration class containing only the
 * fields relevant to that stage.
 * </p>
 */
@JsonPropertyOrder(alphabetic = true)
public class PipelineStagesConfig extends AbstractConfig implements IsProxyConfig {

    private final ReceiveStageConfig receive;
    private final SplitZipStageConfig splitZip;
    private final PreAggregateStageConfig preAggregate;
    private final AggregateStageConfig aggregate;
    private final ForwardStageConfig forward;

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

        this.receive = Objects.requireNonNullElseGet(receive, ReceiveStageConfig::new);
        this.splitZip = Objects.requireNonNullElseGet(splitZip, SplitZipStageConfig::new);
        this.preAggregate = Objects.requireNonNullElseGet(preAggregate, PreAggregateStageConfig::new);
        this.aggregate = Objects.requireNonNullElseGet(aggregate, AggregateStageConfig::new);
        this.forward = Objects.requireNonNullElseGet(forward, ForwardStageConfig::new);
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
