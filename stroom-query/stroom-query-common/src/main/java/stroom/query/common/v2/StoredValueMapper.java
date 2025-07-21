package stroom.query.common.v2;

import stroom.query.language.functions.ref.StoredValues;

import java.util.stream.Stream;

public interface StoredValueMapper {

    Stream<StoredValues> create(StoredValues storedValues);
}
