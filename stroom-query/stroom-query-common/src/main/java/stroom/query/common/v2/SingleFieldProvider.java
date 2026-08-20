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

package stroom.query.common.v2;

import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.SimpleStringExpressionParser.FieldProvider;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A surface with exactly one field, which every term matches against.
 * <p>
 * {@link #getQualifiedField(String)} always returns empty, which is what makes ':' an ordinary
 * value character here: with no qualifier to resolve, "12:30" is a literal rather than a term
 * against a field named "12".
 */
public class SingleFieldProvider implements FieldProvider {

    private final List<QueryField> defaultFields;

    public SingleFieldProvider(final QueryField defaultField) {
        this.defaultFields = Collections.singletonList(defaultField);
    }

    @Override
    public List<QueryField> getDefaultFields() {
        return defaultFields;
    }

    @Override
    public Optional<QueryField> getQualifiedField(final String string) {
        return Optional.empty();
    }
}
