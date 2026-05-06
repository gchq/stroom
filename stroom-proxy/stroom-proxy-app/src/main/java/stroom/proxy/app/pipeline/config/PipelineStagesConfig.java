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
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.Valid;

import java.util.Objects;

/**
 * Stage definitions for independently enabled proxy pipeline stages.
 */
@JsonPropertyOrder(alphabetic = true)
public class PipelineStagesConfig extends AbstractConfig implements IsProxyConfig {

    private final PipelineStageConfig receive;
    private final PipelineStageConfig splitZip;
    private final PipelineStageConfig preAggregate;
    private final PipelineStageConfig aggregate;
    private final PipelineStageConfig forward;

    public PipelineStagesConfig() {
        this(null, null, null, null, null);
    }

    @JsonCreator
    public PipelineStagesConfig(
            @JsonProperty("receive") final PipelineStageConfig receive,
            @JsonProperty("splitZip") final PipelineStageConfig splitZip,
            @JsonProperty("preAggregate") final PipelineStageConfig preAggregate,
            @JsonProperty("aggregate") final PipelineStageConfig aggregate,
            @JsonProperty("forward") final PipelineStageConfig forward) {

        this.receive = Objects.requireNonNullElseGet(receive, PipelineStageConfig::new);
        this.splitZip = Objects.requireNonNullElseGet(splitZip, PipelineStageConfig::new);
        this.preAggregate = Objects.requireNonNullElseGet(preAggregate, PipelineStageConfig::new);
        this.aggregate = Objects.requireNonNullElseGet(aggregate, PipelineStageConfig::new);
        this.forward = Objects.requireNonNullElseGet(forward, PipelineStageConfig::new);
    }

    @Valid
    @JsonProperty
    public PipelineStageConfig getReceive() {
        return receive;
    }

    @Valid
    @JsonProperty
    public PipelineStageConfig getSplitZip() {
        return splitZip;
    }

    @Valid
    @JsonProperty
    public PipelineStageConfig getPreAggregate() {
        return preAggregate;
    }

    @Valid
    @JsonProperty
    public PipelineStageConfig getAggregate() {
        return aggregate;
    }

    @Valid
    @JsonProperty
    public PipelineStageConfig getForward() {
        return forward;
    }
}
