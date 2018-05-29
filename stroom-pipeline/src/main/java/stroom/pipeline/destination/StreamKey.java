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

package stroom.pipeline.destination;

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;

public class StreamKey {
    private final String feed;
    private final String streamType;
    private final boolean segmentOutput;

    public StreamKey(final String feed, final String streamType, final boolean segmentOutput) {
        this.feed = feed;
        this.streamType = streamType;
        this.segmentOutput = segmentOutput;
    }

    public String getFeed() {
        return feed;
    }

    public String getStreamType() {
        return streamType;
    }

    public boolean isSegmentOutput() {
        return segmentOutput;
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(feed);
        builder.append(streamType);
        builder.append(segmentOutput);
        return builder.toHashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof StreamKey)) {
            return false;
        }

        final StreamKey streamKey = (StreamKey) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(feed, streamKey.feed);
        builder.append(streamType, streamKey.streamType);
        builder.append(segmentOutput, streamKey.segmentOutput);
        return builder.isEquals();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("StreamKey(feed=");
        sb.append(feed);
        sb.append(", streamType=");
        sb.append(streamType);
        sb.append(", segmentOutput=");
        sb.append(segmentOutput);
        sb.append(")");
        return sb.toString();
    }
}
