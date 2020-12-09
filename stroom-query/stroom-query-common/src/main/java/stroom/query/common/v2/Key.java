package stroom.query.common.v2;

import stroom.dashboard.expression.v1.ValSerialiser;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Key implements Iterable<KeyPart> {
    private final List<KeyPart> keyParts;

    public Key(final List<KeyPart> keyParts) {
        this.keyParts = keyParts;
    }

    static Key read(final Input input) {
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
    }

    KeyPart getLast() {
        if (keyParts.size() > 0) {
            return keyParts.get(keyParts.size() - 1);
        }
        return null;
    }

    private byte[] toBytes() {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (final Output output = new Output(byteArrayOutputStream)) {
            write(output);
        }
        return byteArrayOutputStream.toByteArray();
    }

    RawKey toRawKey() {
        return new RawKey(toBytes());
    }

    Key getParent() {
        if (keyParts.size() > 0) {
            return new Key(keyParts.subList(0, keyParts.size() - 1));
        }
        return null;
    }

    int getDepth() {
        return keyParts.size() - 1;
    }

    int size() {
        return keyParts.size();
    }

    private void write(final Output output) {
        output.writeInt(keyParts.size());
        for (final KeyPart keyPart : keyParts) {
            output.writeBoolean(keyPart.isGrouped());
            keyPart.write(output);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keyParts.size(); i++) {
            final KeyPart keyPart = keyParts.get(i);
            if (i > 0) {
                sb.append("/");
            }
            keyPart.append(sb);
        }
        return sb.toString();
    }

    @Override
    @Nonnull
    public Iterator<KeyPart> iterator() {
        return keyParts.iterator();
    }
}
