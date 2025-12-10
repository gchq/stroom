/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
