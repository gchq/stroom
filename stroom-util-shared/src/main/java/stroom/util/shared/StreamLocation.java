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

package stroom.util.shared;

public class StreamLocation implements Location {
    private static final long serialVersionUID = 6492056020779177117L;

    private long streamNo;
    private int lineNo;
    private int colNo;

    public StreamLocation() {
        // Default constructor necessary for GWT serialisation.
    }

    public StreamLocation(final StreamLocation location) {
        this.streamNo = location.streamNo;
        this.lineNo = location.lineNo;
        this.colNo = location.colNo;
    }

    public StreamLocation(final long streamNo, final int lineNo, final int colNo) {
        this.streamNo = streamNo;
        this.lineNo = lineNo;
        this.colNo = colNo;
    }

    public long getStreamNo() {
        return streamNo;
    }

    @Override
    public int getLineNo() {
        return lineNo;
    }

    @Override
    public int getColNo() {
        return colNo;
    }

    public void incStreamNo() {
        lineNo++;
    }

    public void incLineNo() {
        lineNo++;
    }

    public void incColNo() {
        colNo++;
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(streamNo);
        builder.append(lineNo);
        builder.append(colNo);
        return builder.toHashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (o == null || !(o instanceof StreamLocation)) {
            return false;
        }

        final StreamLocation location = (StreamLocation) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(streamNo, location.streamNo);
        builder.append(lineNo, location.lineNo);
        builder.append(colNo, location.colNo);
        return builder.isEquals();
    }

    @Override
    public int compareTo(final Location o) {
        if (o == this) {
            return 0;
        }
        if (o == null || !(o instanceof StreamLocation)) {
            return -1;
        }

        final StreamLocation location = (StreamLocation) o;
        final CompareBuilder builder = new CompareBuilder();
        builder.append(streamNo, location.streamNo);
        builder.append(lineNo, location.lineNo);
        builder.append(colNo, location.colNo);
        return builder.toComparison();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(streamNo);
        sb.append(":");
        sb.append(lineNo);
        sb.append(":");
        sb.append(colNo);
        return sb.toString();
    }
}
