package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Expression;
import stroom.dashboard.expression.v1.Generator;
import stroom.dashboard.expression.v1.ValSerialiser;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ItemSerialiser {

    private final CompiledField[] fields;

    public ItemSerialiser(final CompiledField[] fields) {
        this.fields = fields;
    }

    Key readKey(final Input input) {
        return Metrics.measure("Key read", () -> {
            final int size = input.readInt();
            final List<KeyPart> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                final boolean grouped = input.readBoolean();
                if (grouped) {
                    list.add(new GroupKeyPart(ValSerialiser.readArray(input)));
                } else {
                    list.add(new UngroupedKeyPart(input.readLong()));
                }
            }
            return Key.fromParts(list);
        });
    }

    void writeKey(final Key key, final Output output) {
        Metrics.measure("Key write", () -> {
            output.writeInt(key.size());
            for (final KeyPart keyPart : key) {
                output.writeBoolean(keyPart.isGrouped());
                keyPart.write(output);
            }
        });
    }

    void writeChildKey(final Key key, final Output output) {
        Metrics.measure("Key write", () -> {
            output.writeInt(key.size() + 1);
            for (final KeyPart keyPart : key) {
                output.writeBoolean(keyPart.isGrouped());
                keyPart.write(output);
            }
        });
    }

    byte[] toBytes(final Key key) {
        return Metrics.measure("Key toBytes", () ->
                toBytes(output ->
                        writeKey(key, output)));
    }

    byte[] toBytes(final Consumer<Output> outputConsumer) {
        byte[] result;
        try (final Output output = new Output(100, 4096)) {
            outputConsumer.accept(output);
            output.flush();

            result = output.toBytes();
        }

        return result;
    }

    RawKey toRawKey(final Key key) {
        return Metrics.measure("Key toRawKey", () ->
                new RawKey(toBytes(key)));
    }

    Key toKey(final RawKey rawKey) {
        return Metrics.measure("Key toKey (rawKey)", () ->
                toKey(rawKey.getBytes()));
    }

    Key toKey(final byte[] bytes) {
        return Metrics.measure("Key toKey (bytes)", () -> {
            try (final Input input = new Input(bytes)) {
                return readKey(input);
            }
        });
    }


    byte[] toBytes(final RawItem rawItem) {
        return Metrics.measure("Item toBytes rawItem", () ->
                toBytes(output ->
                        writeRawItem(rawItem, output)));
    }

    RawItem readRawItem(final Input input) {
        return Metrics.measure("Item readRawItem input", () -> {
            try {
                final int groupKeyLength = input.readInt();
                final byte[] key = input.readBytes(groupKeyLength);
                final byte[] generators = input.readAllBytes();
                return new RawItem(key, generators);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    RawItem readRawItem(final byte[] bytes) {
        return Metrics.measure("Item readRawItem bytes", () -> {
            try (final Input input = new Input(bytes)) {
                return readRawItem(input);
            }
        });
    }

    void writeRawItem(final RawItem rawItem, final Output output) {
        Metrics.measure("Item writeRawItem", () -> {
            if (rawItem.getKey() != null) {
                output.writeInt(rawItem.getKey().length);
                output.writeBytes(rawItem.getKey());
            } else {
                output.writeInt(0);
            }

            if (rawItem.getGenerators() != null) {
                output.writeBytes(rawItem.getGenerators());
            }
        });
    }

    byte[] toBytes(final Generator[] generators) {
        return Metrics.measure("Item toBytes", () ->
                toBytes(output ->
                        writeGenerators(generators, output)));
    }

    Generator[] readGenerators(final byte[] bytes) {
        return Metrics.measure("Item readGenerators bytes", () -> {
            try (final Input input = new Input(bytes)) {
                return readGenerators(input);
            }
        });
    }

    Generator[] readGenerators(final Input input) {
        return Metrics.measure("Item readGenerators", () -> {
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
        });
    }

    void writeGenerators(final Generator[] generators, final Output output) {
        Metrics.measure("Item writeGenerators", () -> {
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
        });
    }
}
