package stroom.query.common.v2;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferInput;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferOutput;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

class LmdbValue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbValue.class);

    private CompiledField[] fields;
    private ByteBuffer byteBuffer;
    private Key key;
    private Generators generators;

    LmdbValue(final byte[] fullKeyBytes,
              final Generators generators) {
        this.key = new Key(fullKeyBytes);
        this.generators = generators;
    }

    LmdbValue(final CompiledField[] fields,
              final ByteBuffer byteBuffer) {
        this.fields = fields;
        this.byteBuffer = byteBuffer;
    }

    public LmdbValue(final CompiledField[] fields,
                     final byte[] fullKeyBytes,
                     final byte[] generatorBytes) {
        this.fields = fields;
        this.key = new Key(fullKeyBytes);
        this.generators = new Generators(fields, generatorBytes);
    }

    private void unpack() {
        try (final UnsafeByteBufferInput input =
                new UnsafeByteBufferInput(byteBuffer)) {
            final int keyLength = input.readInt();
            key = new Key(input.readBytes(keyLength));
            final int generatorLength = input.readInt();
            generators = new Generators(fields, input.readBytes(generatorLength));
        }
    }

    private void pack() {
        final byte[] keyBytes = getKey().getBytes();
        final byte[] generatorBytes = getGenerators().getBytes();
        final int requiredCapacity = calculateRequiredCapacity(keyBytes, generatorBytes);

        try (final UnsafeByteBufferOutput output = new UnsafeByteBufferOutput(requiredCapacity)) {
            write(output, keyBytes, generatorBytes);
            byteBuffer = output.getByteBuffer().flip();
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private int calculateRequiredCapacity(final byte[] keyBytes, final byte[] generatorBytes) {
        return Integer.BYTES +
                keyBytes.length +
                Integer.BYTES +
                generatorBytes.length;
    }

    static LmdbValue read(final CompiledField[] fields, final Input input) {
        final int fullKeyLength = input.readInt();
        final byte[] fullKey = input.readBytes(fullKeyLength);
        final int generatorsLength = input.readInt();
        final byte[] generatorBytes = input.readBytes(generatorsLength);
        return new LmdbValue(fields, fullKey, generatorBytes);
    }

    void write(final Output output) throws IOException {
        write(output, getKey().getBytes(), getGenerators().getBytes());
    }

    private void write(final Output output, final byte[] fullKeyBytes, final byte[] generatorBytes) throws IOException {
        final int requiredCapacity = calculateRequiredCapacity(fullKeyBytes, generatorBytes);
        final long remaining = output.getMaxCapacity() - output.total();
        if (remaining < requiredCapacity) {
            LOGGER.debug(() -> "Row size exceeds capacity: " +
                    Arrays.toString(fullKeyBytes) +
                    " " +
                    Arrays.toString(generatorBytes));
            throw new IOException("Row size exceeds capacity");
        }

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
                unpack();
            } else {
                throw new NullPointerException("Unable to get key bytes");
            }
        }
        return key;
    }

    Generators getGenerators() {
        if (generators == null) {
            if (byteBuffer != null) {
                unpack();
            } else {
                throw new NullPointerException("Unable to get generator bytes");
            }
        }
        return generators;
    }

    @Override
    public String toString() {
        final Key key = getKey();
        final Generators generators = getGenerators();

        return "LmdbValue{" +
                "key=" + key +
                ", " +
                "generators=" +
                generators +
                "}";
    }
}
