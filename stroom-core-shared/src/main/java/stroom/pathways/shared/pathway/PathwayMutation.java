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

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * One change a trace made to a learnt model, kept so the model's growth can be replayed rather than
 * only seen as it ended up.
 *
 * <p>Enough to redo or undo the change on its own: where in the model it happened, what it was before
 * and what it became. There is no separate field for the value's type because a
 * {@link ConstraintValue} already carries it.
 */
@JsonInclude(Include.NON_NULL)
public class PathwayMutation {

    // The names the grid sorts by, shared so the column clicked and the field ordered on cannot drift.
    public static final String FIELD_TIME = "Time";
    public static final String FIELD_TYPE = "Change";
    public static final String FIELD_PATH = "Node";
    public static final String FIELD_CONSTRAINT = "Constraint";
    public static final String FIELD_OLD_VALUE = "Was";
    public static final String FIELD_NEW_VALUE = "Now";
    public static final String FIELD_TRACE_ID = "Trace";
    public static final String FIELD_SPAN_ID = "Span";

    /**
     * Where this sits in its pathway's history, counting from one. Ordering and addressing both use
     * this rather than the time, which can tie within a batch and can step backwards with the clock.
     */
    @JsonProperty
    private final long sequence;
    @JsonProperty
    private final NanoTime time;
    /**
     * When the trace itself ran, as against {@link #time}, which is when the model learnt from it.
     *
     * <p>A trace is queued and picked up later, so the two differ by however long it waited — minutes
     * while the consumer keeps up, and as long as processing is stopped for when it does not. Held
     * beside the applied time rather than instead of it: the list is ordered by the order things were
     * learnt, and replay depends on that order, so what a row shows has to stay the applied time.
     */
    @JsonProperty
    private final NanoTime traceTime;
    /**
     * Which trace store the trace came from, as a position in the source list on {@link Paths}, or
     * -1 where it arrived without one. Recorded rather than looked up: a store can later be pointed
     * at a different pathways document, and what it already taught this one stays behind.
     */
    @JsonProperty
    private final int source;
    @JsonProperty
    private final String traceId;
    @JsonProperty
    private final String spanId;
    /** The node this happened to, named from the root down. Empty for the pathway itself. */
    @JsonProperty
    private final List<String> nodePath;
    /**
     * The node this happened to. Kept so a replay puts a node back with the identity it had, rather
     * than a fresh one that makes every frame look like the whole tree was replaced.
     */
    @JsonProperty
    private final String nodeUuid;
    /** Which constraint changed, or null where the change was to the node rather than a constraint. */
    @JsonProperty
    private final String constraint;
    @JsonProperty
    private final MutationType type;
    /**
     * Whether the constraint is optional once this change has been made. A value changing never moves
     * it, but a constraint can be born optional, and a replay has no way to work that out.
     */
    @JsonProperty
    private final boolean optional;
    /** What the constraint held before, or null where it held nothing. */
    @JsonProperty
    private final ConstraintValue oldValue;
    /** What it holds now, or null where the change added no value. */
    @JsonProperty
    private final ConstraintValue newValue;

    @JsonCreator
    public PathwayMutation(@JsonProperty("sequence") final Long sequence,
                           @JsonProperty("time") final NanoTime time,
                           @JsonProperty("traceTime") final NanoTime traceTime,
                           @JsonProperty("source") final Integer source,
                           @JsonProperty("traceId") final String traceId,
                           @JsonProperty("spanId") final String spanId,
                           @JsonProperty("nodePath") final List<String> nodePath,
                           @JsonProperty("nodeUuid") final String nodeUuid,
                           @JsonProperty("constraint") final String constraint,
                           @JsonProperty("type") final MutationType type,
                           @JsonProperty("optional") final Boolean optional,
                           @JsonProperty("oldValue") final ConstraintValue oldValue,
                           @JsonProperty("newValue") final ConstraintValue newValue) {
        this.sequence = Objects.requireNonNullElse(sequence, 0L);
        this.nodeUuid = nodeUuid;
        this.time = time;
        this.traceTime = traceTime;
        this.source = Objects.requireNonNullElse(source, 0);
        this.traceId = traceId;
        this.spanId = spanId;
        this.nodePath = nodePath;
        this.constraint = constraint;
        this.type = type;
        this.optional = Objects.requireNonNullElse(optional, false);
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    /**
     * Orders changes by one of the columns a grid offers, or by sequence where the name is not one of
     * them. Here rather than beside either caller because both the reader and the view sort by the
     * same names and must agree on what they mean.
     */
    public static Comparator<PathwayMutation> comparator(final String field, final boolean descending) {
        final Comparator<PathwayMutation> comparator = switch (field == null
                ? ""
                : field) {
            case FIELD_TIME -> Comparator.comparingLong(PathwayMutation::getSequence);
            case FIELD_TYPE -> text(m -> m.getType() == null
                    ? null
                    : m.getType().getDisplayValue());
            case FIELD_PATH -> text(m -> m.getNodePath() == null
                    ? null
                    : String.join(" / ", m.getNodePath()));
            case FIELD_CONSTRAINT -> text(PathwayMutation::getConstraint);
            case FIELD_OLD_VALUE -> text(m -> asText(m.getOldValue()));
            case FIELD_NEW_VALUE -> text(m -> asText(m.getNewValue()));
            case FIELD_TRACE_ID -> text(PathwayMutation::getTraceId);
            case FIELD_SPAN_ID -> text(PathwayMutation::getSpanId);
            default -> Comparator.comparingLong(PathwayMutation::getSequence);
        };
        return descending
                ? comparator.reversed()
                : comparator;
    }

    private static Comparator<PathwayMutation> text(final Function<PathwayMutation, String> value) {
        return Comparator.comparing(value, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER));
    }

    private static String asText(final ConstraintValue value) {
        return value == null
                ? null
                : value.toString();
    }

    /**
     * The same change, numbered and attributed. Used when writing, where both its place in the
     * pathway's history and the store its trace came from are known.
     */
    public PathwayMutation withSequence(final long sequence, final int source) {
        return new PathwayMutation(sequence, time, traceTime, source, traceId, spanId, nodePath,
                nodeUuid, constraint, type, optional, oldValue, newValue);
    }

    public long getSequence() {
        return sequence;
    }

    public NanoTime getTraceTime() {
        return traceTime;
    }

    /**
     * Where the trace came from, as a position in the source list on {@link Paths}, or -1 where it
     * arrived without one.
     */
    public int getSource() {
        return source;
    }

    public NanoTime getTime() {
        return time;
    }

    public String getNodeUuid() {
        return nodeUuid;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public List<String> getNodePath() {
        return nodePath;
    }

    public String getConstraint() {
        return constraint;
    }

    public MutationType getType() {
        return type;
    }

    public boolean isOptional() {
        return optional;
    }

    public ConstraintValue getOldValue() {
        return oldValue;
    }

    public ConstraintValue getNewValue() {
        return newValue;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PathwayMutation that = (PathwayMutation) o;
        return sequence == that.sequence
               && Objects.equals(nodeUuid, that.nodeUuid)
               && Objects.equals(time, that.time)
               && Objects.equals(traceTime, that.traceTime)
               && source == that.source
               && Objects.equals(traceId, that.traceId)
               && Objects.equals(spanId, that.spanId)
               && Objects.equals(nodePath, that.nodePath)
               && Objects.equals(constraint, that.constraint)
               && type == that.type
               && optional == that.optional
               && Objects.equals(oldValue, that.oldValue)
               && Objects.equals(newValue, that.newValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequence, time, traceTime, source, traceId, spanId, nodePath, nodeUuid,
                constraint, type, optional, oldValue, newValue);
    }

    @Override
    public String toString() {
        return sequence + " " + type + " " + nodePath + (constraint == null
                ? ""
                : " " + constraint) + " " + oldValue + " -> " + newValue;
    }
}
