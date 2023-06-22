package stroom.dashboard.expression.v1;

import stroom.dashboard.expression.v1.ref.StoredValues;

public interface ChildData {

    StoredValues first();

    StoredValues last();

    StoredValues nth(int pos);

    Iterable<StoredValues> top(int limit);

    Iterable<StoredValues> bottom(int limit);

    long count();
}
