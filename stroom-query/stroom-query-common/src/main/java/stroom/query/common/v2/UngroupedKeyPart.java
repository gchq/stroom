package stroom.query.common.v2;

import com.esotericsoftware.kryo.io.Output;

import java.util.Objects;

class UngroupedKeyPart implements KeyPart {

    private final String uuid;

    public UngroupedKeyPart(final String uuid) {
        this.uuid = uuid;
    }

    @Override
    public void write(final Output output) {
        output.writeString(uuid);
    }

    @Override
    public boolean isGrouped() {
        return false;
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
        final UngroupedKeyPart that = (UngroupedKeyPart) o;
        return uuid == that.uuid;
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    @Override
    public void append(final StringBuilder sb) {
        sb.append("~");
        sb.append(uuid);
    }

    @Override
    public String toString() {
        return "";
    }
}
