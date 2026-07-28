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

package stroom.pathways.shared.otel.trace;

import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@JsonInclude(Include.NON_NULL)
public class TraceRoot {

    @JsonProperty
    private final String traceId;
    @JsonProperty
    private final String name;
    @JsonProperty
    private final NanoTime startTime;
    @JsonProperty
    private final NanoTime endTime;
    @JsonProperty
    private final int services;
    @JsonProperty
    private final int depth;
    @JsonProperty
    private final int totalSpans;
    /**
     * Wall-clock epoch millis of the most recent merge cycle that touched this trace.
     * Drives the retention/archival "is this trace still active?" decision so a
     * long-running trace's root is retained (kept live and current) rather than aged
     * out by its (fixed) start time. Zero when unknown.
     */
    @JsonProperty
    private final long lastActivityMs;
    /**
     * The root span's <em>own</em> end time (its {@code endTimeUnixNano}), as opposed to
     * {@link #endTime} which is the maximum end time across <em>all</em> spans in the trace.
     * When a background/pool thread captures the trace's OTel context and later emits spans
     * long after the root finished, {@link #endTime} is dragged far into the future while this
     * stays fixed; the gap between the two reveals such "trailing leaked activity". Nullable
     * when unknown.
     */
    @JsonProperty
    private final NanoTime rootEndTime;
    /**
     * True when this row represents an "orphan-only" trace: a traceId with spans but no root span
     * (the root aged out under retention/archival, or never arrived). Such a row is synthesized
     * from per-trace stats rather than from a root span; the UI flags it and its detail view
     * renders a rootless span forest.
     */
    @JsonProperty
    private final boolean orphan;
    /**
     * True when any span in the trace reported an error status
     * ({@link StatusCode#STATUS_CODE_ERROR}).
     */
    @JsonProperty
    private final boolean error;

    public TraceRoot(final Trace trace) {
        final Span root = trace.root();
        this.traceId = trace.getTraceId();
        this.name = root == null ? "" : root.getName();
        this.startTime = root == null ? null : root.start();
        this.endTime = root == null ? null : root.end();
        this.services = services(trace);
        this.depth = root == null ? 0 : depth(trace);
        this.totalSpans = totalSpans(trace);
        this.lastActivityMs = 0L;
        this.rootEndTime = root == null ? null : root.end();
        this.orphan = root == null;
        this.error = hasError(trace);
    }

    private static boolean hasError(final Trace trace) {
        return trace.getParentSpanIdMap()
                .values()
                .stream()
                .flatMap(List::stream)
                .anyMatch(span -> {
                    final SpanStatus status = span.getStatus();
                    return status != null && StatusCode.STATUS_CODE_ERROR.equals(status.getCode());
                });
    }

    private static int services(final Trace trace) {
        final Set<String> set = trace.getParentSpanIdMap()
                .values()
                .stream()
                .flatMap(List::stream)
                .map(Span::getName)
                .collect(Collectors.toSet());
        return set.size();
    }

    private static int depth(final Trace trace) {
        final Span root = trace.root();
        int depth = 1;
        depth = Math.max(depth, depth(trace, root) + 1);
        return depth;
    }

    private static int depth(final Trace trace, final Span span) {
        int depth = 0;
        final List<Span> children = trace.getParentSpanIdMap().get(span.getSpanId());
        if (children == null || children.isEmpty()) {
            return 0;
        }
        depth = 1;
        for (final Span child : children) {
            depth = Math.max(depth, depth(trace, child) + 1);
        }
        return depth;
    }

    private static int totalSpans(final Trace trace) {
        return trace.getParentSpanIdMap()
                .values()
                .stream()
                .mapToInt(List::size)
                .sum();
    }

    @JsonCreator
    public TraceRoot(@JsonProperty("traceId") final String traceId,
                     @JsonProperty("name") final String name,
                     @JsonProperty("startTime") final NanoTime startTime,
                     @JsonProperty("endTime") final NanoTime endTime,
                     @JsonProperty("services") final int services,
                     @JsonProperty("depth") final int depth,
                     @JsonProperty("totalSpans") final int totalSpans,
                     @JsonProperty("lastActivityMs") final long lastActivityMs,
                     @JsonProperty("rootEndTime") final NanoTime rootEndTime,
                     @JsonProperty("orphan") final boolean orphan,
                     @JsonProperty("error") final boolean error) {
        this.traceId = traceId;
        this.name = name;
        this.startTime = startTime;
        this.endTime = endTime;
        this.services = services;
        this.depth = depth;
        this.totalSpans = totalSpans;
        this.lastActivityMs = lastActivityMs;
        this.rootEndTime = rootEndTime;
        this.orphan = orphan;
        this.error = error;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getName() {
        return name;
    }

    public NanoTime getStartTime() {
        return startTime;
    }

    public NanoTime getEndTime() {
        return endTime;
    }

    public int getServices() {
        return services;
    }

    public int getDepth() {
        return depth;
    }

    public int getTotalSpans() {
        return totalSpans;
    }

    public long getLastActivityMs() {
        return lastActivityMs;
    }

    public NanoTime getRootEndTime() {
        return rootEndTime;
    }

    public boolean isOrphan() {
        return orphan;
    }

    public boolean isError() {
        return error;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TraceRoot traceRoot = (TraceRoot) o;
        return services == traceRoot.services &&
               depth == traceRoot.depth &&
               totalSpans == traceRoot.totalSpans &&
               lastActivityMs == traceRoot.lastActivityMs &&
               Objects.equals(traceId, traceRoot.traceId) &&
               Objects.equals(name, traceRoot.name) &&
               Objects.equals(startTime, traceRoot.startTime) &&
               Objects.equals(endTime, traceRoot.endTime) &&
               Objects.equals(rootEndTime, traceRoot.rootEndTime) &&
               orphan == traceRoot.orphan &&
               error == traceRoot.error;
    }

    @Override
    public int hashCode() {
        return Objects.hash(traceId, name, startTime, endTime, services, depth, totalSpans,
                lastActivityMs, rootEndTime, orphan, error);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder extends AbstractBuilder<TraceRoot, Builder> {

        private String traceId;
        private String name;
        private NanoTime startTime;
        private NanoTime endTime;
        private int services;
        private int depth;
        private int totalSpans;
        private long lastActivityMs;
        private NanoTime rootEndTime;
        private boolean orphan;
        private boolean error;

        private Builder() {
        }

        private Builder(final TraceRoot traceRoot) {
            this.traceId = traceRoot.traceId;
            this.name = traceRoot.name;
            this.startTime = traceRoot.startTime;
            this.endTime = traceRoot.endTime;
            this.services = traceRoot.services;
            this.depth = traceRoot.depth;
            this.totalSpans = traceRoot.totalSpans;
            this.lastActivityMs = traceRoot.lastActivityMs;
            this.rootEndTime = traceRoot.rootEndTime;
            this.orphan = traceRoot.orphan;
            this.error = traceRoot.error;
        }

        public Builder traceId(final String traceId) {
            this.traceId = traceId;
            return self();
        }

        public Builder name(final String name) {
            this.name = name;
            return self();
        }

        public Builder startTime(final NanoTime startTime) {
            this.startTime = startTime;
            return self();
        }

        public Builder endTime(final NanoTime endTime) {
            this.endTime = endTime;
            return self();
        }

        public Builder services(final int services) {
            this.services = services;
            return self();
        }

        public Builder depth(final int depth) {
            this.depth = depth;
            return self();
        }

        public Builder totalSpans(final int totalSpans) {
            this.totalSpans = totalSpans;
            return self();
        }

        public Builder lastActivityMs(final long lastActivityMs) {
            this.lastActivityMs = lastActivityMs;
            return self();
        }

        public Builder rootEndTime(final NanoTime rootEndTime) {
            this.rootEndTime = rootEndTime;
            return self();
        }

        public Builder orphan(final boolean orphan) {
            this.orphan = orphan;
            return self();
        }

        public Builder error(final boolean error) {
            this.error = error;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public TraceRoot build() {
            return new TraceRoot(
                    traceId,
                    name,
                    startTime,
                    endTime,
                    services,
                    depth,
                    totalSpans,
                    lastActivityMs,
                    rootEndTime,
                    orphan,
                    error
            );
        }
    }
}
