/*
 * Copyright 2016 Crown Copyright
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Set;

public class SteppingResult {
    @JsonProperty
    private Map<String, SteppingFilterSettings> stepFilterMap;
    @JsonProperty
    private StepLocation stepLocation;
    @JsonProperty
    private SharedStepData stepData;
    @JsonProperty
    private Integer currentStreamOffset;
    @JsonProperty
    private boolean foundRecord;
    @JsonProperty
    private Set<String> generalErrors;

    public SteppingResult() {
    }

    @JsonCreator
    public SteppingResult(@JsonProperty("stepFilterMap") final Map<String, SteppingFilterSettings> stepFilterMap,
                          @JsonProperty("stepLocation") final StepLocation stepLocation,
                          @JsonProperty("stepData") final SharedStepData stepData,
                          @JsonProperty("currentStreamOffset") final Integer currentStreamOffset,
                          @JsonProperty("foundRecord") final boolean foundRecord,
                          @JsonProperty("generalErrors") final Set<String> generalErrors) {
        // Copy the step filter map so it can be remembered across multiple
        // requests.
        this.stepFilterMap = stepFilterMap;
        this.stepLocation = stepLocation;
        this.stepData = stepData;
        this.currentStreamOffset = currentStreamOffset;
        this.foundRecord = foundRecord;
        this.generalErrors = generalErrors;
    }

    public StepLocation getStepLocation() {
        return stepLocation;
    }

    public void setStepLocation(final StepLocation stepLocation) {
        this.stepLocation = stepLocation;
    }

    public SharedStepData getStepData() {
        return stepData;
    }

    public void setStepData(final SharedStepData stepData) {
        this.stepData = stepData;
    }

    public Integer getCurrentStreamOffset() {
        return currentStreamOffset;
    }

    public void setCurrentStreamOffset(final Integer currentStreamOffset) {
        this.currentStreamOffset = currentStreamOffset;
    }

    public boolean isFoundRecord() {
        return foundRecord;
    }

    public void setFoundRecord(final boolean foundRecord) {
        this.foundRecord = foundRecord;
    }

    public Map<String, SteppingFilterSettings> getStepFilterMap() {
        return stepFilterMap;
    }

    public void setStepFilterMap(final Map<String, SteppingFilterSettings> stepFilterMap) {
        this.stepFilterMap = stepFilterMap;
    }

    public Set<String> getGeneralErrors() {
        return generalErrors;
    }

    public void setGeneralErrors(final Set<String> generalErrors) {
        this.generalErrors = generalErrors;
    }
}
