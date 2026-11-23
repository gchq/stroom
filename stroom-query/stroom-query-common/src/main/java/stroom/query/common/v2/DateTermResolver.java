/*
 * Copyright 2026 Crown Copyright
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

import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.util.date.DateUtil;
import stroom.util.shared.NullSafe;

import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/// Turns the values of an expression's date terms, such as `day()-1d`, into absolute UTC dates.
///
/// This is for data sources whose queries are built by code that isn't given the date time settings of the
/// search. It lets them follow the time zone and reference time of the search as every other data source
/// does. The words of a dictionary are not resolved, as the term only names the dictionary.
public final class DateTermResolver {

    private static final String DELIMITER = ",";

    private DateTermResolver() {
        // Static methods only.
    }

    /// Resolves the date terms of an expression.
    ///
    /// @param expression       The expression, or `null`.
    /// @param fields           The fields of the data source.
    /// @param dateTimeSettings The date time settings of the search, or `null`.
    /// @return A copy of the expression with its date terms resolved. A value that can't be read is left as
    /// it is, for whatever reads it next to report.
    public static ExpressionOperator resolve(final ExpressionOperator expression,
                                             final Collection<QueryField> fields,
                                             final DateTimeSettings dateTimeSettings) {
        if (expression == null || dateTimeSettings == null) {
            return expression;
        }
        final Set<String> dateFields = fields.stream()
                .filter(field -> FieldType.DATE.equals(field.getFldType()))
                .map(QueryField::getFldName)
                .collect(Collectors.toSet());
        return resolveOperator(expression, dateFields, dateTimeSettings);
    }

    private static ExpressionOperator resolveOperator(final ExpressionOperator operator,
                                                      final Set<String> dateFields,
                                                      final DateTimeSettings dateTimeSettings) {
        final List<ExpressionItem> children = new ArrayList<>();
        for (final ExpressionItem child : NullSafe.list(operator.getChildren())) {
            if (child instanceof final ExpressionOperator childOperator) {
                children.add(resolveOperator(childOperator, dateFields, dateTimeSettings));
            } else if (child instanceof final ExpressionTerm term
                       && dateFields.contains(term.getField())
                       && !NullSafe.isBlankString(term.getValue())) {
                children.add(term.copy()
                        .value(resolveValue(term.getValue(), dateTimeSettings))
                        .build());
            } else {
                children.add(child);
            }
        }
        return operator.copy().children(children).build();
    }

    // A value is one date or, for conditions such as between and in, a list of them.
    private static String resolveValue(final String value, final DateTimeSettings dateTimeSettings) {
        final List<String> dates = new ArrayList<>();
        for (final String part : value.split(DELIMITER)) {
            try {
                dates.add(DateExpressionParser.parse(part.trim(), dateTimeSettings)
                        .map(date -> DateUtil.createNormalDateTimeString(date.toInstant()))
                        .orElse(part));
            } catch (final DateTimeException | ArithmeticException e) {
                return value;
            }
        }
        return String.join(DELIMITER, dates);
    }
}
