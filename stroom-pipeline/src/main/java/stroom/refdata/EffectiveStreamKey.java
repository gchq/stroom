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

package stroom.refdata;

import stroom.entity.shared.DocRef;
import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;

import java.io.Serializable;

public class EffectiveStreamKey implements Serializable {
    private final DocRef feed;
    private final String streamType;
    private final long effectiveMs;

    private final int hashCode;

    public EffectiveStreamKey(final DocRef feed, final String streamType, final long effectiveMs) {
        this.feed = feed;
        this.streamType = streamType;
        this.effectiveMs = effectiveMs;

        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(feed);
        builder.append(streamType);
        builder.append(effectiveMs);
        hashCode = builder.toHashCode();
    }

    public DocRef getFeed() {
        return feed;
    }

    public String getStreamType() {
        return streamType;
    }

    public long getEffectiveMs() {
        return effectiveMs;
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
        if (o == null || !(o instanceof EffectiveStreamKey)) {
            return false;
        }

        final EffectiveStreamKey effectiveStreamKey = (EffectiveStreamKey) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(feed, effectiveStreamKey.feed);
        builder.append(streamType, effectiveStreamKey.streamType);
        builder.append(effectiveMs, effectiveStreamKey.effectiveMs);
        return builder.isEquals();
    }

    @Override
    public String toString() {
        return "feed=" + feed + ", streamType=" + streamType + ",effectiveMs=" + effectiveMs;
    }
}
