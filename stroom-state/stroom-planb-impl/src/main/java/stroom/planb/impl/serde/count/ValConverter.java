package stroom.planb.impl.serde.count;

import stroom.planb.impl.serde.temporalkey.TemporalKey;
import stroom.query.language.functions.Val;

public interface ValConverter<V> {

    Val convert(TemporalKey key, V v);
}
