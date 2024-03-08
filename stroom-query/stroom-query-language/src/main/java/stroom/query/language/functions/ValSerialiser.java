package stroom.query.language.functions;

import stroom.util.logging.LogUtil;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class ValSerialiser {

    private static final int maxId = Arrays.stream(Type.values())
            .mapToInt(Type::getId)
            .max()
            .orElse(0);
    private static final Serialiser[] SERIALISERS = new Serialiser[maxId + 1];

    static {
        SERIALISERS[Type.NULL.getId()] = new Serialiser(
                input -> ValNull.INSTANCE,
                (output, value) -> {
                });
        SERIALISERS[Type.BOOLEAN.getId()] = new Serialiser(
                input -> ValBoolean.create(input.readBoolean()),
                (output, value) -> output.writeBoolean(value.toBoolean()));
        SERIALISERS[Type.FLOAT.getId()] = new Serialiser(
                input -> ValFloat.create(input.readFloat()),
                (output, value) -> output.writeFloat(value.toFloat()));
        SERIALISERS[Type.DOUBLE.getId()] = new Serialiser(
                input -> ValDouble.create(input.readDouble()),
                (output, value) -> output.writeDouble(value.toDouble()));
        SERIALISERS[Type.INTEGER.getId()] = new Serialiser(
                input -> ValInteger.create(input.readInt()),
                (output, value) -> output.writeInt(value.toInteger()));
        SERIALISERS[Type.LONG.getId()] = new Serialiser(
                input -> ValLong.create(input.readLong()),
                (output, value) -> output.writeLong(value.toLong()));
        SERIALISERS[Type.DATE.getId()] = new Serialiser(
                input -> ValDate.create(input.readLong()),
                (output, value) -> output.writeLong(value.toLong()));
        SERIALISERS[Type.STRING.getId()] = new Serialiser(
                input -> ValString.create(input.readString()),
                (output, value) -> output.writeString(value.toString()));
        SERIALISERS[Type.ERR.getId()] = new Serialiser(
                input -> ValErr.create(input.readString()),
                (output, value) -> output.writeString(((ValErr) value).getMessage()));
        SERIALISERS[Type.DURATION.getId()] = new Serialiser(
                input -> ValDuration.create(input.readLong()),
                (output, value) -> output.writeLong(value.toLong()));
    }

    public static Val read(final Input input) {
        final int id = input.readByte();
        final Serialiser serialiser = SERIALISERS[id];
        return serialiser.reader.apply(input);
    }

    public static void write(final Output output, final Val val) {
        final byte id = val.type().getId();
        output.writeByte(id);
        final Serialiser serialiser = SERIALISERS[id];
        Objects.requireNonNull(serialiser, () -> LogUtil.message("No serialiser found for val type: {}, id: {}",
                val.type(), id));

        serialiser.writer.accept(output, val);
    }

    public static Val[] readArray(final Input input) {
        Val[] values = Val.empty();

        final int valueCount = input.readByteUnsigned();
        if (valueCount > 0) {
            values = new Val[valueCount];
            for (int i = 0; i < valueCount; i++) {
                values[i] = read(input);
            }
        }

        return values;
    }

    public static void writeArray(final Output output, final Val[] values) {
        if (values.length > 255) {
            throw new RuntimeException("You can only write a maximum of " + 255 + " values");
        }
        output.writeByte(values.length);
        for (final Val val : values) {
            write(output, val);
        }
    }

    /**
     * For testing to ensure we have all types covered
     */
    static Serialiser getSerialiser(final int id) {
        return SERIALISERS[id];
    }


    // --------------------------------------------------------------------------------


    /**
     * Package private for testing
     */
    static class Serialiser {

        final Function<Input, Val> reader;
        final BiConsumer<Output, Val> writer;

        public Serialiser(final Function<Input, Val> reader,
                          final BiConsumer<Output, Val> writer) {
            this.reader = reader;
            this.writer = writer;
        }
    }
}
