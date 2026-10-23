/*
 * Copyright 2026 Crown Copyright
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

package stroom.pathways.shared.pathway;

import stroom.pathways.shared.otel.trace.NanoTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * One path through the model, with how many traces took it.
 *
 * <p>What the trace did is one shape, {@code root}, holding the whole walk: every node reached, the
 * children run at each, and the order they ran in, with work that repeated said once. How many times
 * a repeat ran is not part of it, because how much work there was to do is the workload rather than
 * the path through the code. Two traces whose shapes are the same took the same path and share a
 * row.
 *
 * <p>The id of the trace that created the row is kept so the screen can open one that took the
 * path. It stays put as the count rises rather than moving to the newest, so the row points at a
 * trace someone can have looked at — at the cost of being the one most likely to have aged out of
 * the trace store.
 */
@JsonInclude(Include.NON_NULL)
public class PathUse {

    @JsonProperty
    private final int root;
    @JsonProperty
    private final long timesUsed;
    @JsonProperty
    private final NanoTime firstUsedTime;
    @JsonProperty
    private final NanoTime lastUsedTime;
    @JsonProperty
    private final String createdByTraceId;
    /**
     * When the trace that first took this path actually ran, as against {@link #firstUsedTime}, which
     * is when the model learnt from it. The two differ by however long the trace waited to be applied.
     */
    @JsonProperty
    private final NanoTime traceTime;
    /**
     * When the newest trace to take this path ran, as against {@link #lastUsedTime}, which is when the
     * model last learnt from one. How recently a path was taken is asked of this: a queue that has
     * been held up applies old traces now, and a path's age is the age of the work, not of the
     * processing.
     */
    @JsonProperty
    private final NanoTime lastTraceTime;
    /**
     * Which trace store the creating trace came from, as a position in the source list on
     * {@link Paths}, or -1 where the trace arrived without one. Recorded rather than looked up,
     * because a store can later be pointed at a different pathways document and what it already
     * taught this one stays behind.
     */
    @JsonProperty
    private final int source;

    @JsonCreator
    public PathUse(@JsonProperty("root") final Integer root,
                    @JsonProperty("timesUsed") final Long timesUsed,
                    @JsonProperty("firstUsedTime") final NanoTime firstUsedTime,
                    @JsonProperty("lastUsedTime") final NanoTime lastUsedTime,
                    @JsonProperty("createdByTraceId") final String createdByTraceId,
                   @JsonProperty("traceTime") final NanoTime traceTime,
                   @JsonProperty("lastTraceTime") final NanoTime lastTraceTime,
                   @JsonProperty("source") final Integer source) {
        this.root = Objects.requireNonNullElse(root, 0);
        this.timesUsed = Objects.requireNonNullElse(timesUsed, 0L);
        this.firstUsedTime = firstUsedTime;
        this.lastUsedTime = lastUsedTime;
        this.createdByTraceId = createdByTraceId;
        this.traceTime = traceTime;
        this.lastTraceTime = lastTraceTime;
        this.source = Objects.requireNonNullElse(source, 0);
    }

    /**
     * Where the walk this path took begins, as a position in the shape list on {@link Paths}.
     */
    public int getRoot() {
        return root;
    }

    public long getTimesUsed() {
        return timesUsed;
    }

    public NanoTime getFirstUsedTime() {
        return firstUsedTime;
    }

    public NanoTime getLastUsedTime() {
        return lastUsedTime;
    }

    public String getCreatedByTraceId() {
        return createdByTraceId;
    }

    /**
     * The same path taken again. The creating trace id and first used time keep the values they were
     * given, so the row still points at a trace that has been checked rather than the newest one.
     */
    public NanoTime getTraceTime() {
        return traceTime;
    }

    /**
     * Where the creating trace came from, as a position in the source list on {@link Paths}, or -1
     * where it arrived without one.
     */
    public int getSource() {
        return source;
    }

    /**
     * When the newest trace to take this path ran.
     */
    public NanoTime getLastTraceTime() {
        return lastTraceTime;
    }

    public PathUse used(final NanoTime time, final NanoTime ranAt) {
        return new PathUse(root, timesUsed + 1, firstUsedTime, time, createdByTraceId, traceTime,
                newest(lastTraceTime, ranAt), source);
    }

    // Traces do not arrive in the order they ran: a store that has been held up hands over old ones
    // after fresh ones, and two stores feeding one pathway keep no order between them. Taking the
    // newer of the two keeps this the newest, which is what being asked how recently a path was taken
    // needs it to be.
    private static NanoTime newest(final NanoTime held, final NanoTime arrived) {
        if (held == null) {
            return arrived;
        }
        if (arrived == null) {
            return held;
        }
        return held.isGreaterThan(arrived)
                ? held
                : arrived;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PathUse that = (PathUse) o;
        return timesUsed == that.timesUsed
               && root == that.root
               && Objects.equals(firstUsedTime, that.firstUsedTime)
               && Objects.equals(lastUsedTime, that.lastUsedTime)
               && Objects.equals(createdByTraceId, that.createdByTraceId)
               && Objects.equals(traceTime, that.traceTime)
               && Objects.equals(lastTraceTime, that.lastTraceTime)
               && source == that.source;
    }

    @Override
    public int hashCode() {
        return Objects.hash(root, timesUsed, firstUsedTime, lastUsedTime, createdByTraceId, traceTime,
                lastTraceTime, source);
    }

    @Override
    public String toString() {
        return "PathUse{shape " + root + " x" + timesUsed + '}';
    }
}
