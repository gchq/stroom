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

import stroom.dispatch.shared.Action;
import stroom.streamstore.shared.StreamType;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.Severity;

public class FetchDataAction extends Action<AbstractFetchDataResult> {
    private static final long serialVersionUID = -1773544031158236156L;

    private Long streamId;
    private StreamType childStreamType;

    private OffsetRange<Long> streamRange;
    private OffsetRange<Long> pageRange;
    private boolean showAsHtml;
    private boolean markerMode;
    private Severity[] expandedSeverities;
    private transient boolean fireEvents;

    public FetchDataAction() {
        streamRange = new OffsetRange<Long>(0L, 1L);
        pageRange = new OffsetRange<Long>(0L, 100L);
    }

    public FetchDataAction(final Long streamId, final Long segmentId, final boolean showAsHtml) {
        this.streamId = streamId;

        streamRange = new OffsetRange<Long>(0L, 1L);
        pageRange = new OffsetRange<Long>(segmentId - 1, 1L);

        this.showAsHtml = showAsHtml;
    }

    public Long getStreamId() {
        return streamId;
    }

    public void setStreamId(final Long streamId) {
        this.streamId = streamId;
    }

    public StreamType getChildStreamType() {
        return childStreamType;
    }

    public void setChildStreamType(final StreamType childStreamType) {
        this.childStreamType = childStreamType;
    }

    public OffsetRange<Long> getStreamRange() {
        return streamRange;
    }

    public void setStreamRange(final OffsetRange<Long> streamRange) {
        this.streamRange = streamRange;
    }

    public OffsetRange<Long> getPageRange() {
        return pageRange;
    }

    public void setPageRange(final OffsetRange<Long> pageRange) {
        this.pageRange = pageRange;
    }

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

    @Override
    public String getTaskName() {
        return "SourcePresenter - fetchData()";
    }

    public void setFireEvents(final boolean fireEvents) {
        this.fireEvents = fireEvents;
    }

    public boolean isFireEvents() {
        return fireEvents;
    }
}
