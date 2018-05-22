/*
 * Copyright 2017 Crown Copyright
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

package stroom.pipeline.stepping;

import stroom.pipeline.shared.StepLocation;
import stroom.pipeline.shared.StepType;
import stroom.pipeline.shared.SteppingFilterSettings;
import stroom.pipeline.shared.SteppingResult;
import stroom.docref.DocRef;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.StreamType;
import stroom.task.ServerTask;

import java.util.Map;
import java.util.Map.Entry;

public class SteppingTask extends ServerTask<SteppingResult> {
    // This is what chooses the input to the translation.
    private FindStreamCriteria criteria;
    private StreamType childStreamType;
    private StepLocation stepLocation;
    private StepType stepType;
    private int stepSize = 1;

    private Map<String, SteppingFilterSettings> stepFilterMap;

    private DocRef pipeline;
    private Map<String, String> code;

    public SteppingTask(final String userToken) {
        super(null, userToken);
    }

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

    public StepType getStepType() {
        return stepType;
    }

    public void setStepType(final StepType stepType) {
        this.stepType = stepType;
    }

    public SteppingFilterSettings getStepFilterSettings(final String elementId) {
        if (stepFilterMap == null) {
            return null;
        }
        final SteppingFilterSettings steppingFilterIO = stepFilterMap.get(elementId);
        if (steppingFilterIO == null) {
            return null;
        }

        return steppingFilterIO;
    }

    public Map<String, SteppingFilterSettings> getStepFilterMap() {
        return stepFilterMap;
    }

    public void setStepFilterMap(final Map<String, SteppingFilterSettings> stepFilterMap) {
        this.stepFilterMap = stepFilterMap;
    }

    public int getStepSize() {
        return stepSize;
    }

    public void setStepSize(final int stepSize) {
        this.stepSize = stepSize;
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
        return "Translation stepping";
    }
}
