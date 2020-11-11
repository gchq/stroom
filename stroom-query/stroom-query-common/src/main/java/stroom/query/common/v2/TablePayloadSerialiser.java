package stroom.query.common.v2;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

public class TablePayloadSerialiser {
    private final ItemSerialiser itemSerialiser;

    public TablePayloadSerialiser(final CompiledFields compiledFields) {
        itemSerialiser = new ItemSerialiser(compiledFields);
    }

    public byte[] fromQueue(final Item[] items) {
        byte[] data;
        try (final ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            try (final Output output = new Output(outputStream)) {
                itemSerialiser.writeArray(output, items);
            }

            outputStream.flush();
            data = outputStream.toByteArray();
//            final ByteBuffer byteBuffer = outputStream.getPooledByteBuffer().getByteBuffer();
//            byteBuffer.flip();
//
//            data = new byte[byteBuffer.remaining()];
//            byteBuffer.get(data);

        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        return data;
    }

    public Item[] toQueue(final byte[] data) {
        try (final Input input = new Input(new ByteArrayInputStream(data))) {
            return itemSerialiser.readArray(input);
        }
    }

    public TablePayload getPayload(final CoprocessorKey coprocessorKey, final Item[] items) {
        final byte[] data = fromQueue(items);
        return new TablePayload(coprocessorKey, data);
    }
}
