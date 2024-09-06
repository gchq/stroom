package stroom.query.common.v2;

import stroom.query.api.v2.Column;

import java.util.function.Function;

public class ColumnFunction implements Function<Item, String> {
    final Column column;

    public ColumnFunction(final Column column) {
        this.column = column;
    }

    @Override
    public String apply(final Item item) {
        return null;
    }
}
