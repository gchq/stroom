package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValSerialiser;
import stroom.util.io.ByteSizeUnit;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.annotation.Nonnull;

public class Key implements Iterable<KeyPart> {

    private static final int MIN_KEY_SIZE = (int) ByteSizeUnit.BYTE.longBytes(10);
    private static final int MAX_KEY_SIZE = (int) ByteSizeUnit.MEBIBYTE.longBytes(1);
    private static final Key ROOT_KEY = new Key(Collections.emptyList());

    private byte[] bytes;
    private int hashCode;
    private List<KeyPart> keyParts;

    public Key(final byte[] bytes) {
        this.bytes = bytes;
        this.hashCode = Arrays.hashCode(bytes);
    }

    public Key(final List<KeyPart> keyParts) {
        this.keyParts = keyParts;
    }

    public static Key root() {
        return ROOT_KEY;
    }

    public byte[] getBytes() {
        if (bytes == null) {
            Metrics.measure("Key getBytes", () -> {
                try (final Output output = new Output(MIN_KEY_SIZE, MAX_KEY_SIZE)) {
                    output.writeInt(keyParts.size());
                    for (final KeyPart keyPart : keyParts) {
                        output.writeBoolean(keyPart.isGrouped());
                        keyPart.write(output);
                    }
                    output.flush();
                    bytes = output.toBytes();
                    hashCode = Arrays.hashCode(bytes);
                }
            });
        }
        return bytes;
    }

    private List<KeyPart> getKeyParts() {
        if (keyParts == null) {
            Metrics.measure("Key getKeyParts", () -> {
                try (final Input input = new Input(bytes)) {
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
                    keyParts = list;
                }
            });
        }
        return keyParts;
    }

    Key resolve(final Val[] groupValues) {
        return resolve(new GroupKeyPart(groupValues));
    }

    Key resolve(final long uniqueId) {
        return resolve(new UngroupedKeyPart(uniqueId));
    }

    private Key resolve(final KeyPart keyPart) {
        final List<KeyPart> keyParts = getKeyParts();
        final List<KeyPart> parts = new ArrayList<>(keyParts.size() + 1);
        parts.addAll(keyParts);
        parts.add(keyPart);
        return new Key(parts);
    }

    Key getParent() {
        final List<KeyPart> keyParts = getKeyParts();
        if (keyParts.size() > 0) {
            return new Key(keyParts.subList(0, keyParts.size() - 1));
        }
        return null;
    }

    int size() {
        final List<KeyPart> keyParts = getKeyParts();
        return keyParts.size();
    }

    boolean isGrouped() {
        final KeyPart last = getLast();
        return last == null || last.isGrouped();
    }

    private KeyPart getLast() {
        final List<KeyPart> keyParts = getKeyParts();
        if (keyParts.size() > 0) {
            return keyParts.get(keyParts.size() - 1);
        }
        return null;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final Key key = (Key) o;
        return Arrays.equals(getBytes(), key.getBytes());
    }

    @Override
    public int hashCode() {
        getBytes();
        return hashCode;
    }

    @Override
    public String toString() {
        final List<KeyPart> keyParts = getKeyParts();
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
        final List<KeyPart> keyParts = getKeyParts();
        return keyParts.iterator();
    }
}
