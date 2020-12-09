package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Expression;
import stroom.dashboard.expression.v1.Generator;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

public class ItemSerialiser {
    private final CompiledField[] fields;

    public ItemSerialiser(final CompiledField[] fields) {
        this.fields = fields;
    }

    static byte[] toBytes(final RawItem rawItem) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (final Output output = new Output(byteArrayOutputStream)) {
            writeRawItem(rawItem, output);
        }
        return byteArrayOutputStream.toByteArray();
    }

    static RawItem readRawItem(final Input input) {
        try {
            final int groupKeyLength = input.readInt();
            final RawKey groupKey = new RawKey(input.readBytes(groupKeyLength));
            final byte[] generators = input.readAllBytes();
            return new RawItem(groupKey, generators);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static RawItem readRawItem(final byte[] bytes) {
        try (final Input input = new Input(new ByteArrayInputStream(bytes))) {
            return readRawItem(input);
        }
    }

    static void writeRawItem(final RawItem rawItem, final Output output) {
        if (rawItem.getGroupKey() != null) {
            output.writeInt(rawItem.getGroupKey().getBytes().length);
            output.writeBytes(rawItem.getGroupKey().getBytes());
        } else {
            output.writeInt(0);
        }

        if (rawItem.getGenerators() != null) {
            output.writeBytes(rawItem.getGenerators());
        }
    }

    byte[] toBytes(final Generator[] generators) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (final Output output = new Output(byteArrayOutputStream)) {
            writeGenerators(generators, output);
        }
        return byteArrayOutputStream.toByteArray();
    }

    private Generator[] readGenerators(final byte[] bytes) {
        try (final Input input = new Input(new ByteArrayInputStream(bytes))) {
            return readGenerators(input);
        }
    }

//    Item toItem(final RawItem rawItem) {
//        Generator[] generatorArray;
//        try (final Input input = new Input(new ByteArrayInputStream(rawItem.getGenerators()))) {
//            generatorArray = readGenerators( input);
//        }
//        return new Item(rawItem.getGroupKey(), generatorArray);
//    }

    private Generator[] readGenerators(final Input input) {
        final Generator[] generators = new Generator[fields.length];
        int pos = 0;
        for (final CompiledField compiledField : fields) {
            final boolean nonNull = input.readBoolean();
            if (nonNull) {
                final Expression expression = compiledField.getExpression();
                final Generator generator = expression.createGenerator();
                generator.read(input);
                generators[pos] = generator;
            }
            pos++;
        }
        return generators;
    }

    private void writeGenerators(final Generator[] generators, final Output output) {
        if (generators.length > Byte.MAX_VALUE) {
            throw new RuntimeException("You can only write a maximum of " + 255 + " values");
        }
        for (final Generator generator : generators) {
            if (generator != null) {
                output.writeBoolean(true);
                generator.write(output);
            } else {
                output.writeBoolean(false);
            }
        }
    }

    byte[] toBytes(final Item item) {
        final RawItem rawItem = new RawItem(item.getGroupKey(), toBytes(item.getGenerators()));
        return toBytes(rawItem);
    }

    Item readItem(final byte[] bytes) {
        final RawItem rawItem = readRawItem(bytes);
        Generator[] generators = readGenerators(rawItem.getGenerators());
        return new Item(rawItem.getGroupKey(), generators);
    }
}
