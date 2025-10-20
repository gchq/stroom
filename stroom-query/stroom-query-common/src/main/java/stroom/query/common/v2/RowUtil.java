package stroom.query.common.v2;

import stroom.query.api.Column;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactories;
import stroom.query.common.v2.ExpressionPredicateFactory.ValueFunctionFactory;
import stroom.query.common.v2.format.Formatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.query.language.functions.Values;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RowUtil {

    static Formatter[] createFormatters(final List<Column> newColumns,
                                        final FormatterFactory formatterFactory) {
        final Formatter[] formatters = new Formatter[newColumns.size()];
        for (int i = 0; i < newColumns.size(); i++) {
            final Column column = newColumns.get(i);
            formatters[i] = formatterFactory.create(column);
        }
        return formatters;
    }

    public static ValueFunctionFactories<Values> createColumnNameValExtractor(final List<Column> newColumns) {
        // Create the field position map for the new columns.
        final Map<String, ValueFunctionFactory<Values>> fieldPositionMap = new HashMap<>();
        for (int i = 0; i < newColumns.size(); i++) {
            final Column column = newColumns.get(i);
            fieldPositionMap.put(column.getName(), new ValuesFunctionFactory(column, i));
        }
        return fieldPositionMap::get;
    }
}
