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

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.Map;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SteppingResult {

    @JsonProperty
    private final String sessionUuid;
    @JsonProperty
    private final Map<String, SteppingFilterSettings> stepFilterMap;
    @JsonProperty
    private final StepLocation progressLocation;
    @JsonProperty
    private final StepLocation foundLocation;
    @JsonProperty
    private final SharedStepData stepData;
    @JsonProperty
    private final Integer currentStreamOffset;
    @JsonProperty
    private final boolean foundRecord;
    @JsonProperty
    private final Set<String> generalErrors;
    @JsonProperty
    private final boolean segmentedData;
    @JsonPropertyDescription("True if stepping has completed")
    @JsonProperty
    private final boolean complete;

    @JsonCreator
    public SteppingResult(@JsonProperty("sessionUuid") final String sessionUuid,
                          @JsonProperty("stepFilterMap") final Map<String, SteppingFilterSettings> stepFilterMap,
                          @JsonProperty("progressLocation") final StepLocation progressLocation,
                          @JsonProperty("foundLocation") final StepLocation foundLocation,
                          @JsonProperty("stepData") final SharedStepData stepData,
                          @JsonProperty("currentStreamOffset") final Integer currentStreamOffset,
                          @JsonProperty("foundRecord") final boolean foundRecord,
                          @JsonProperty("generalErrors") final Set<String> generalErrors,
                          @JsonProperty("segmentedData") final boolean segmentedData,
                          @JsonProperty("complete") final boolean complete) {

        // Copy the step filter map so it can be remembered across multiple
        // requests.
        this.sessionUuid = sessionUuid;
        this.stepFilterMap = stepFilterMap;
        this.progressLocation = progressLocation;
        this.foundLocation = foundLocation;
        this.stepData = stepData;
        this.currentStreamOffset = currentStreamOffset;
        this.foundRecord = foundRecord;
        this.generalErrors = generalErrors;
        this.segmentedData = segmentedData;
        this.complete = complete;
    }

    public String getSessionUuid() {
        return sessionUuid;
    }

    public StepLocation getProgressLocation() {
        return progressLocation;
    }

    public StepLocation getFoundLocation() {
        return foundLocation;
    }

    public SharedStepData getStepData() {
        return stepData;
    }

    public Integer getCurrentStreamOffset() {
        return currentStreamOffset;
    }

    public boolean isFoundRecord() {
        return foundRecord;
    }

    public Map<String, SteppingFilterSettings> getStepFilterMap() {
        return stepFilterMap;
    }

    public boolean hasActiveFilter() {
        return NullSafe.map(stepFilterMap)
                .values()
                .stream()
                .anyMatch(SteppingFilterSettings::hasActiveFilters);
    }

    public Set<String> getGeneralErrors() {
        return generalErrors;
    }

    public boolean isSegmentedData() {
        return segmentedData;
    }

    public boolean isComplete() {
        return complete;
    }
}
