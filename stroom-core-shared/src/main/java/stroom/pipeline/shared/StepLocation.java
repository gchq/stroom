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
import stroom.util.shared.SharedObject;

public class StepLocation implements SharedObject {
    private static final long serialVersionUID = 6018818196613322322L;

    private long id;
    // The stream number is 1 based and not 0 based as in the stream store.
    private long partNo;
    private long recordNo;

    public StepLocation() {
        // Default constructor necessary for GWT serialisation.
    }

    public StepLocation(final long id, final long partNo, final long recordNo) {
        this.id = id;
        this.partNo = partNo;
        this.recordNo = recordNo;
    }

    public long getId() {
        return id;
    }

    public long getPartNo() {
        return partNo;
    }

    public long getRecordNo() {
        return recordNo;
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(id);
        builder.append(partNo);
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
        builder.append(id, stepLocation.id);
        builder.append(partNo, stepLocation.partNo);
        builder.append(recordNo, stepLocation.recordNo);
        return builder.isEquals();
    }

    public String getEventId() {
        return id + ":" + partNo + ":" + recordNo;
    }

    @Override
    public String toString() {
        return "[" + getEventId() + "]";
    }
}
