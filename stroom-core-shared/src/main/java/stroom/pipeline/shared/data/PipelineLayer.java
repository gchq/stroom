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

package stroom.pipeline.shared.data;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class PipelineLayer {

    @JsonProperty
    private final DocRef sourcePipeline;
    @JsonProperty
    private final PipelineData pipelineData;

    @JsonCreator
    public PipelineLayer(@JsonProperty("sourcePipeline") final DocRef sourcePipeline,
                         @JsonProperty("pipelineData") final PipelineData pipelineData) {
        this.sourcePipeline = sourcePipeline;
        this.pipelineData = pipelineData;
    }

    public DocRef getSourcePipeline() {
        return sourcePipeline;
    }

    public PipelineData getPipelineData() {
        return pipelineData;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PipelineLayer that = (PipelineLayer) o;
        return Objects.equals(sourcePipeline, that.sourcePipeline) && Objects.equals(pipelineData,
                that.pipelineData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourcePipeline, pipelineData);
    }
}
