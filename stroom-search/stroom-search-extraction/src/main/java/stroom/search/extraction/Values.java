package stroom.search.extraction;

import stroom.dashboard.expression.v1.Val;

import java.util.Arrays;

public class Values {
    public static final Values COMPLETE = new Values(null);

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

    @Override
    public String toString() {
        if (values != null) {
            return Arrays.toString(values);
        }
        return "complete";
    }
}
