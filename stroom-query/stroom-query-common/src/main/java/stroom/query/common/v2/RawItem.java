package stroom.query.common.v2;

public class RawItem {
    private final RawKey groupKey;
    private final byte[] generators;

    public RawItem(final RawKey groupKey,
                   final byte[] generators) {
        this.groupKey = groupKey;
        this.generators = generators;
    }

    public RawKey getGroupKey() {
        return groupKey;
    }

    public byte[] getGenerators() {
        return generators;
    }
}