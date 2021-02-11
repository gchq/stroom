package stroom.query.common.v2;

public class RawItem {

    private final byte[] key;
    private final byte[] generators;

    public RawItem(final byte[] key,
                   final byte[] generators) {
        this.key = key;
        this.generators = generators;
    }

    public byte[] getKey() {
        return key;
    }

    public byte[] getGenerators() {
        return generators;
    }
}
