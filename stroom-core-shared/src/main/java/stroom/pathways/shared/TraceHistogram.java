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

package stroom.pathways.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

/**
 * A time histogram of trace counts: {@code counts.get(i)} is the number of traces whose start time
 * falls in the {@code i}-th of {@code counts.size()} equal buckets spanning [{@code fromMs},
 * {@code toMs}). When {@code available} is {@code false} the requested window was unbounded or wider
 * than {@code maxWindowMs} and no scan was performed.
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class TraceHistogram {

    @JsonProperty
    private final boolean available;
    @JsonProperty
    private final long fromMs;
    @JsonProperty
    private final long toMs;
    @JsonProperty
    private final long bucketWidthMs;
    @JsonProperty
    private final long maxWindowMs;
    @JsonProperty
    private final List<Long> counts;

    @JsonCreator
    public TraceHistogram(@JsonProperty("available") final boolean available,
                          @JsonProperty("fromMs") final long fromMs,
                          @JsonProperty("toMs") final long toMs,
                          @JsonProperty("bucketWidthMs") final long bucketWidthMs,
                          @JsonProperty("maxWindowMs") final long maxWindowMs,
                          @JsonProperty("counts") final List<Long> counts) {
        this.available = available;
        this.fromMs = fromMs;
        this.toMs = toMs;
        this.bucketWidthMs = bucketWidthMs;
        this.maxWindowMs = maxWindowMs;
        this.counts = counts;
    }

    public static TraceHistogram unavailable(final long maxWindowMs) {
        return new TraceHistogram(false, 0L, 0L, 0L, maxWindowMs, null);
    }

    public boolean isAvailable() {
        return available;
    }

    public long getFromMs() {
        return fromMs;
    }

    public long getToMs() {
        return toMs;
    }

    public long getBucketWidthMs() {
        return bucketWidthMs;
    }

    public long getMaxWindowMs() {
        return maxWindowMs;
    }

    public List<Long> getCounts() {
        return counts;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TraceHistogram that = (TraceHistogram) o;
        return available == that.available &&
               fromMs == that.fromMs &&
               toMs == that.toMs &&
               bucketWidthMs == that.bucketWidthMs &&
               maxWindowMs == that.maxWindowMs &&
               Objects.equals(counts, that.counts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(available, fromMs, toMs, bucketWidthMs, maxWindowMs, counts);
    }

    @Override
    public String toString() {
        return "TraceHistogram{" +
               "available=" + available +
               ", fromMs=" + fromMs +
               ", toMs=" + toMs +
               ", bucketWidthMs=" + bucketWidthMs +
               ", maxWindowMs=" + maxWindowMs +
               ", counts=" + counts +
               '}';
    }
}
