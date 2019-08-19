package stroom.search.extraction;

import stroom.dashboard.expression.v1.Val;

public class Values {
    private final Val[] values;

    public Values(final Val[] values) {
        this.values = values;
    }

    public Val[] getValues() {
        return values;
    }

    public boolean complete() {
        return values == null;
    }
}
