package stroom.dashboard.expression.v1;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public final class GroupKeySerialiser {
    public static GroupKey read(final Input input) {
        // Read the parent values.
        final int parentLength = input.readInt();
        byte[] parentBytes = null;
        if (parentLength != 0) {
            parentBytes = input.readBytes(parentLength);
        }

        final int valuesLength = input.readInt();
        final byte[] valuesBytes = input.readBytes(valuesLength);

        return new GroupKey(parentBytes, valuesBytes);
    }

    public static void write(final Output output, final GroupKey key) {
        // Write the parent values.
        if (key.getParent() != null) {
            output.writeInt(key.getParent().length);
            output.writeBytes(key.getParent());
        } else {
            output.writeInt(0);
        }

        // Write the values.
        output.writeInt(key.getValues().length);
        output.writeBytes(key.getValues());
    }

    public static byte[] toBytes(final GroupKey groupKey) {
        if (groupKey == null) {
            return null;
        }

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (final Output output = new Output(byteArrayOutputStream)) {
            write(output, groupKey);
        }
        return byteArrayOutputStream.toByteArray();
    }

    public static GroupKey toGroupKey(final byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        try (final Input input = new Input(new ByteArrayInputStream(bytes))) {
            return read(input);
        }
    }
}
