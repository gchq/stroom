package stroom.query.common.v2;

import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.StoredValues;

public interface StoredValueKeyFactory {

    Val[] getGroupValues(int depth, StoredValues storedValues);

    long getGroupHash(int depth, StoredValues storedValues);

    long hash(Val[] groupValues);

    long getTimeMs(StoredValues storedValues);
}
