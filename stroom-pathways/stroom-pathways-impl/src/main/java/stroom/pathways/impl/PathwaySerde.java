/*
 * Copyright 2025 Crown Copyright
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

package stroom.pathways.impl;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferPoolOutput;
import stroom.pathways.shared.PathwaySummary;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.pathway.AnyBoolean;
import stroom.pathways.shared.pathway.AnyTypeValue;
import stroom.pathways.shared.pathway.BooleanValue;
import stroom.pathways.shared.pathway.Constraint;
import stroom.pathways.shared.pathway.ConstraintValue;
import stroom.pathways.shared.pathway.ConstraintValueType;
import stroom.pathways.shared.pathway.DoubleRange;
import stroom.pathways.shared.pathway.DoubleSet;
import stroom.pathways.shared.pathway.DoubleValue;
import stroom.pathways.shared.pathway.IntegerRange;
import stroom.pathways.shared.pathway.IntegerSet;
import stroom.pathways.shared.pathway.IntegerValue;
import stroom.pathways.shared.pathway.LongRange;
import stroom.pathways.shared.pathway.LongSet;
import stroom.pathways.shared.pathway.LongValue;
import stroom.pathways.shared.pathway.MutationType;
import stroom.pathways.shared.pathway.NamePathKey;
import stroom.pathways.shared.pathway.NamesPathKey;
import stroom.pathways.shared.pathway.NanoTimeRange;
import stroom.pathways.shared.pathway.NanoTimeValue;
import stroom.pathways.shared.pathway.PathKey;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.PathStep;
import stroom.pathways.shared.pathway.PathUse;
import stroom.pathways.shared.pathway.Paths;
import stroom.pathways.shared.pathway.Pathway;
import stroom.pathways.shared.pathway.Regex;
import stroom.pathways.shared.pathway.StringSet;
import stroom.pathways.shared.pathway.StringValue;
import stroom.pathways.shared.pathway.TerminalPathKey;
import stroom.util.shared.NullSafe;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferInput;
import jakarta.inject.Inject;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class PathwaySerde {

    /**
     * Where a write starts from when the caller has nothing better to offer, which is a pathway that
     * does not exist yet. Kryo grows the buffer from here as it writes.
     */
    private static final int MIN_BUFFER_SIZE = 128;

    /**
     * Which shape a stored pathway has. Raise this whenever a field is added, removed or moved,
     * anywhere in the pathway, its nodes, or their constraints.
     */
    private static final byte PATHWAY_VERSION = 1;

    /**
     * Which shape a stored mutation has. The layout is positional and carries no field names, so a row
     * written by a build that laid the fields out differently cannot be told apart from a current one
     * by its contents alone. Raise this whenever a field is added, removed or moved.
     */
    private static final byte MUTATION_VERSION = 2;
    private static final byte NODE_VERSION = 1;
    private static final byte TRACE_VERSION = 1;
    private static final byte USAGE_VERSION = 2;
    private static final byte COUNTS_VERSION = 1;
    private static final byte OBSERVED_VALUES_VERSION = 1;

    /**
     * Added to a shape's node before it is written and taken off after. A shape that is not a node
     * carries a negative marker, and a variable length integer written as positive cannot hold one.
     */
    private static final int MARKER_OFFSET = 4;

    private final ByteBufferFactory byteBufferFactory;

    @Inject
    public PathwaySerde(final ByteBufferFactory byteBufferFactory) {
        this.byteBufferFactory = byteBufferFactory;
    }

    public Pathway readPathway(final ByteBuffer byteBuffer) {
        final Input input = new UnsafeByteBufferInput(byteBuffer);
        checkVersion(input.readByte(), PATHWAY_VERSION, "pathway", "pathways");
        return readPathway(input);
    }

    /**
     * Reads only what the pathway list shows, leaving the model where it is.
     *
     * <p>Every field a row needs is written before {@code pathKey} and {@code root}, so this is a
     * matter of stopping rather than of skipping — nothing is decoded that is not displayed. That is
     * the whole point: a pathway keeps every path it has seen, so deserialising one to read its name
     * is what made a page of them unopenable.
     *
     * @param byteBuffer the stored value, whose {@code remaining()} is taken as the pathway's size
     *                   before anything is read from it.
     */
    public PathwaySummary readSummary(final ByteBuffer byteBuffer) {
        final long sizeBytes = byteBuffer.remaining();
        final Input input = new UnsafeByteBufferInput(byteBuffer);
        checkVersion(input.readByte(), PATHWAY_VERSION, "pathway", "pathways");
        return new PathwaySummary(
                input.readString(),
                readNanoTime(input),
                readNanoTime(input),
                readNanoTime(input),
                input.readLong(),
                input.readLong(),
                input.readVarInt(true),
                input.readVarInt(true),
                sizeBytes);
    }

    private Pathway readPathway(final Input input) {
        final Pathway.Builder builder = Pathway.builder()
                .name(input.readString())
                .createTime(readNanoTime(input))
                .updateTime(readNanoTime(input))
                .lastUsedTime(readNanoTime(input))
                .timesUsed(input.readLong())
                .timesUpdated(input.readLong());

        // The node and path counts the list reads. Taken off the front to keep everything after
        // them lined up, and then dropped: what they count is about to be read in full, and a
        // pathway holding its own count of itself is a second answer that can disagree.
        input.readVarInt(true);
        input.readVarInt(true);

        return builder
                .pathKey(readPathKey(input))
                .root(readPathNode(input))
                .paths(readPaths(input))
                .build();
    }

    private NanoTime readNanoTime(final Input input) {
        return new NanoTime(input.readLong(), input.readInt());
    }

    // A node or constraint made outside the learner has never been used, so it has no time to write.
    private NanoTime readNullableNanoTime(final Input input) {
        return input.readBoolean()
                ? readNanoTime(input)
                : null;
    }

    private void writeNullableNanoTime(final NanoTime nanoTime, final Output output) {
        output.writeBoolean(nanoTime != null);
        if (nanoTime != null) {
            writeNanoTime(nanoTime, output);
        }
    }

    private PathKey readPathKey(final Input input) {
        final byte type = input.readByte();
        return switch (type) {
            case 0 -> new NamePathKey(input.readString());
            case 1 -> new NamesPathKey(readStrings(input));
            default -> TerminalPathKey.INSTANCE;
        };
    }

    private PathNode readPathNode(final Input input) {
        return PathNode.builder()
                .uuid(input.readString())
                .name(input.readString())
                .nodePath(readStrings(input))
                .children(readList(input, this::readPathNode))
                .constraints(readConstraints(input))
                .timesUsed(input.readLong())
                .lastUsedTime(readNullableNanoTime(input))
                .retiredTime(readNullableNanoTime(input))
                .build();
    }

    private Paths readPaths(final Input input) {
        return new Paths(readStrings(input),
                readList(input, this::readPathStep),
                readList(input, this::readPathUse),
                readStrings(input));
    }

    // Written one more than it is held, so the group marker of -1 fits a variable length integer
    // that cannot carry a negative.
    private PathStep readPathStep(final Input input) {
        return new PathStep(
                input.readVarInt(true) - MARKER_OFFSET,
                readList(input, in -> in.readVarInt(true)));
    }

    private PathUse readPathUse(final Input input) {
        return new PathUse(
                input.readVarInt(true) - MARKER_OFFSET,
                input.readLong(),
                readNullableNanoTime(input),
                readNullableNanoTime(input),
                input.readString(),
                readNullableNanoTime(input),
                readNullableNanoTime(input),
                input.readVarInt(true) - MARKER_OFFSET);
    }

    private List<String> readStrings(final Input input) {
        return readList(input, Input::readString);
    }

    private <R> List<R> readList(final Input input, final Function<Input, R> function) {
        final int size = input.readInt();
        final List<R> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(function.apply(input));
        }
        return list;
    }

    private <R> Set<R> readSet(final Input input, final Function<Input, R> function) {
        final int size = input.readInt();
        final Set<R> set = new HashSet<>(size);
        for (int i = 0; i < size; i++) {
            set.add(function.apply(input));
        }
        return set;
    }

    private Map<String, Constraint> readConstraints(final Input input) {
        final int size = input.readInt();
        final Map<String, Constraint> map = new HashMap<>(size);
        for (int i = 0; i < size; i++) {
            map.put(input.readString(), readConstraint(input));
        }
        return map;
    }

    private Constraint readConstraint(final Input input) {
        return Constraint.builder()
                .name(input.readString())
                .value(readConstraintValue(input))
                .optional(input.readBoolean())
                .timesUsed(input.readLong())
                .lastUsedTime(readNullableNanoTime(input))
                .build();
    }

    private ConstraintValue readConstraintValue(final Input input) {
        final ConstraintValueType type =
                ConstraintValueType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(input.readByte());
        return switch (type) {
            case ConstraintValueType.ANY -> new AnyTypeValue();
            case ConstraintValueType.DURATION_VALUE -> new NanoTimeValue(readNanoTime(input));
            case ConstraintValueType.DURATION_RANGE -> new NanoTimeRange(readNanoTime(input), readNanoTime(input));
            // One end left open, so only the other was written.
            case ConstraintValueType.DURATION_AT_LEAST -> new NanoTimeRange(readNanoTime(input), null);
            case ConstraintValueType.DURATION_AT_MOST -> new NanoTimeRange(null, readNanoTime(input));
            case ConstraintValueType.STRING -> new StringValue(input.readString());
            case ConstraintValueType.STRING_SET -> new StringSet(readSet(input, Input::readString));
            case ConstraintValueType.REGEX -> new Regex(input.readString());
            case ConstraintValueType.BOOLEAN -> new BooleanValue(input.readBoolean());
            case ConstraintValueType.ANY_BOOLEAN -> new AnyBoolean();
            case ConstraintValueType.INTEGER -> new IntegerValue(input.readInt());
            case ConstraintValueType.INTEGER_SET -> new IntegerSet(readSet(input, Input::readInt));
            case ConstraintValueType.INTEGER_RANGE -> new IntegerRange(input.readInt(), input.readInt());
            case ConstraintValueType.LONG -> new LongValue(input.readLong());
            case ConstraintValueType.LONG_SET -> new LongSet(readSet(input, Input::readLong));
            case ConstraintValueType.LONG_RANGE -> new LongRange(input.readLong(), input.readLong());
            case ConstraintValueType.DOUBLE -> new DoubleValue(input.readDouble());
            case ConstraintValueType.DOUBLE_SET -> new DoubleSet(readSet(input, Input::readDouble));
            case ConstraintValueType.DOUBLE_RANGE -> new DoubleRange(input.readDouble(), input.readDouble());
        };
    }

    /**
     * @param sizeHint what the pathway took when it was last stored, or 0 where it has not been stored
     *                 yet. A rewrite is almost exactly the size of what it replaces, so this is the
     *                 best estimate available and it costs nothing to obtain — the caller has just read
     *                 the value it is replacing. Kryo still grows the buffer where the hint falls
     *                 short, so a wrong hint costs a copy rather than a failure.
     *                 <p>The size is not remembered between calls. One pathway is not a guide to the
     *                 next: this serde is shared by every thread applying traces, so a remembered
     *                 maximum would have every write of every pathway allocate whatever the largest
     *                 one needed.
     */
    public void writeMutation(final StoredChange change, final Consumer<ByteBuffer> consumer) {
        try (final ByteBufferPoolOutput output =
                new ByteBufferPoolOutput(byteBufferFactory, MIN_BUFFER_SIZE, -1)) {
            output.writeByte(MUTATION_VERSION);
            output.writeLong(change.sequence());
            output.writeVarLong(change.sequence() - change.traceSequence(), true);
            output.writeVarLong(change.nodeId(), true);
            output.writeString(change.spanId());
            output.writeString(change.constraint());
            output.writeByte(change.type().getPrimitiveValue());
            output.writeBoolean(change.optional());
            writeNullableValue(change.oldValue(), output);
            writeNullableValue(change.newValue(), output);
            consumer.accept(output.getByteBuffer().flip());
        }
    }

    /**
     * One node of one pathway, written once and referred to by number from every change that node
     * ever receives. The path is held alongside the uuid rather than worked out from the model,
     * because the model's shape can move around a node and a change has to say where the node stood
     * when it was made.
     */
    public void writeNode(final StoredNode node, final Consumer<ByteBuffer> consumer) {
        try (final ByteBufferPoolOutput output =
                new ByteBufferPoolOutput(byteBufferFactory, MIN_BUFFER_SIZE, -1)) {
            output.writeByte(NODE_VERSION);
            output.writeString(node.uuid());
            writeStrings(node.path(), output);
            consumer.accept(output.getByteBuffer().flip());
        }
    }

    public StoredNode readNode(final ByteBuffer byteBuffer) {
        final Input input = new UnsafeByteBufferInput(byteBuffer);
        checkVersion(input.readByte(), NODE_VERSION, "pathway node", "mutations");
        return new StoredNode(input.readString(), readStrings(input));
    }

    /**
     * What one trace, or one run of the narrowing, was: written once however many changes it made.
     * The span is not here because it moves from node to node within a trace, so it stays with the
     * change that names the node.
     */
    public void writeTrace(final StoredTrace trace, final Consumer<ByteBuffer> consumer) {
        try (final ByteBufferPoolOutput output =
                new ByteBufferPoolOutput(byteBufferFactory, MIN_BUFFER_SIZE, -1)) {
            output.writeByte(TRACE_VERSION);
            output.writeString(trace.traceId());
            writeNanoTime(trace.time(), output);
            writeNanoTime(trace.traceTime(), output);
            output.writeVarInt(trace.source() + MARKER_OFFSET, true);
            consumer.accept(output.getByteBuffer().flip());
        }
    }

    public StoredTrace readTrace(final ByteBuffer byteBuffer) {
        final Input input = new UnsafeByteBufferInput(byteBuffer);
        checkVersion(input.readByte(), TRACE_VERSION, "pathway trace", "mutations");
        return new StoredTrace(input.readString(),
                readNanoTime(input),
                readNanoTime(input),
                input.readVarInt(true) - MARKER_OFFSET);
    }

    /**
     * A day's worth of what each of a model's constraints was given, by node uuid then constraint
     * name. Written with the same constraint values the model holds, so the two can be compared
     * without either being converted.
     */
    public void writeObservedValues(final Map<String, Map<String, ConstraintValue>> observed,
                                    final Consumer<ByteBuffer> consumer) {
        try (final ByteBufferPoolOutput output =
                new ByteBufferPoolOutput(byteBufferFactory, MIN_BUFFER_SIZE, -1)) {
            output.writeByte(OBSERVED_VALUES_VERSION);
            output.writeVarInt(observed.size(), true);
            observed.forEach((nodeUuid, constraints) -> {
                writeString(nodeUuid, output);
                output.writeVarInt(constraints.size(), true);
                constraints.forEach((name, value) -> {
                    writeString(name, output);
                    writeConstraintValue(value, output);
                });
            });
            consumer.accept(output.getByteBuffer().flip());
        }
    }

    public Map<String, Map<String, ConstraintValue>> readObservedValues(final ByteBuffer byteBuffer) {
        final Input input = new UnsafeByteBufferInput(byteBuffer);
        checkVersion(input.readByte(), OBSERVED_VALUES_VERSION, "observed values", "observed-values");

        final int nodes = input.readVarInt(true);
        final Map<String, Map<String, ConstraintValue>> observed = new LinkedHashMap<>(nodes);
        for (int i = 0; i < nodes; i++) {
            final String nodeUuid = input.readString();
            final int count = input.readVarInt(true);
            final Map<String, ConstraintValue> constraints = new LinkedHashMap<>(count);
            for (int j = 0; j < count; j++) {
                constraints.put(input.readString(), readConstraintValue(input));
            }
            observed.put(nodeUuid, constraints);
        }
        return observed;
    }

    public StoredChange readMutation(final ByteBuffer byteBuffer) {
        final Input input = new UnsafeByteBufferInput(byteBuffer);
        checkVersion(input.readByte(), MUTATION_VERSION, "pathway mutation", "mutations");

        final long sequence = input.readLong();
        return new StoredChange(
                sequence,
                sequence - input.readVarLong(true),
                input.readVarLong(true),
                input.readString(),
                input.readString(),
                MutationType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(input.readByte()),
                input.readBoolean(),
                readNullableValue(input),
                readNullableValue(input));
    }

    /**
     * One change as it is stored: what it did, and numbers standing for the node it was made against
     * and the trace that made it.
     */
    public record StoredChange(long sequence,
                               long traceSequence,
                               long nodeId,
                               String spanId,
                               String constraint,
                               MutationType type,
                               boolean optional,
                               ConstraintValue oldValue,
                               ConstraintValue newValue) {

    }

    /** A node of a pathway, as the changes against it refer to it. */
    public record StoredNode(String uuid, List<String> path) {

    }

    /** What made a run of changes, as those changes refer to it. */
    public record StoredTrace(String traceId, NanoTime time, NanoTime traceTime, int source) {

    }

    // A mutation that added something has no old value, and one that added a node has neither, so both
    // sides carry a presence flag rather than a type byte that would have nothing to describe.
    // Read as though it were the current shape, a row laid out differently comes back as whatever its
    // bytes happened to mean. Refusing says what is wrong and which table to clear.
    private static void checkVersion(final byte found, final byte expected, final String what,
                                     final String table) {
        if (found != expected) {
            throw new IllegalStateException(what + " written in format " + found + ", and this reads "
                                            + "format " + expected + ". The " + table + " table has "
                                            + "to be cleared before it can be read.");
        }
    }

    private void writeNullableValue(final ConstraintValue value, final Output output) {
        output.writeBoolean(value != null);
        if (value != null) {
            writeConstraintValue(value, output);
        }
    }

    private ConstraintValue readNullableValue(final Input input) {
        return input.readBoolean()
                ? readConstraintValue(input)
                : null;
    }

    public void writePathway(final Pathway pathway, final int sizeHint, final Consumer<ByteBuffer> consumer) {
        final int initialSize = Math.max(MIN_BUFFER_SIZE, sizeHint);
        try (final ByteBufferPoolOutput output =
                new ByteBufferPoolOutput(byteBufferFactory, initialSize, -1)) {
            writePathway(pathway, output);
            consumer.accept(output.getByteBuffer().flip());
        }
    }

    private void writePathway(final Pathway pathway, final Output output) {
        output.writeByte(PATHWAY_VERSION);
        output.writeString(pathway.getName());
        writeNanoTime(pathway.getCreateTime(), output);
        writeNanoTime(pathway.getUpdateTime(), output);
        writeNanoTime(pathway.getLastUsedTime(), output);
        output.writeLong(pathway.getTimesUsed());
        output.writeLong(pathway.getTimesUpdated());
        // Derived here rather than held on the pathway, so they cannot say something the model does
        // not. Written ahead of the model because the list reads them and stops before it.
        output.writeVarInt(countNodes(pathway.getRoot()), true);
        output.writeVarInt(pathway.getPaths().getPaths().size(), true);
        writePathKey(pathway.getPathKey(), output);
        writePathNode(pathway.getRoot(), output);
        writePaths(pathway.getPaths(), output);
    }

    private void writePaths(final Paths paths, final Output output) {
        writeStrings(paths.getNodes(), output);
        writeList(paths.getSteps(), output, this::writePathStep);
        writeList(paths.getPaths(), output, this::writePathUse);
        writeStrings(paths.getSources(), output);
    }

    private void writePathStep(final PathStep step, final Output output) {
        output.writeVarInt(step.getNode() + MARKER_OFFSET, true);
        writeList(step.getSteps(), output, (child, out) -> out.writeVarInt(child, true));
    }

    // Positions rather than names, and both small, so a variable-length integer is most of why a
    // path costs a couple of bytes a visit instead of the length of a uuid.
    private void writePathUse(final PathUse pathUse, final Output output) {
        output.writeVarInt(pathUse.getRoot() + MARKER_OFFSET, true);
        output.writeLong(pathUse.getTimesUsed());
        writeNullableNanoTime(pathUse.getFirstUsedTime(), output);
        writeNullableNanoTime(pathUse.getLastUsedTime(), output);
        output.writeString(pathUse.getCreatedByTraceId());
        writeNullableNanoTime(pathUse.getTraceTime(), output);
        writeNullableNanoTime(pathUse.getLastTraceTime(), output);
        output.writeVarInt(pathUse.getSource() + MARKER_OFFSET, true);
    }

    private static int countNodes(final PathNode pathNode) {
        if (pathNode == null) {
            return 0;
        }
        int count = 1;
        for (final PathNode child : NullSafe.list(pathNode.getChildren())) {
            count += countNodes(child);
        }
        return count;
    }

    private void writeNanoTime(final NanoTime nanoTime, final Output output) {
        output.writeLong(nanoTime.getSeconds());
        output.writeInt(nanoTime.getNanos());
    }

    private void writePathKey(final PathKey pathKey, final Output output) {
        switch (pathKey) {
            case final NamePathKey namePathKey -> {
                output.writeByte(0);
                output.writeString(namePathKey.getName());
            }
            case final NamesPathKey namesPathKey -> {
                output.writeByte(1);
                writeStrings(namesPathKey.getNames(), output);
            }
            default -> output.writeByte(99);
        }
    }

    private void writePathNode(final PathNode pathNode, final Output output) {
        output.writeString(pathNode.getUuid());
        output.writeString(pathNode.getName());
        writeStrings(pathNode.getNodePath(), output);
        writeList(pathNode.getChildren(), output, this::writePathNode);
        writeConstraints(pathNode.getConstraints(), output);
        output.writeLong(pathNode.getTimesUsed());
        writeNullableNanoTime(pathNode.getLastUsedTime(), output);
        writeNullableNanoTime(pathNode.getRetiredTime(), output);
    }

    private void writeString(final String string, final Output output) {
        output.writeString(string);
    }

    private void writeLong(final long l, final Output output) {
        output.writeLong(l);
    }

    private void writeInteger(final int i, final Output output) {
        output.writeInt(i);
    }

    private void writeDouble(final double d, final Output output) {
        output.writeDouble(d);
    }

    private void writeStrings(final List<String> list, final Output output) {
        writeList(list, output, this::writeString);
    }

    private <R> void writeList(final List<R> list, final Output output, final BiConsumer<R, Output> consumer) {
        final List<R> l = NullSafe.list(list);
        output.writeInt(l.size());
        for (final R r : l) {
            consumer.accept(r, output);
        }
    }

    private <R> void writeSet(final Set<R> set, final Output output, final BiConsumer<R, Output> consumer) {
        final Set<R> l = NullSafe.set(set);
        output.writeInt(l.size());
        for (final R r : l) {
            consumer.accept(r, output);
        }
    }

    private void writeConstraints(final Map<String, Constraint> constraints, final Output output) {
        final Set<Entry<String, Constraint>> set = NullSafe.map(constraints).entrySet();
        output.writeInt(set.size());
        for (final Entry<String, Constraint> entry : set) {
            writeString(entry.getKey(), output);
            writeConstraint(entry.getValue(), output);
        }
    }

    public void writeUsage(final StoredUsage usage, final Consumer<ByteBuffer> consumer) {
        try (final ByteBufferPoolOutput output =
                new ByteBufferPoolOutput(byteBufferFactory, MIN_BUFFER_SIZE, -1)) {
            output.writeByte(USAGE_VERSION);
            output.writeLong(usage.sequence());
            writeNanoTime(usage.time(), output);
            writeList(usage.moved(), output, this::writeCount);
            writeList(usage.gone(), output, (id, out) -> out.writeVarLong(id, true));
            consumer.accept(output.getByteBuffer().flip());
        }
    }

    public StoredUsage readUsage(final ByteBuffer byteBuffer) {
        final Input input = new UnsafeByteBufferInput(byteBuffer);
        checkVersion(input.readByte(), USAGE_VERSION, "pathway usage", "mutations");
        final long sequence = input.readLong();
        final NanoTime time = readNanoTime(input);
        return new StoredUsage(sequence,
                time,
                readList(input, this::readCount),
                readList(input, in -> in.readVarLong(true)));
    }

    /**
     * How busy every node of one pathway was when its last reading was taken. Not history: it is
     * written over each time rather than added to, and is here only so that the next reading can say
     * what has moved since without reading the readings back.
     */
    public void writeCounts(final List<StoredCount> counts, final Consumer<ByteBuffer> consumer) {
        try (final ByteBufferPoolOutput output =
                new ByteBufferPoolOutput(byteBufferFactory, MIN_BUFFER_SIZE, -1)) {
            output.writeByte(COUNTS_VERSION);
            writeList(counts, output, this::writeCount);
            consumer.accept(output.getByteBuffer().flip());
        }
    }

    public List<StoredCount> readCounts(final ByteBuffer byteBuffer) {
        final Input input = new UnsafeByteBufferInput(byteBuffer);
        checkVersion(input.readByte(), COUNTS_VERSION, "pathway counts", "mutations");
        return readList(input, this::readCount);
    }

    // A node used by the trace this reading belongs to was used at that trace's own time, which the
    // reading already carries, so the time itself is only written for a node that was not.
    private void writeCount(final StoredCount count, final Output output) {
        output.writeVarLong(count.nodeId(), true);
        output.writeVarLong(count.timesUsed(), true);
        output.writeBoolean(count.usedNow());
        if (!count.usedNow()) {
            writeNullableNanoTime(count.lastUsedTime(), output);
        }
    }

    private StoredCount readCount(final Input input) {
        final long nodeId = input.readVarLong(true);
        final long timesUsed = input.readVarLong(true);
        final boolean usedNow = input.readBoolean();
        return new StoredCount(nodeId, timesUsed, usedNow, usedNow
                ? null
                : readNullableNanoTime(input));
    }

    /**
     * One reading of how busy a pathway's nodes are, holding only the nodes that have moved since the
     * reading before it. A trace runs part of a pathway and leaves the rest of it exactly as it was,
     * so naming the rest again is most of what a reading would otherwise cost.
     */
    public record StoredUsage(long sequence, NanoTime time, List<StoredCount> moved, List<Long> gone) {

    }

    /**
     * How busy one node is. {@code usedNow} says the time is the reading's own, which it is for every
     * node a trace actually ran.
     */
    public record StoredCount(long nodeId, long timesUsed, boolean usedNow, NanoTime lastUsedTime) {

    }

    private void writeConstraint(final Constraint constraint, final Output output) {
        writeString(constraint.getName(), output);
        writeConstraintValue(constraint.getValue(), output);
        output.writeBoolean(constraint.isOptional());
        output.writeLong(constraint.getTimesUsed());
        writeNullableNanoTime(constraint.getLastUsedTime(), output);
    }

    private void writeConstraintValue(final ConstraintValue constraintValue, final Output output) {
        output.writeByte(constraintValue.valueType().getPrimitiveValue());
        switch (constraintValue) {
            case final AnyTypeValue anyTypeValue -> {
            }
            case final NanoTimeValue nanoTimeValue -> {
                writeNanoTime(nanoTimeValue.getValue(), output);
            }
            case final NanoTimeRange nanoTimeRange -> {
                // Whichever ends are present, which the type byte written above already says.
                if (nanoTimeRange.getMin() != null) {
                    writeNanoTime(nanoTimeRange.getMin(), output);
                }
                if (nanoTimeRange.getMax() != null) {
                    writeNanoTime(nanoTimeRange.getMax(), output);
                }
            }
            case final StringValue stringValue -> {
                writeString(stringValue.getValue(), output);
            }
            case final StringSet stringSet -> {
                writeSet(stringSet.getSet(), output, this::writeString);
            }
            case final Regex regex -> {
                writeString(regex.getValue(), output);
            }
            case final BooleanValue booleanValue -> {
                output.writeBoolean(booleanValue.getValue());
            }
            case final AnyBoolean anyBoolean -> {
            }
            case final IntegerValue integerValue -> {
                output.writeInt(integerValue.getValue());
            }
            case final IntegerSet integerSet -> {
                writeSet(integerSet.getSet(), output, this::writeInteger);
            }
            case final IntegerRange integerRange -> {
                output.writeInt(integerRange.getMin());
                output.writeInt(integerRange.getMax());
            }
            case final LongValue longValue -> {
                output.writeLong(longValue.getValue());
            }
            case final LongSet longSet -> {
                writeSet(longSet.getSet(), output, this::writeLong);
            }
            case final LongRange longRange -> {
                output.writeLong(longRange.getMin());
                output.writeLong(longRange.getMax());
            }
            case final DoubleValue doubleValue -> {
                output.writeDouble(doubleValue.getValue());
            }
            case final DoubleSet doubleSet -> {
                writeSet(doubleSet.getSet(), output, this::writeDouble);
            }
            case final DoubleRange doubleRange -> {
                output.writeDouble(doubleRange.getMin());
                output.writeDouble(doubleRange.getMax());
            }
        }
    }
}
