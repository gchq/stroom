package stroom.planb.impl.serde.hash;

import stroom.planb.shared.HashLength;

public class HashFactoryFactory {
    public static HashFactory create(final HashLength hashLength) {
        if (hashLength == null || HashLength.LONG.equals(hashLength)) {
            return new LongHashFactory();
        }
        return new IntegerHashFactory();
    }
}
