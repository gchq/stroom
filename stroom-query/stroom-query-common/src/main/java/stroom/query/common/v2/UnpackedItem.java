package stroom.query.common.v2;

import stroom.dashboard.expression.v1.Generator;

public class UnpackedItem implements HasGenerators {

    private final Key key;
    private final Generator[] generators;
    private final byte[] bytes;

    public UnpackedItem(final Key key, final Generator[] generators, final byte[] bytes) {
        this.key = key;
        this.generators = generators;
        this.bytes = bytes;
    }

    public Key getKey() {
        return key;
    }

    @Override
    public Generator[] getGenerators() {
        return generators;
    }

    public byte[] getBytes() {
        return bytes;
    }
}
