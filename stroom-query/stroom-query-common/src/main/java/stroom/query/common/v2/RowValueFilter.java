/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.query.common.v2;

import stroom.query.api.v2.Column;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;

import java.util.List;
import java.util.Optional;

public class RowValueFilter {

    public static Optional<ExpressionOperator> create(final List<Column> columns) {
        final ExpressionOperator.Builder valueFilterBuilder = ExpressionOperator.builder();
        columns.forEach(column -> {
            if (column.getColumnFilter() != null) {
                final Optional<ExpressionOperator> operator = SimpleStringExpressionParser.create(
                        new SingleFieldProvider(column.getId()),
                        column.getColumnFilter().getFilter(),
                        column.getColumnFilter().isCaseSensitive());
                operator.ifPresent(valueFilterBuilder::addOperator);
            }
        });
        final ExpressionOperator valueFilter = valueFilterBuilder.build();
        if (!ExpressionUtil.hasTerms(valueFilter)) {
            return Optional.empty();
        }

        return Optional.of(valueFilter);
    }
}
