/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.pipeline.shared.stepping;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.docref.DocRef;
import stroom.meta.shared.FindMetaCriteria;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class PipelineStepRequest {
    /**
     * This is what chooses the input to the translation.
     */
    @JsonProperty
    private FindMetaCriteria criteria;
    @JsonProperty
    private String childStreamType;
    @JsonProperty
    private StepLocation stepLocation;
    @JsonProperty
    private StepType stepType;
    @JsonProperty
    private Map<String, SteppingFilterSettings> stepFilterMap;
    @JsonProperty
    private DocRef pipeline;
    @JsonProperty
    private Map<String, String> code;

    public PipelineStepRequest() {
    }

    @JsonCreator
    public PipelineStepRequest(@JsonProperty("criteria") final FindMetaCriteria criteria,
                               @JsonProperty("childStreamType") final String childStreamType,
                               @JsonProperty("stepLocation") final StepLocation stepLocation,
                               @JsonProperty("stepType") final StepType stepType,
                               @JsonProperty("stepFilterMap") final Map<String, SteppingFilterSettings> stepFilterMap,
                               @JsonProperty("pipeline") final DocRef pipeline,
                               @JsonProperty("code") final Map<String, String> code) {
        this.criteria = criteria;
        this.childStreamType = childStreamType;
        this.stepLocation = stepLocation;
        this.stepType = stepType;
        this.stepFilterMap = stepFilterMap;
        this.pipeline = pipeline;
        this.code = code;
    }

    public FindMetaCriteria getCriteria() {
        return criteria;
    }

    public void setCriteria(final FindMetaCriteria criteria) {
        this.criteria = criteria;
    }

    public String getChildStreamType() {
        return childStreamType;
    }

    public void setChildStreamType(final String childStreamType) {
        this.childStreamType = childStreamType;
    }

    public DocRef getPipeline() {
        return pipeline;
    }

    public void setPipeline(final DocRef pipeline) {
        this.pipeline = pipeline;
    }

    public Map<String, String> getCode() {
        return code;
    }

    public void setCode(final Map<String, String> code) {
        this.code = code;
    }

    public StepLocation getStepLocation() {
        return stepLocation;
    }

    public void setStepLocation(final StepLocation stepLocation) {
        this.stepLocation = stepLocation;
    }

    public StepType getStepType() {
        return stepType;
    }

    public void setStepType(final StepType stepType) {
        this.stepType = stepType;
    }

    public SteppingFilterSettings getStepFilter(final String elementId) {
        if (stepFilterMap == null) {
            stepFilterMap = new HashMap<>();
        }
        return stepFilterMap.computeIfAbsent(elementId, k -> new SteppingFilterSettings());
    }

    public Map<String, SteppingFilterSettings> getStepFilterMap() {
        return stepFilterMap;
    }

    public void setStepFilterMap(final Map<String, SteppingFilterSettings> stepFilterMap) {
        this.stepFilterMap = stepFilterMap;
    }

    public void reset() {
        stepLocation = null;
        stepType = null;

        // Remove remembered values from the step filters.
        if (stepFilterMap != null) {
            for (final Entry<String, SteppingFilterSettings> entry : stepFilterMap.entrySet()) {
                final SteppingFilterSettings settings = entry.getValue();
                settings.clearUniqueValues();
            }
        }
    }
}
