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

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;
import stroom.docref.SharedObject;

public class StepLocation implements SharedObject {
    private static final long serialVersionUID = 6018818196613322322L;

    private long streamId;
    // The stream number is 1 based and not 0 based as in the stream store.
    private long streamNo;
    private long recordNo;

    public StepLocation() {
        // Default constructor necessary for GWT serialisation.
    }

    public StepLocation(final long streamId, final long streamNo, final long recordNo) {
        this.streamId = streamId;
        this.streamNo = streamNo;
        this.recordNo = recordNo;
    }

    public long getStreamId() {
        return streamId;
    }

    public long getStreamNo() {
        return streamNo;
    }

    public long getRecordNo() {
        return recordNo;
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(streamId);
        builder.append(streamNo);
        builder.append(recordNo);
        return builder.toHashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !(o instanceof StepLocation)) {
            return false;
        }

        final StepLocation stepLocation = (StepLocation) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(streamId, stepLocation.streamId);
        builder.append(streamNo, stepLocation.streamNo);
        builder.append(recordNo, stepLocation.recordNo);
        return builder.isEquals();
    }

    @Override
    public String toString() {
        return "[" + streamId + ":" + streamNo + ":" + recordNo + "]";
    }
}
