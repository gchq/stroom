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

package stroom.pipeline.shared;

import stroom.docref.DocRef;
import stroom.util.shared.Severity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FetchDataRequest {

    @JsonProperty
    private SourceLocation sourceLocation;
    @JsonProperty
    private long recordCount = 1;
    @JsonProperty
    private DocRef pipeline;
    @JsonProperty
    private boolean showAsHtml;
    @JsonProperty
    private Severity[] expandedSeverities;
    @JsonProperty
    private DisplayMode displayMode = DisplayMode.TEXT;


    // Segmented (one rec could still be too large for display in the UI)
    // rec no. offset => rec count
    // rec no. offset => rec no. offset
    // All of the above limited by a max char count to display on screen, ideally with a way
    // to decided which truncated portion so show.
    // Ideally display one rec only, not a page of them

    // Non-segmented (i.e. raw, a rec could be a tiny slice of one massive line,
    // one line out of many or a set of lines out of many)
    // line/col => char count
    // line/col => line/col
    // char offset => char offset
    // char offset => char count
    // All of the above limited by a max char count to display on screen, ideally with a way
    // to decided which truncated portion so show.

    // recordOffsetFrom
    // recordOffsetTo
    // recordCount

    // locationFrom
    // locationTo
    // charOffsetFrom
    // charOffsetTo
    // charCount

    @JsonIgnore
    private transient boolean fireEvents;

    public FetchDataRequest(final SourceLocation sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    @JsonCreator
    public FetchDataRequest(@JsonProperty("sourceLocation") final SourceLocation sourceLocation,
                            @JsonProperty("recordCount") final long recordCount,
                            @JsonProperty("pipeline") final DocRef pipeline,
                            @JsonProperty("showAsHtml") final boolean showAsHtml,
                            @JsonProperty("expandedSeverities") final Severity[] expandedSeverities,
                            @JsonProperty("displayMode") final DisplayMode displayMode) {
        this.sourceLocation = sourceLocation;
        this.recordCount = recordCount;
        this.pipeline = pipeline;
        this.showAsHtml = showAsHtml;
        this.expandedSeverities = expandedSeverities;
        this.displayMode = displayMode;
    }

    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }

    public void setSourceLocation(final SourceLocation sourceLocation) {
        this.sourceLocation = sourceLocation;
    }

    public long getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(final long recordCount) {
        this.recordCount = recordCount;
    }

    public boolean isShowAsHtml() {
        return showAsHtml;
    }

    public void setShowAsHtml(final boolean showAsHtml) {
        this.showAsHtml = showAsHtml;
    }

    public Severity[] getExpandedSeverities() {
        return expandedSeverities;
    }

    public void setExpandedSeverities(final Severity[] expandedSeverities) {
        this.expandedSeverities = expandedSeverities;
    }

    public DocRef getPipeline() {
        return pipeline;
    }

    public void setPipeline(final DocRef pipeline) {
        this.pipeline = pipeline;
    }

    public DisplayMode getDisplayMode() {
        return displayMode;
    }

    public void setDisplayMode(final DisplayMode displayMode) {
        this.displayMode = displayMode;
    }

    @JsonIgnore
    public boolean isFireEvents() {
        return fireEvents;
    }

    @JsonIgnore
    public void setFireEvents(final boolean fireEvents) {
        this.fireEvents = fireEvents;
    }

    public enum DisplayMode {
        TEXT,
        HEX,
        MARKER;
    }

    @Override
    public String toString() {
        return "FetchDataRequest{" +
                "sourceLocation=" + sourceLocation +
                ", recordCount=" + recordCount +
                ", pipeline=" + pipeline +
                ", showAsHtml=" + showAsHtml +
                ", expandedSeverities=" + Arrays.toString(expandedSeverities) +
                ", displayMode=" + displayMode +
                ", fireEvents=" + fireEvents +
                '}';
    }
}
