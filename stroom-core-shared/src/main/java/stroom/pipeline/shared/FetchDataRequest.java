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

import stroom.data.shared.DataRange;
import stroom.data.shared.DataRange.Builder;
import stroom.docref.DocRef;
import stroom.util.shared.Severity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.function.Consumer;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class FetchDataRequest {

    @JsonProperty
    private DataRange dataRange;
//    @JsonProperty
//    private Long streamId;
//    @JsonProperty
//    private String childStreamType;
    @JsonProperty
    private DocRef pipeline;
//    @JsonProperty
//    private OffsetRange<Long> streamRange;
//    @JsonProperty
//    private OffsetRange<Long> pageRange; // this may be line offset + no. of lines or rec offset + no. of recs
//    @JsonProperty
//    private Location locationFrom;
//    @JsonProperty
//    private Location locationTo;
    @JsonProperty
    private boolean showAsHtml;
    @JsonProperty
    private boolean markerMode;
    @JsonProperty
    private Severity[] expandedSeverities;

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

//    public FetchDataRequest() {
//        streamRange = new OffsetRange<>(0L, 1L);
//        pageRange = new OffsetRange<>(0L, 100L);
//    }


    public FetchDataRequest(final DataRange dataRange) {
        this.dataRange = dataRange;
    }

    public FetchDataRequest(final long metaId, final Consumer<Builder> dataRangeBuilder) {
        final Builder builder = DataRange.builder(metaId);
        dataRangeBuilder.accept(builder);
        this.dataRange = builder.build();
    }

    @JsonCreator
    public FetchDataRequest( @JsonProperty("dataRange") final DataRange dataRange,
//            @JsonProperty("streamId") final Long streamId,
//                            @JsonProperty("childStreamType") final String childStreamType,
                             @JsonProperty("pipeline") final DocRef pipeline,
//                            @JsonProperty("streamRange") final OffsetRange<Long> streamRange,
//                            @JsonProperty("pageRange") final OffsetRange<Long> pageRange,
//                            @JsonProperty("locationFrom") final Location locationFrom,
//                            @JsonProperty("locationTo") final Location locationTo,
                             @JsonProperty("showAsHtml") final boolean showAsHtml,
                             @JsonProperty("markerMode") final boolean markerMode,
                             @JsonProperty("expandedSeverities") final Severity[] expandedSeverities) {
        this.dataRange = dataRange;
//        this.streamId = streamId;
//        this.childStreamType = childStreamType;
        this.pipeline = pipeline;
//        this.streamRange = streamRange;
//        this.pageRange = pageRange;
//        this.locationFrom = locationFrom;
//        this.locationTo = locationTo;
        this.showAsHtml = showAsHtml;
        this.markerMode = markerMode;
        this.expandedSeverities = expandedSeverities;
    }

    public DataRange getDataRange() {
        return dataRange;
    }

    public void setDataRange(final DataRange dataRange) {
        this.dataRange = dataRange;
    }

    //    public Long getStreamId() {
//        return streamId;
//    }
//
//    public void setStreamId(final Long streamId) {
//        this.streamId = streamId;
//    }

//    public String getChildStreamType() {
//        return childStreamType;
//    }
//
//    public void setChildStreamType(final String childStreamType) {
//        this.childStreamType = childStreamType;
//    }

//    public OffsetRange<Long> getStreamRange() {
//        return streamRange;
//    }
//
//    public void setStreamRange(final OffsetRange<Long> streamRange) {
//        this.streamRange = streamRange;
//    }
//
//    public OffsetRange<Long> getPageRange() {
//        return pageRange;
//    }
//
//    public void setPageRange(final OffsetRange<Long> pageRange) {
//        this.pageRange = pageRange;
//    }
//
//    public Location getLocationFrom() {
//        return locationFrom;
//    }
//
//    public void setLocationFrom(final Location locationFrom) {
//        this.locationFrom = locationFrom;
//    }
//
//    public Location getLocationTo() {
//        return locationTo;
//    }
//
//    public void setLocationTo(final Location locationTo) {
//        this.locationTo = locationTo;
//    }

    public boolean isShowAsHtml() {
        return showAsHtml;
    }

    public void setShowAsHtml(final boolean showAsHtml) {
        this.showAsHtml = showAsHtml;
    }

    public boolean isMarkerMode() {
        return markerMode;
    }

    public void setMarkerMode(final boolean markerMode) {
        this.markerMode = markerMode;
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

    @JsonIgnore
    public boolean isFireEvents() {
        return fireEvents;
    }

    @JsonIgnore
    public void setFireEvents(final boolean fireEvents) {
        this.fireEvents = fireEvents;
    }
}
