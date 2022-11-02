package stroom.query.common.v2;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferInput;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferOutput;

import java.io.IOException;
import java.nio.ByteBuffer;

class LmdbValue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbValue.class);

    private final Serialisers serialisers;
    private CompiledField[] fields;
    private ByteBuffer byteBuffer;
    private Key key;
    private Generators generators;

    LmdbValue(final Serialisers serialisers,
              final byte[] fullKeyBytes,
              final Generators generators) {
        this.serialisers = serialisers;
        this.key = new Key(serialisers, fullKeyBytes);
        this.generators = generators;
    }

    LmdbValue(final Serialisers serialisers,
              final CompiledField[] fields,
              final ByteBuffer byteBuffer) {
        this.serialisers = serialisers;
        this.fields = fields;
        this.byteBuffer = byteBuffer;
    }

    public LmdbValue(final Serialisers serialisers,
                     final CompiledField[] fields,
                     final byte[] fullKeyBytes,
                     final byte[] generatorBytes) {
        this.serialisers = serialisers;
        this.fields = fields;
        this.key = new Key(serialisers, fullKeyBytes);
        this.generators = new Generators(serialisers, fields, generatorBytes);
    }

    private void unpack() {
        try (final UnsafeByteBufferInput input =
                serialisers.getInputFactory().createByteBufferInput(byteBuffer)) {
            final int keyLength = input.readInt();
            key = new Key(serialisers, input.readBytes(keyLength));
            final int generatorLength = input.readInt();
            generators = new Generators(serialisers, fields, input.readBytes(generatorLength));
        }
    }

    private void pack() {
        final byte[] keyBytes = getKey().getBytes();
        final byte[] generatorBytes = getGenerators().getBytes();
        final int requiredCapacity = calculateRequiredCapacity(keyBytes, generatorBytes);

        try (final UnsafeByteBufferOutput output =
                serialisers.getOutputFactory().createByteBufferOutput(requiredCapacity)) {
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

    static LmdbValue read(final Serialisers serialisers,
                          final CompiledField[] fields,
                          final Input input) {
        final int fullKeyLength = input.readInt();
        final byte[] fullKey = input.readBytes(fullKeyLength);
        final int generatorsLength = input.readInt();
        final byte[] generatorBytes = input.readBytes(generatorsLength);
        return new LmdbValue(serialisers, fields, fullKey, generatorBytes);
    }

    void write(final Output output) throws IOException {
        write(output, getKey().getBytes(), getGenerators().getBytes());
    }

    private void write(final Output output, final byte[] fullKeyBytes, final byte[] generatorBytes) throws IOException {
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
