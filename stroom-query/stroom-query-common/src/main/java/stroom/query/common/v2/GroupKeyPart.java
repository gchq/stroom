package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValSerialiser;

import com.esotericsoftware.kryo.io.Output;

import java.util.Arrays;

class GroupKeyPart implements KeyPart {

    private final Val[] groupValues;

    public GroupKeyPart(final Val[] groupValues) {
        this.groupValues = groupValues;
    }

    @Override
    public void write(final Output output) {
        ValSerialiser.writeArray(output, groupValues);
    }

    @Override
    public boolean isGrouped() {
        return true;
    }

    @Override
    public Val[] getGroupValues() {
        return groupValues;
    }

    @SuppressWarnings("checkstyle:needbraces")
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final GroupKeyPart that = (GroupKeyPart) o;
        return Arrays.equals(groupValues, that.groupValues);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(groupValues);
    }

    @Override
    public void append(final StringBuilder sb) {
        for (int i = 0; i < groupValues.length; i++) {
            final Val val = groupValues[i];
            if (i > 0) {
                sb.append("|");
            }
            if (val != null) {
                sb.append(val.toString());
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        append(sb);
        return sb.toString();
    }
}
