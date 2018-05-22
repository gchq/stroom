/*
 * Copyright 2017 Crown Copyright
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

import stroom.docref.DocRef;
import stroom.util.date.DateUtil;

import java.util.Objects;

class EffectiveStreamKey {
    private final DocRef feed;
    private final String streamType;
    private final long fromMs;
    private final long toMs;
    private final int hashCode;

    EffectiveStreamKey(final DocRef feed, final String streamType, final long fromMs, final long toMs) {
        this.feed = feed;
        this.streamType = streamType;
        this.fromMs = fromMs;
        this.toMs = toMs;
        hashCode = Objects.hash(feed, streamType, fromMs, toMs);
    }

    DocRef getFeed() {
        return feed;
    }

    String getStreamType() {
        return streamType;
    }

    long getFromMs() {
        return fromMs;
    }

    long getToMs() {
        return toMs;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final EffectiveStreamKey that = (EffectiveStreamKey) o;
        return fromMs == that.fromMs &&
                toMs == that.toMs &&
                Objects.equals(feed, that.feed) &&
                Objects.equals(streamType, that.streamType);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public void append(final StringBuilder sb) {
        if (feed != null) {
            sb.append("feed = ");
            sb.append(feed.getName());
            sb.append(", ");
        }
        sb.append("streamType = ");
        sb.append(streamType);
        sb.append(", effectiveTimeWindow >= ");
        sb.append(DateUtil.createNormalDateTimeString(fromMs));
        sb.append(" < ");
        sb.append(DateUtil.createNormalDateTimeString(toMs));
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        append(sb);
        return sb.toString();
    }
}
