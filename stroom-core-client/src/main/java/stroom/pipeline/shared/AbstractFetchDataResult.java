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

import java.util.List;

import stroom.streamstore.shared.StreamType;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.RowCount;
import stroom.util.shared.SharedObject;

public abstract class AbstractFetchDataResult implements SharedObject {
    private static final long serialVersionUID = 7559713171858774241L;

    private StreamType streamType;
    private String classification;
    private OffsetRange<Long> streamRange;
    private RowCount<Long> streamRowCount;
    private OffsetRange<Long> pageRange;
    private RowCount<Long> pageRowCount;
    private List<StreamType> availableChildStreamTypes;

    public AbstractFetchDataResult() {
        // Default constructor necessary for GWT serialisation.
    }

    public AbstractFetchDataResult(final StreamType streamType, final String classification,
            final OffsetRange<Long> streamRange, final RowCount<Long> streamRowCount, final OffsetRange<Long> pageRange,
            final RowCount<Long> pageRowCount, final List<StreamType> availableChildStreamTypes) {
        this.streamType = streamType;
        this.classification = classification;
        this.streamRange = streamRange;
        this.streamRowCount = streamRowCount;
        this.pageRange = pageRange;
        this.pageRowCount = pageRowCount;
        this.availableChildStreamTypes = availableChildStreamTypes;
    }

    public StreamType getStreamType() {
        return streamType;
    }

    public String getClassification() {
        return classification;
    }

    public OffsetRange<Long> getStreamRange() {
        return streamRange;
    }

    public RowCount<Long> getStreamRowCount() {
        return streamRowCount;
    }

    public OffsetRange<Long> getPageRange() {
        return pageRange;
    }

    public RowCount<Long> getPageRowCount() {
        return pageRowCount;
    }

    public List<StreamType> getAvailableChildStreamTypes() {
        return availableChildStreamTypes;
    }
}
