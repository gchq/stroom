package stroom.query.common.v2;

import stroom.util.io.ByteSizeUnit;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferInput;
import com.esotericsoftware.kryo.unsafe.UnsafeByteBufferOutput;

import java.nio.ByteBuffer;

class LmdbValue {

    private static final int MIN_VALUE_SIZE = (int) ByteSizeUnit.KIBIBYTE.longBytes(1);
    private static final int MAX_VALUE_SIZE = (int) ByteSizeUnit.MEBIBYTE.longBytes(1);

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

    private void split() {
        try (final UnsafeByteBufferInput input =
                new UnsafeByteBufferInput(byteBuffer)) {
            final int keyLength = input.readInt();
            key = new Key(input.readBytes(keyLength));
            final int generatorLength = input.readInt();
            generators = new Generators(fields, input.readBytes(generatorLength));
        }
    }

    private void pack() {
        try (final UnsafeByteBufferOutput output =
                new UnsafeByteBufferOutput(MIN_VALUE_SIZE, MAX_VALUE_SIZE)) {
            write(output, getKey().getBytes(), getGenerators().getBytes());
            byteBuffer = output.getByteBuffer().flip();
        }
    }

    static LmdbValue read(final CompiledField[] fields, final Input input) {
        final int fullKeyLength = input.readInt();
        final byte[] fullKey = input.readBytes(fullKeyLength);
        final int generatorsLength = input.readInt();
        final byte[] generatorBytes = input.readBytes(generatorsLength);
        return new LmdbValue(fields, fullKey, generatorBytes);
    }

    void write(final Output output) {
        write(output, getKey().getBytes(), getGenerators().getBytes());
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

//    private byte[] getGeneratorBytes() {
//        if (generators == null) {
//            if (byteBuffer != null) {
//                split();
//            } else if (generators != null) {
//                generatorBytes = generators.toBytes();
//            } else {
//                throw new NullPointerException("Unable to get generator bytes");
//            }
//        }
//        return generatorBytes;
//    }

    Generators getGenerators() {
        if (generators == null) {
            if (byteBuffer != null) {
                split();
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
