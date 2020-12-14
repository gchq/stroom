package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Generator;

public class UnpackedItem {
    private final RawKey rawKey;
    private final Generator[] generators;
    private final byte[] bytes;

    public UnpackedItem(final RawKey rawKey, final Generator[] generators, final byte[] bytes) {
        this.rawKey = rawKey;
        this.generators = generators;
        this.bytes = bytes;
    }

    public RawKey getRawKey() {
        return rawKey;
    }

    public Generator[] getGenerators() {
        return generators;
    }

    public byte[] getBytes() {
        return bytes;
    }
}
