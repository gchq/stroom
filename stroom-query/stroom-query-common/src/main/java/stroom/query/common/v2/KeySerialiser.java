package stroom.query.common.v2;

import stroom.dashboard.expression.v1.ValSerialiser;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class KeySerialiser {
    static Key read(final Input input) {
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
            return new Key(list);
        });
    }

    static void write(final Key key, final Output output) {
        Metrics.measure("Key write", () -> {
            output.writeInt(key.size());
            for (final KeyPart keyPart : key) {
                output.writeBoolean(keyPart.isGrouped());
                keyPart.write(output);
            }
        });
    }

    static byte[] toBytes(final Key key) {
        return Metrics.measure("Key toBytes", () -> {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            try (final Output output = new Output(byteArrayOutputStream)) {
                write(key, output);
            }
            return byteArrayOutputStream.toByteArray();
        });
    }

    static RawKey toRawKey(final Key key) {
        return Metrics.measure("Key toRawKey", () -> {
            return new RawKey(toBytes(key));
        });
    }

    static Key toKey(final RawKey rawKey) {
        return Metrics.measure("Key toKey (rawKey)", () -> {
            return toKey(rawKey.getBytes());
        });
    }

    static Key toKey(final byte[] bytes) {
        return Metrics.measure("Key toKey (bytes)", () -> {
            try (final Input input = new Input(new ByteArrayInputStream(bytes))) {
                return read(input);
            }
        });
    }
}
