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

import stroom.util.date.DateUtil;

import java.util.Objects;

class EffectiveStream implements Comparable<EffectiveStream> {
    private final long streamId;
    private final long effectiveMs;
    private final int hashCode;

    EffectiveStream(final long streamId, final long effectiveMs) {
        this.streamId = streamId;
        this.effectiveMs = effectiveMs;
        this.hashCode = Objects.hash(streamId, effectiveMs);
    }

    public long getStreamId() {
        return streamId;
    }

    public long getEffectiveMs() {
        return effectiveMs;
    }

    @Override
    public int compareTo(final EffectiveStream o) {
        return Long.compare(effectiveMs, o.effectiveMs);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final EffectiveStream that = (EffectiveStream) o;
        return streamId == that.streamId &&
                effectiveMs == that.effectiveMs;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public void append(final StringBuilder sb) {
        sb.append("streamId=");
        sb.append(streamId);
        sb.append(", effectiveTime=");
        sb.append(DateUtil.createNormalDateTimeString(effectiveMs));
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        append(sb);
        return sb.toString();
    }
}
