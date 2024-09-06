package stroom.query.common.v2;

import stroom.query.api.v2.Column;
import stroom.query.api.v2.Filter;

record ColumnKey(String expression, Filter filter, Integer group) {
    public static ColumnKey create(final Column column) {
        return new ColumnKey(column.getExpression(), column.getFilter(), column.getGroup());
    }
}
