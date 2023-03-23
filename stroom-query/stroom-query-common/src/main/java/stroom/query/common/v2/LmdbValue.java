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
    private final KeyFactory keyFactory;
    private CompiledField[] fields;
    private ByteBuffer byteBuffer;
    private Key key;
    private Generators generators;
    private byte[] fullKeyBytes;
    private byte[] generatorBytes;

    LmdbValue(final Serialisers serialisers,
              final KeyFactory keyFactory,
              final Key key,
              final Generators generators) {
        this.serialisers = serialisers;
        this.keyFactory = keyFactory;
        this.key = key;
        this.generators = generators;
    }

    LmdbValue(final Serialisers serialisers,
              final KeyFactory keyFactory,
              final CompiledField[] fields,
              final ByteBuffer byteBuffer) {
        this.serialisers = serialisers;
        this.keyFactory = keyFactory;
        this.fields = fields;
        this.byteBuffer = byteBuffer;
    }

    public LmdbValue(final Serialisers serialisers,
                     final KeyFactory keyFactory,
                     final CompiledField[] fields,
                     final byte[] fullKeyBytes,
                     final byte[] generatorBytes) {
        this.serialisers = serialisers;
        this.keyFactory = keyFactory;
        this.fields = fields;
        this.fullKeyBytes = fullKeyBytes;
        this.key = keyFactory.create(fullKeyBytes);
        this.generators = new Generators(serialisers, fields, generatorBytes);
    }

    public byte[] getFullKeyBytes(final ErrorConsumer errorConsumer) {
        if (fullKeyBytes == null) {
            if (byteBuffer != null) {
                try (final UnsafeByteBufferInput input =
                        serialisers.getInputFactory().createByteBufferInput(byteBuffer)) {
                    final int keyLength = input.readInt();
                    fullKeyBytes = input.readBytes(keyLength);
                }
                byteBuffer.rewind();
            } else {
                fullKeyBytes = keyFactory.getBytes(getKey(), errorConsumer);
            }
        }
        return fullKeyBytes;
    }

    public byte[] getGeneratorBytes(final ErrorConsumer errorConsumer) {
        if (generatorBytes == null) {
            if (byteBuffer != null) {
                unpack();
            } else {
                generatorBytes = getGenerators().getBytes(errorConsumer);
            }
        }
        return generatorBytes;
    }

    private void unpack() {
        try (final UnsafeByteBufferInput input =
                serialisers.getInputFactory().createByteBufferInput(byteBuffer)) {
            final int keyLength = input.readInt();
            fullKeyBytes = input.readBytes(keyLength);
            key = keyFactory.create(fullKeyBytes);
            final int generatorLength = input.readInt();
            generatorBytes = input.readBytes(generatorLength);
            generators = new Generators(serialisers, fields, generatorBytes);
        }
        byteBuffer.rewind();
    }

    private void pack(final ErrorConsumer errorConsumer) {
        final byte[] generatorBytes = getGeneratorBytes(errorConsumer);
        final byte[] keyBytes = getFullKeyBytes(errorConsumer);
        final int requiredCapacity = calculateRequiredCapacity(keyBytes, generatorBytes);

        try (final UnsafeByteBufferOutput output =
                serialisers.getOutputFactory().createByteBufferOutput(requiredCapacity, errorConsumer)) {
            write(output, keyBytes, generatorBytes);
            output.flush();
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
                          final KeyFactory keyFactory,
                          final CompiledField[] fields,
                          final Input input) {
        final int fullKeyLength = input.readInt();
        final byte[] fullKey = input.readBytes(fullKeyLength);
        final int generatorsLength = input.readInt();
        final byte[] generatorBytes = input.readBytes(generatorsLength);
        return new LmdbValue(serialisers, keyFactory, fields, fullKey, generatorBytes);
    }

    void write(final Output output, final ErrorConsumer errorConsumer) throws IOException {
        write(output, keyFactory.getBytes(getKey(), errorConsumer), getGenerators().getBytes(errorConsumer));
    }

    private void write(final Output output, final byte[] fullKeyBytes, final byte[] generatorBytes) throws IOException {
        output.writeInt(fullKeyBytes.length);
        output.writeBytes(fullKeyBytes, 0, fullKeyBytes.length);
        output.writeInt(generatorBytes.length);
        output.writeBytes(generatorBytes);
    }

    ByteBuffer getByteBuffer(final ErrorConsumer errorConsumer) {
        if (byteBuffer == null) {
            pack(errorConsumer);
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
