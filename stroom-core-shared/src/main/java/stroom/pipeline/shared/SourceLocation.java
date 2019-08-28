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

import stroom.streamstore.shared.StreamType;
import stroom.util.shared.CompareBuilder;
import stroom.util.shared.Highlight;

import java.io.Serializable;
import java.util.Objects;

public class SourceLocation implements Serializable, Comparable<SourceLocation> {
    private static final long serialVersionUID = -2327935798577052482L;

    private long streamId;
    private StreamType childStreamType;
    private long streamNo;
    private long recordNo = -1;
    private Highlight highlight;

    public SourceLocation() {
        // Default constructor necessary for GWT serialisation.
    }

    public SourceLocation(final long streamId, final StreamType childStreamType, final long streamNo, final long recordNo, final Highlight highlight) {
        this.streamId = streamId;
        this.childStreamType = childStreamType;
        this.streamNo = streamNo;
        this.recordNo = recordNo;
        this.highlight = highlight;
    }

    public long getStreamId() {
        return streamId;
    }

    public StreamType getChildStreamType() {
        return childStreamType;
    }

    public long getStreamNo() {
        return streamNo;
    }

    public long getRecordNo() {
        return recordNo;
    }

    public Highlight getHighlight() {
        return highlight;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SourceLocation that = (SourceLocation) o;
        return streamId == that.streamId &&
                streamNo == that.streamNo &&
                recordNo == that.recordNo &&
                Objects.equals(childStreamType, that.childStreamType) &&
                Objects.equals(highlight, that.highlight);
    }

    @Override
    public int hashCode() {
        return Objects.hash(streamId, childStreamType, streamNo, recordNo, highlight);
    }

    @Override
    public int compareTo(final SourceLocation o) {
        final CompareBuilder builder = new CompareBuilder();
        builder.append(streamId, o.streamId);
        builder.append(childStreamType, o.childStreamType);
        builder.append(streamNo, o.streamNo);
        builder.append(recordNo, o.recordNo);
        builder.append(highlight, o.highlight);
        return builder.toComparison();
    }

    @Override
    public String toString() {
        return "DataLocation{" +
                "streamId=" + streamId +
                ", streamId=" + childStreamType +
                ", streamNo=" + streamNo +
                ", recordNo=" + recordNo +
                ", highlight=" + highlight +
                '}';
    }
}
