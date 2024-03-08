package stroom.query.language.functions;

import stroom.query.language.functions.ref.StoredValues;

public interface ChildData {

    StoredValues first();

    StoredValues last();

    StoredValues nth(int pos);

    Iterable<StoredValues> top(int limit);

    Iterable<StoredValues> bottom(int limit);

    long count();
}
