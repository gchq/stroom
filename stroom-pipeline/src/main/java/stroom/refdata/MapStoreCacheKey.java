/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.refdata;

import stroom.query.api.DocRef;
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;

/**
 * This class stores a relationship between a stream and a pipeline for loading
 * purposes;
 */
public class MapStoreCacheKey {
    private final DocRef pipeline;
    private final long streamId;

    private final int hashCode;

    public MapStoreCacheKey(final DocRef pipeline, final long streamId) {
        this.pipeline = pipeline;
        this.streamId = streamId;

        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(pipeline);
        builder.append(streamId);
        hashCode = builder.toHashCode();
    }

    public DocRef getPipeline() {
        return pipeline;
    }

    public long getStreamId() {
        return streamId;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !(o instanceof MapStoreCacheKey)) {
            return false;
        }

        final MapStoreCacheKey mapStorePoolKey = (MapStoreCacheKey) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(pipeline, mapStorePoolKey.pipeline);
        builder.append(streamId, mapStorePoolKey.streamId);
        return builder.isEquals();
    }

    @Override
    public String toString() {
        return "pipeline=" + pipeline + ", streamId=" + streamId;
    }
}
