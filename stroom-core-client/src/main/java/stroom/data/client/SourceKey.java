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

package stroom.data.client;

import stroom.pipeline.shared.SourceLocation;

import java.util.Objects;
import java.util.Optional;

public class SourceKey {

    private final long metaId;
    private final long partIndex;
    private final Long recordIndex;
    private final String childStreamType;

    public SourceKey(final long metaId,
                     final long partIndex,
                     final Long recordIndex,
                     final String childStreamType) {
        this.metaId = metaId;
        this.partIndex = partIndex;
        this.recordIndex = recordIndex;
        this.childStreamType = childStreamType;
    }

    public SourceKey(final SourceLocation sourceLocation) {
        this.metaId = sourceLocation.getMetaId();
        this.partIndex = sourceLocation.getPartIndex();
        this.recordIndex = sourceLocation.getRecordIndex();
        this.childStreamType = sourceLocation.getChildType();
    }

    public long getMetaId() {
        return metaId;
    }

    public Optional<String> getOptChildStreamType() {
        return Optional.ofNullable(childStreamType);
    }

    /**
     * @return The part index, zero based. 0 for single-part segmented streams.
     */
    public long getPartIndex() {
        return partIndex;
    }

    public Optional<Long> getRecordIndex() {
        return Optional.ofNullable(recordIndex);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SourceKey sourceKey = (SourceKey) o;
        return metaId == sourceKey.metaId &&
                partIndex == sourceKey.partIndex &&
                Objects.equals(recordIndex, sourceKey.recordIndex) &&
                Objects.equals(childStreamType, sourceKey.childStreamType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(metaId, partIndex, recordIndex, childStreamType);
    }

    @Override
    public String toString() {
        return metaId + ":"
                + partIndex + ":"
                + getRecordIndex().orElse(0L) + " - "
                + childStreamType;
    }
}

