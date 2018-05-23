/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.offheapstore;

import stroom.query.api.v2.DocRef;

import java.util.Objects;

public class RefStreamDefinition {

    private final DocRef pipelineDocRef;
    private final byte pipelineVersion;
    private final long streamId;

    public RefStreamDefinition(final DocRef pipelineDocRef, final byte pipelineVersion, final long streamId) {
        this.pipelineDocRef = pipelineDocRef;
        this.pipelineVersion = pipelineVersion;
        this.streamId = streamId;

        //TODO should we also have streamNo in here to differentiate between nested streams?
    }

    DocRef getPipelineDocRef() {
        return pipelineDocRef;
    }

    byte getPipelineVersion() {
        return pipelineVersion;
    }

    long getStreamId() {
        return streamId;
    }

    @Override
    public String toString() {
        return "RefStreamDefinition{" +
                "pipelineDocRef=" + pipelineDocRef +
                ", pipelineVersion=" + pipelineVersion +
                ", streamId=" + streamId +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final RefStreamDefinition that = (RefStreamDefinition) o;
        return pipelineVersion == that.pipelineVersion &&
                streamId == that.streamId &&
                Objects.equals(pipelineDocRef, that.pipelineDocRef);
    }

    @Override
    public int hashCode() {

        return Objects.hash(pipelineDocRef, pipelineVersion, streamId);
    }

}
