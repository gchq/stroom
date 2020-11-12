package stroom.dashboard.expression.v1;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

public final class GroupKeySerialiser {
    public static GroupKey read(final Input input) {
        // Read depth.
        final int depth = input.readByteUnsigned();

        // Read values.
        final Val[] values = ValSerialiser.readArray(input);

        final boolean hasParent = input.readBoolean();
        GroupKey parent = null;
        if (hasParent) {
            parent = read(input);
        }
        return new GroupKey(depth, parent, values);
    }

    public static void write(final Output output, final GroupKey key) {
        // Write depth.
        if (key.getDepth() > 255) {
            throw new RuntimeException("Max depth allowed is " + 255);
        }
        output.writeByte(key.getDepth());

        // Write the group values.
        ValSerialiser.writeArray(output, key.getValues());

        if (key.getParent() != null) {
            // Write a boolean to indicate we have parents.
            output.writeBoolean(true);

            // Write the parents.
            write(output, key.getParent());
        } else {
            // Write a boolean to indicate we don't have parents.
            output.writeBoolean(false);
        }
    }
}
