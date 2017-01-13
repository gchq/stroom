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

package stroom.pipeline.shared;

import stroom.util.shared.SharedObject;

import java.util.Map;
import java.util.Set;

public class SteppingResult implements SharedObject {
    private static final long serialVersionUID = 111359625573465578L;

    private Map<String, SteppingFilterSettings> stepFilterMap;
    private StepLocation stepLocation;
    private SharedStepData stepData;
    private Integer currentStreamOffset;
    private boolean foundRecord;
    private Set<String> generalErrors;

    public SteppingResult() {
        // Default constructor necessary for GWT serialisation.
    }

    public SteppingResult(final Map<String, SteppingFilterSettings> stepFilterMap, final StepLocation stepLocation,
            final SharedStepData stepData, final Integer currentStreamOffset, final boolean foundRecord,
            final Set<String> generalErrors) {
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

    public Set<String> getGeneralErrors() {
        return generalErrors;
    }
}
