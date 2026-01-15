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

package stroom.pipeline.refdata;

import stroom.util.date.DateUtil;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Represents a time range of approx 11.5 days. The range size and bounds are
 */
@JsonInclude(Include.NON_NULL)
class EffectiveStreamKey {

    // Actually 11.5 days but this is fine for the purposes of reference data.
    private static final long APPROX_TEN_DAYS = 1_000_000_000;

    @JsonProperty
    private final String feed;
    @JsonProperty
    private final String streamType;
    @JsonProperty
    private final long fromMs;
    @JsonProperty
    private final long toMs;
    private final int hashCode;

    EffectiveStreamKey(@JsonProperty("feed") final String feed,
                       @JsonProperty("streamType") final String streamType,
                       @JsonProperty("fromMs") final long fromMs,
                       @JsonProperty("toMs") final long toMs) {

        Objects.requireNonNull(feed);
        Objects.requireNonNull(streamType);
        this.feed = feed;
        this.streamType = streamType;
        this.fromMs = fromMs;
        this.toMs = toMs;
        hashCode = Objects.hash(feed, streamType, fromMs, toMs);
    }

    /**
     * Create an {@link EffectiveStreamKey} for a point t
     *
     * @param feed
     * @param streamType
     * @param timeMs
     * @return
     */
    public static EffectiveStreamKey forLookupTime(final String feed,
                                                   final String streamType,
                                                   final long timeMs) {
        // Create a window of approx 10 days to cache effective streams.
        // First round down the time to the nearest 10 days approx (actually more like 11.5, one billion milliseconds).
        final long fromMs = (timeMs / APPROX_TEN_DAYS) * APPROX_TEN_DAYS;
        final long toMs = fromMs + APPROX_TEN_DAYS;
        return new EffectiveStreamKey(feed, streamType, fromMs, toMs);
    }


    String getFeed() {
        return feed;
    }

    String getStreamType() {
        return streamType;
    }

    /**
     * Inclusive
     */
    long getFromMs() {
        return fromMs;
    }

    /**
     * Exclusive
     */
    long getToMs() {
        return toMs;
    }

    /**
     * Helper for testing
     */
    EffectiveStreamKey nextKey() {
        return EffectiveStreamKey.forLookupTime(feed, streamType, toMs + 1);
    }

    /**
     * Helper for testing
     */
    EffectiveStreamKey previousKey() {
        return EffectiveStreamKey.forLookupTime(feed, streamType, fromMs - 1);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
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

    @Override
    public String toString() {
        return "EffectiveStreamKey{" +
                feed +
                ":" +
                streamType +
                " >= " +
                DateUtil.createNormalDateTimeString(fromMs) +
                " < " +
                DateUtil.createNormalDateTimeString(toMs) +
                "}";
    }

    boolean isTimeInKeyWindow(final long timeMs) {
        return timeMs >= fromMs
                && timeMs < toMs;
    }
}
