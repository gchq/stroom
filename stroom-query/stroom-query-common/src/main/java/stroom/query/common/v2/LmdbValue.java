package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Generator;
import stroom.util.io.ByteSizeUnit;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferInput;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferOutput;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.Collectors;

class LmdbValue {

    private static final int MIN_VALUE_SIZE = (int) ByteSizeUnit.KIBIBYTE.longBytes(1);
    private static final int MAX_VALUE_SIZE = (int) ByteSizeUnit.MEBIBYTE.longBytes(1);

    private final ItemSerialiser itemSerialiser;
    private ByteBuffer byteBuffer;
    private Key key;
    private byte[] generatorBytes;
    private Generator[] generators;

    LmdbValue(final ItemSerialiser itemSerialiser,
              final byte[] fullKeyBytes,
              final Generator[] generators) {
        this.itemSerialiser = itemSerialiser;
        this.key = new Key(fullKeyBytes);
        this.generators = generators;
    }

    LmdbValue(final ItemSerialiser itemSerialiser,
              final ByteBuffer byteBuffer) {
        this.itemSerialiser = itemSerialiser;
        this.byteBuffer = byteBuffer;
    }

    public LmdbValue(final ItemSerialiser itemSerialiser,
                     final byte[] fullKeyBytes,
                     final byte[] generatorBytes) {
        this.itemSerialiser = itemSerialiser;
        this.key = new Key(fullKeyBytes);
        this.generatorBytes = generatorBytes;
    }

    private void split() {
        try (final UnsafeByteBufferInput input =
                new UnsafeByteBufferInput(byteBuffer)) {
            final int keyLength = input.readInt();
            key = new Key(input.readBytes(keyLength));
            final int generatorLength = input.readInt();
            generatorBytes = input.readBytes(generatorLength);
        }
    }

    private void pack() {
        try (final UnsafeByteBufferOutput output =
                new UnsafeByteBufferOutput(MIN_VALUE_SIZE, MAX_VALUE_SIZE)) {
            write(output, getKey().getBytes(), getGeneratorBytes());
            byteBuffer = output.getByteBuffer().flip();
        }
    }

    static LmdbValue read(final ItemSerialiser itemSerialiser, final Input input) {
        final int fullKeyLength = input.readInt();
        final byte[] fullKey = input.readBytes(fullKeyLength);
        final int generatorsLength = input.readInt();
        final byte[] generatorBytes = input.readBytes(generatorsLength);
        return new LmdbValue(itemSerialiser, fullKey, generatorBytes);
    }

    void write(final Output output) {
        write(output, getKey().getBytes(), getGeneratorBytes());
    }

    private void write(final Output output, final byte[] fullKeyBytes, final byte[] generatorBytes) {
        output.writeInt(fullKeyBytes.length);
        output.writeBytes(fullKeyBytes, 0, fullKeyBytes.length);
        output.writeInt(generatorBytes.length);
        output.writeBytes(generatorBytes);
    }

    ByteBuffer getByteBuffer() {
        if (byteBuffer == null) {
            pack();
        }
        return byteBuffer;
    }

    Key getKey() {
        if (key == null) {
            if (byteBuffer != null) {
                split();
            } else {
                throw new NullPointerException("Unable to get key bytes");
            }
        }
        return key;
    }

    private byte[] getGeneratorBytes() {
        if (generatorBytes == null) {
            if (byteBuffer != null) {
                split();
            } else if (generators != null) {
                generatorBytes = itemSerialiser.toBytes(generators);
            } else {
                throw new NullPointerException("Unable to get generator bytes");
            }
        }
        return generatorBytes;
    }

    Generator[] getGenerators() {
        if (generators == null) {
            generators = itemSerialiser.readGenerators(getGeneratorBytes());
        }
        return generators;
    }

    @Override
    public String toString() {
        final Key key = getKey();
        final Generator[] generators = getGenerators();

        return "LmdbValue{" +
                "key=" + key +
                ", " +
                "generators=[" +
                Arrays
                        .stream(generators)
                        .map(generator -> {
                            if (generator == null) {
                                return "null";
                            }
                            return generator.eval().toString();
                        })
                        .collect(Collectors.joining(",")) +
                "]}";
    }
}
