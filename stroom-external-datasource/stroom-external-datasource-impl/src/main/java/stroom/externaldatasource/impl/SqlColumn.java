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

package stroom.externaldatasource.impl;

import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;

import java.util.List;

/// A column returned by the SQL of a query, which becomes a query field of the same name.
///
/// @param name      The column's label, as the database reports it.
/// @param fieldType The Stroom field type its values are read as.
/// @param sqlType   The column's `java.sql.Types` code, or for Amazon Athena [java.sql.Types#OTHER].
public record SqlColumn(String name, FieldType fieldType, int sqlType) {

    /// @return The column as a query field.
    public QueryField toQueryField() {
        return QueryField.builder()
                .fldName(name)
                .fldType(fieldType)
                .queryable(true)
                .build();
    }

    /// @param columns Columns, in order.
    /// @return The columns as query fields, in the same order.
    public static List<QueryField> toQueryFields(final List<SqlColumn> columns) {
        return columns.stream()
                .map(SqlColumn::toQueryField)
                .toList();
    }
}
