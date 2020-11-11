package stroom.dashboard.expression.v1;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public final class GroupKeySerialiser {
    public static GroupKey read(final Input input) {
        final byte depth = input.readByte();
        final Val[] values = ValSerialiser.readArray(input);
        final boolean hasParent = input.readBoolean();
        GroupKey parent = null;
        if (hasParent) {
            parent = read(input);
        }
        return new GroupKey(depth, parent, values);
    }

    public static void write(final Output output, final GroupKey key) {
        if (key.getDepth() > Byte.MAX_VALUE) {
            throw new RuntimeException("Max depth allowed is " + Byte.MAX_VALUE);
        }
        output.writeByte(key.getDepth());
        ValSerialiser.writeArray(output, key.getValues());
        if (key.getParent() != null) {
            output.writeBoolean(true);
            write(output, key.getParent());
        } else {
            output.writeBoolean(false);
        }
    }
}
