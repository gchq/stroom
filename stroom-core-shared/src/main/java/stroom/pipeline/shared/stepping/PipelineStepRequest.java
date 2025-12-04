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

package stroom.pipeline.shared.stepping;

import stroom.docref.DocRef;
import stroom.meta.shared.FindMetaCriteria;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PipelineStepRequest {

    /**
     * This is what chooses the input to the translation.
     */
    @JsonProperty
    private final String sessionUuid;
    @JsonProperty
    private final FindMetaCriteria criteria;
    @JsonProperty
    private final String childStreamType;
    @JsonProperty
    private final StepLocation stepLocation;
    @JsonProperty
    private final StepType stepType;
    @JsonProperty
    private final Map<String, SteppingFilterSettings> stepFilterMap;
    @JsonProperty
    private final DocRef pipeline;
    @JsonProperty
    private final Map<String, String> code;
    @JsonProperty
    private final int stepSize;
    @JsonPropertyDescription("Set the maximum time (in ms) for the server to wait for a complete result")
    @JsonProperty
    private final Long timeout;

    @JsonCreator
    public PipelineStepRequest(@JsonProperty("sessionUuid") final String sessionUuid,
                               @JsonProperty("criteria") final FindMetaCriteria criteria,
                               @JsonProperty("childStreamType") final String childStreamType,
                               @JsonProperty("stepLocation") final StepLocation stepLocation,
                               @JsonProperty("stepType") final StepType stepType,
                               @JsonProperty("stepFilterMap") final Map<String, SteppingFilterSettings> stepFilterMap,
                               @JsonProperty("pipeline") final DocRef pipeline,
                               @JsonProperty("code") final Map<String, String> code,
                               @JsonProperty("stepSize") final int stepSize,
                               @JsonProperty("timeout") final Long timeout) {
        this.sessionUuid = sessionUuid;
        this.criteria = criteria;
        this.childStreamType = childStreamType;
        this.stepLocation = stepLocation;
        this.stepType = stepType;
        this.stepFilterMap = stepFilterMap;
        this.pipeline = pipeline;
        this.code = code;
        this.stepSize = stepSize;
        this.timeout = timeout;
    }

    public String getSessionUuid() {
        return sessionUuid;
    }

    public FindMetaCriteria getCriteria() {
        return criteria;
    }

    public String getChildStreamType() {
        return childStreamType;
    }

    public DocRef getPipeline() {
        return pipeline;
    }

    public Map<String, String> getCode() {
        return code;
    }

    public StepLocation getStepLocation() {
        return stepLocation;
    }

    public StepType getStepType() {
        return stepType;
    }

    public Map<String, SteppingFilterSettings> getStepFilterMap() {
        return stepFilterMap;
    }

    public SteppingFilterSettings getStepFilterSettings(final String elementId) {
        if (stepFilterMap == null) {
            return null;
        }
        return stepFilterMap.get(elementId);
    }

    public int getStepSize() {
        return stepSize;
    }

    public Long getTimeout() {
        return timeout;
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String sessionUuid;
        private FindMetaCriteria criteria;
        private String childStreamType;
        private StepLocation stepLocation;
        private StepType stepType;
        private Map<String, SteppingFilterSettings> stepFilterMap;
        private DocRef pipeline;
        private Map<String, String> code;
        private int stepSize = 1;
        private Long timeout = 1000L;

        private Builder() {
        }

        private Builder(final PipelineStepRequest request) {
            this.sessionUuid = request.sessionUuid;
            this.criteria = request.criteria;
            this.childStreamType = request.childStreamType;
            this.stepLocation = request.stepLocation;
            this.stepType = request.stepType;
            this.stepFilterMap = request.stepFilterMap;
            this.pipeline = request.pipeline;
            this.code = request.code;
            this.stepSize = request.stepSize;
            this.timeout = request.timeout;
        }

        public Builder sessionUuid(final String sessionUuid) {
            this.sessionUuid = sessionUuid;
            return this;
        }

        public Builder criteria(final FindMetaCriteria criteria) {
            this.criteria = criteria;
            return this;
        }

        public Builder childStreamType(final String childStreamType) {
            this.childStreamType = childStreamType;
            return this;
        }

        public Builder stepLocation(final StepLocation stepLocation) {
            this.stepLocation = stepLocation;
            return this;
        }

        public Builder stepType(final StepType stepType) {
            this.stepType = stepType;
            return this;
        }

        public Builder stepFilterMap(final Map<String, SteppingFilterSettings> stepFilterMap) {
            this.stepFilterMap = stepFilterMap;
            return this;
        }

        public Builder pipeline(final DocRef pipeline) {
            this.pipeline = pipeline;
            return this;
        }

        public Builder code(final Map<String, String> code) {
            this.code = code;
            return this;
        }

        public Builder stepSize(final int stepSize) {
            this.stepSize = stepSize;
            return this;
        }

        public Builder timeout(final Long timeout) {
            this.timeout = timeout;
            return this;
        }

        public PipelineStepRequest build() {
            return new PipelineStepRequest(
                    sessionUuid,
                    criteria,
                    childStreamType,
                    stepLocation,
                    stepType,
                    stepFilterMap,
                    pipeline,
                    code,
                    stepSize,
                    timeout);
        }
    }
}
