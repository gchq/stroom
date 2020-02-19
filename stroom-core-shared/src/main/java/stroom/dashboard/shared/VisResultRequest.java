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

package stroom.dashboard.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.OffsetRange;

@JsonInclude(Include.NON_DEFAULT)
public class VisResultRequest extends ComponentResultRequest {
    @JsonProperty
    private VisComponentSettings visDashboardSettings;
    @JsonProperty
    private OffsetRange<Integer> requestedRange;

    public VisResultRequest(final int offset, final int length) {
        requestedRange = new OffsetRange<>(offset, length);
    }

    @JsonCreator
    public VisResultRequest(@JsonProperty("visDashboardSettings") final VisComponentSettings visDashboardSettings,
                            @JsonProperty("requestedRange") final OffsetRange<Integer> requestedRange) {
        this.visDashboardSettings = visDashboardSettings;
        this.requestedRange = requestedRange;
    }

    public VisComponentSettings getVisDashboardSettings() {
        return visDashboardSettings;
    }

    public void setVisDashboardSettings(final VisComponentSettings visDashboardSettings) {
        this.visDashboardSettings = visDashboardSettings;
    }

    public OffsetRange<Integer> getRequestedRange() {
        return requestedRange;
    }

    public void setRequestedRange(final OffsetRange<Integer> requestedRange) {
        this.requestedRange = requestedRange;
    }

    public void setRange(final int offset, final int length) {
        requestedRange = new OffsetRange<>(offset, length);
    }

    @JsonIgnore
    @Override
    public ComponentType getComponentType() {
        return ComponentType.VIS;
    }
}
