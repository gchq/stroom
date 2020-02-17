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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public class StreamLocation implements Location {
    @JsonProperty
    private final long streamNo;
    @JsonProperty
    private final int lineNo;
    @JsonProperty
    private final int colNo;

    @JsonCreator
    public StreamLocation(@JsonProperty("streamNo") final long streamNo,
                          @JsonProperty("lineNo") final int lineNo,
                          @JsonProperty("colNo") final int colNo) {
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

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final StreamLocation that = (StreamLocation) o;
        return streamNo == that.streamNo &&
                lineNo == that.lineNo &&
                colNo == that.colNo;
    }

    @Override
    public int hashCode() {
        return Objects.hash(streamNo, lineNo, colNo);
    }

    @Override
    public int compareTo(final Location o) {
        if (o == this) {
            return 0;
        }
        if (!(o instanceof StreamLocation)) {
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
        return streamNo +
                ":" +
                lineNo +
                ":" +
                colNo;
    }
}
