package stroom.pathways.impl;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferPoolOutput;
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
import stroom.pathways.shared.pathway.NamePathKey;
import stroom.pathways.shared.pathway.NamesPathKey;
import stroom.pathways.shared.pathway.NanoTimeRange;
import stroom.pathways.shared.pathway.NanoTimeValue;
import stroom.pathways.shared.pathway.PathKey;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.PathNodeSequence;
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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class PathwaySerde {

    private final ByteBufferFactory byteBufferFactory;
    private int bufferSize = 128;

    @Inject
    public PathwaySerde(final ByteBufferFactory byteBufferFactory) {
        this.byteBufferFactory = byteBufferFactory;
    }

    public Pathway readPathway(final ByteBuffer byteBuffer) {
        return readPathway(new UnsafeByteBufferInput(byteBuffer));
    }

    private Pathway readPathway(final Input input) {
        return Pathway.builder()
                .name(input.readString())
                .createTime(readNanoTime(input))
                .updateTime(readNanoTime(input))
                .lastUsedTime(readNanoTime(input))
                .pathKey(readPathKey(input))
                .root(readPathNode(input))
                .build();
    }

    private NanoTime readNanoTime(final Input input) {
        return new NanoTime(input.readLong(), input.readInt());
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
                .path(readStrings(input))
                .targets(readList(input, this::readPathNodeSequence))
                .constraints(readConstraints(input))
                .build();
    }

    private PathNodeSequence readPathNodeSequence(final Input input) {
        return PathNodeSequence.builder()
                .uuid(input.readString())
                .pathKey(readPathKey(input))
                .nodes(readList(input, this::readPathNode))
                .build();
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
                .build();
    }

    private ConstraintValue readConstraintValue(final Input input) {
        final ConstraintValueType type =
                ConstraintValueType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(input.readByte());
        return switch (type) {
            case ConstraintValueType.ANY -> new AnyTypeValue();
            case ConstraintValueType.DURATION_VALUE -> new NanoTimeValue(readNanoTime(input));
            case ConstraintValueType.DURATION_RANGE -> new NanoTimeRange(readNanoTime(input), readNanoTime(input));
            case ConstraintValueType.STRING -> new StringValue(input.readString());
            case ConstraintValueType.STRING_SET -> new StringSet(readSet(input, Input::readString));
            case ConstraintValueType.REGEX -> new Regex(input.readString());
            case ConstraintValueType.BOOLEAN -> new BooleanValue(input.readBoolean());
            case ConstraintValueType.ANY_BOOLEAN -> new AnyBoolean();
            case ConstraintValueType.INTEGER -> new IntegerValue(input.readInt());
            case ConstraintValueType.INTEGER_SET -> new IntegerSet(readSet(input, Input::readInt));
            case ConstraintValueType.INTEGER_RANGE -> new IntegerRange(input.readInt(), input.readInt());
            case ConstraintValueType.DOUBLE -> new DoubleValue(input.readDouble());
            case ConstraintValueType.DOUBLE_SET -> new DoubleSet(readSet(input, Input::readDouble));
            case ConstraintValueType.DOUBLE_RANGE -> new DoubleRange(input.readDouble(), input.readDouble());
        };
    }

    public void writePathway(final Pathway pathway, final Consumer<ByteBuffer> consumer) {
        try (final ByteBufferPoolOutput output =
                new ByteBufferPoolOutput(byteBufferFactory, bufferSize, -1)) {
            writePathway(pathway, output);
            final ByteBuffer byteBuffer = output.getByteBuffer().flip();
            bufferSize = Math.max(bufferSize, byteBuffer.capacity());
            consumer.accept(byteBuffer);
        }
    }

    private void writePathway(final Pathway pathway, final Output output) {
        output.writeString(pathway.getName());
        writeNanoTime(pathway.getCreateTime(), output);
        writeNanoTime(pathway.getUpdateTime(), output);
        writeNanoTime(pathway.getLastUsedTime(), output);
        writePathKey(pathway.getPathKey(), output);
        writePathNode(pathway.getRoot(), output);
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
        writeStrings(pathNode.getPath(), output);
        writeList(pathNode.getTargets(), output, this::writePathNodeSequence);
//        writeList(pathNode.getSpans(), output, this::writeSpan);
        writeConstraints(pathNode.getConstraints(), output);
    }

    private void writePathNodeSequence(final PathNodeSequence pathNodeSequence, final Output output) {
        output.writeString(pathNodeSequence.getUuid());
        writePathKey(pathNodeSequence.getPathKey(), output);
        writeList(pathNodeSequence.getNodes(), output, this::writePathNode);
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

    private void writeConstraint(final Constraint constraint, final Output output) {
        writeString(constraint.getName(), output);
        writeConstraintValue(constraint.getValue(), output);
        output.writeBoolean(constraint.isOptional());
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
                writeNanoTime(nanoTimeRange.getMin(), output);
                writeNanoTime(nanoTimeRange.getMax(), output);
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
