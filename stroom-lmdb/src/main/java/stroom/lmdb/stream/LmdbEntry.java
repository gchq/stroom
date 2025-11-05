package stroom.lmdb.stream;

import java.nio.ByteBuffer;

public class LmdbEntry {

    ByteBuffer key;
    ByteBuffer val;

    LmdbEntry() {
        // Not externally creatable.
    }

    public ByteBuffer getKey() {
        return key;
    }

    void setKey(final ByteBuffer key) {
        this.key = key;
    }

    public ByteBuffer getVal() {
        return val;
    }

    void setVal(final ByteBuffer val) {
        this.val = val;
    }
}
