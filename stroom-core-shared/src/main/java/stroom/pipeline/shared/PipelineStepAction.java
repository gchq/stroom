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

package stroom.pipeline.shared;

import stroom.entity.shared.Action;
import stroom.query.api.v1.DocRef;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.StreamType;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class PipelineStepAction extends Action<SteppingResult> {
    private static final long serialVersionUID = 111359625573465578L;

    /**
     * This is what chooses the input to the translation.
     */
    private FindStreamCriteria criteria;
    private StreamType childStreamType;
    private StepLocation stepLocation;
    private StepType stepType;

    private Map<String, SteppingFilterSettings> stepFilterMap;

    private DocRef pipeline;
    private Map<String, String> code;

    public FindStreamCriteria getCriteria() {
        return criteria;
    }

    public void setCriteria(final FindStreamCriteria criteria) {
        this.criteria = criteria;
    }

    public StreamType getChildStreamType() {
        return childStreamType;
    }

    public void setChildStreamType(final StreamType childStreamType) {
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

    /**
     * @return The step type.
     */
    public StepType getStepType() {
        return stepType;
    }

    /**
     * @param stepType the stepType to set.
     */
    public void setStepType(final StepType stepType) {
        this.stepType = stepType;
    }

    public SteppingFilterSettings getStepFilter(final String elementId) {
        if (stepFilterMap == null) {
            stepFilterMap = new HashMap<>();
        }
        SteppingFilterSettings settings = stepFilterMap.get(elementId);
        if (settings == null) {
            settings = new SteppingFilterSettings();
            stepFilterMap.put(elementId, settings);
        }
        return settings;
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

    @Override
    public String getTaskName() {
        return "Pipeline stepping";
    }
}
