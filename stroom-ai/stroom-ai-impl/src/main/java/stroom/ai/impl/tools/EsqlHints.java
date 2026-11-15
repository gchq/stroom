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

package stroom.ai.impl.tools;

import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.util.shared.NullSafe;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/// What the tools say to steer an agent to esql_query where Elasticsearch can answer a question itself: an example
/// pipeline built from an index's own fields (describe_data_source), and the names in ES|QL of Stroom's fields.
final class EsqlHints {

    private static final Pattern PLAIN_NAME = Pattern.compile("[A-Za-z_@][A-Za-z0-9_@.]*");
    private static final Set<String> NOT_MEASURES = Set.of("StreamId", "EventId");
    private static final Set<FieldType> NUMBERS = Set.of(FieldType.INTEGER, FieldType.LONG, FieldType.FLOAT,
            FieldType.DOUBLE);

    private EsqlHints() {
        // Static only.
    }

    /// An example aggregation over an index: a count (and a sum of a number, where the index has one) by a keyword
    /// field, the most first, top 10.
    ///
    /// @param fields The index's fields, as Stroom lists them.
    /// @return The pipeline, or empty if the index has no keyword field to group by.
    static Optional<String> examplePipeline(final List<QueryField> fields) {
        final Optional<String> group = NullSafe.list(fields).stream()
                .filter(field -> field.getFldType() == FieldType.KEYWORD && field.queryable())
                .map(QueryField::getFldName)
                .filter(name -> name != null && !name.startsWith("_") && !NOT_MEASURES.contains(name))
                .findFirst();
        if (group.isEmpty()) {
            return Optional.empty();
        }
        final Optional<String> measure = NullSafe.list(fields).stream()
                .filter(field -> NUMBERS.contains(field.getFldType()))
                .map(QueryField::getFldName)
                .filter(name -> name != null && !name.startsWith("_") && !NOT_MEASURES.contains(name))
                .findFirst();
        final String by = name(group.get());
        return Optional.of(measure
                .map(number -> "| STATS n = COUNT(*), total = SUM(" + name(number) + ") BY " + by
                               + " | SORT total DESC | LIMIT 10")
                .orElse("| STATS n = COUNT(*) BY " + by + " | SORT n DESC | LIMIT 10"));
    }

    /// @param fieldName A field's name.
    /// @return It as ES|QL names it: as it is, or in backticks if it holds anything but letters, digits, `_`, `@`
    /// and `.`.
    static String name(final String fieldName) {
        return PLAIN_NAME.matcher(fieldName).matches()
                ? fieldName
                : "`" + fieldName.replace("`", "``") + "`";
    }
}
