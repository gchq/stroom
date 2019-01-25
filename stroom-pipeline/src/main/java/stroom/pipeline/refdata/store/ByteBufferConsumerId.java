package stroom.pipeline.refdata.store;

import java.io.Serializable;
import java.util.Objects;

public class ByteBufferConsumerId implements Serializable {
    private final Integer id;

    public ByteBufferConsumerId(final Integer id) {
        this.id = id;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ByteBufferConsumerId byteBufferConsumerId = (ByteBufferConsumerId) o;
        return Objects.equals(id, byteBufferConsumerId.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return Integer.toString(id);
    }
}
