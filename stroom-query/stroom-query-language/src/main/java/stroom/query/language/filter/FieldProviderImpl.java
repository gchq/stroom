/*
 * Copyright 2024 Crown Copyright
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

package stroom.query.language.filter;

import stroom.query.api.datasource.QueryField;
import stroom.query.language.filter.SimpleStringExpressionParser.FieldProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class FieldProviderImpl implements FieldProvider {

    private final List<QueryField> defaultFields;
    private final Map<String, QueryField> qualifiedFields;

    /**
     * @param defaultFields   the fields a bare, unqualified term ORs across
     * @param qualifiedFields every field the user can name with a qualifier, which normally
     *                        includes the default fields
     */
    public FieldProviderImpl(final List<QueryField> defaultFields,
                             final List<QueryField> qualifiedFields) {
        this.defaultFields = defaultFields;
        this.qualifiedFields = new HashMap<>();
        for (final QueryField field : qualifiedFields) {
            this.qualifiedFields.put(field.getFldName().toLowerCase(Locale.ROOT), field);
        }
    }

    @Override
    public List<QueryField> getDefaultFields() {
        return defaultFields;
    }

    @Override
    public Optional<QueryField> getQualifiedField(final String string) {
        return Optional.ofNullable(qualifiedFields.get(string.toLowerCase(Locale.ROOT)));
    }
}
