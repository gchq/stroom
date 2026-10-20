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

package stroom.quickfilter.shared;

import stroom.docref.DocRef;
import stroom.query.api.datasource.QueryField;
import stroom.util.shared.NullSafe;
import stroom.util.shared.filter.FilterFieldDefinition;

import java.util.List;
import java.util.Objects;

/**
 * Everything a screen has to say about its quick filter, in one place.
 * <p>
 * Before this existed each surface declared the same information three times over -
 * {@code FIELD_DEFINITIONS} for the tooltip, {@code QUERY_FIELDS} and {@code DEFAULT_QUERY_FIELDS}
 * for the server-side parse - and handed each to a different consumer, with nothing to stop
 * the tooltip advertising a field the parser did not know about. This is one object for all of
 * them, plus the one thing none of them had: a stable identity, so that the filters a user has
 * recently typed here can be kept apart from the ones they typed everywhere else.
 * <p>
 * A context is a <em>field set</em> over a <em>data source</em>. Two screens that parse against
 * the same fields over the same data source share one context, so a filter that works in one
 * works in the other. Surfaces whose criteria carry a data source - traces and pathways, which
 * query one Plan B document at a time - call {@link #withDataSource(DocRef)} at bind time; the
 * operations and trace ids in one document mean nothing in another.
 * <p>
 * Not a Jackson type: it is declared as a constant and never travels. What travels is the
 * {@link QuickFilterHistoryKey} it derives.
 */
public final class QuickFilterContext {

    private final String key;
    private final DocRef dataSource;
    private final List<FilterFieldDefinition> fieldDefinitions;
    private final List<QueryField> defaultFields;
    private final List<QueryField> qualifiedFields;

    private QuickFilterContext(final String key,
                               final DocRef dataSource,
                               final List<FilterFieldDefinition> fieldDefinitions,
                               final List<QueryField> defaultFields,
                               final List<QueryField> qualifiedFields) {
        this.key = Objects.requireNonNull(key);
        this.dataSource = dataSource;
        this.fieldDefinitions = NullSafe.list(fieldDefinitions);
        this.defaultFields = NullSafe.list(defaultFields);
        this.qualifiedFields = NullSafe.list(qualifiedFields);
    }

    /**
     * @param key             identifies the field set. Persisted against every filter a user
     *                        records here, so it must never change once released. Lower camel
     *                        case, e.g. {@code "dependencies"}.
     * @param fieldDefinitions what the tooltip advertises
     * @param defaultFields   the fields a bare, unqualified term ORs across
     * @param qualifiedFields every field the user can name with a qualifier
     */
    public static QuickFilterContext of(final String key,
                                        final List<FilterFieldDefinition> fieldDefinitions,
                                        final List<QueryField> defaultFields,
                                        final List<QueryField> qualifiedFields) {
        return new QuickFilterContext(key, null, fieldDefinitions, defaultFields, qualifiedFields);
    }

    /**
     * A context whose fields are not declared client-side - they are served at runtime
     * ({@code ActivityResource.listFieldDefinitions()}) or only the server's DAO knows them. History
     * works exactly as for any other context; only Advanced Query, which needs the fields to
     * build a tree editor, is unavailable until the surface declares them.
     */
    public static QuickFilterContext historyOnly(final String key) {
        return new QuickFilterContext(key, null, null, null, null);
    }

    /**
     * Whether Advanced Query can be offered: false for {@link #historyOnly(String)} contexts.
     */
    public boolean hasFields() {
        return !qualifiedFields.isEmpty();
    }

    /**
     * The same field set over one particular data source. See the class comment.
     */
    public QuickFilterContext withDataSource(final DocRef dataSource) {
        return new QuickFilterContext(key, dataSource, fieldDefinitions, defaultFields, qualifiedFields);
    }

    public String getKey() {
        return key;
    }

    public DocRef getDataSource() {
        return dataSource;
    }

    public List<FilterFieldDefinition> getFieldDefinitions() {
        return fieldDefinitions;
    }

    public List<QueryField> getDefaultFields() {
        return defaultFields;
    }

    public List<QueryField> getQualifiedFields() {
        return qualifiedFields;
    }

    /**
     * What the history endpoints key on: the field set and the data source, without the fields
     * themselves, which the server has no use for.
     */
    public QuickFilterHistoryKey toHistoryKey() {
        return new QuickFilterHistoryKey(key, NullSafe.get(dataSource, DocRef::getUuid));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final QuickFilterContext that = (QuickFilterContext) o;
        return key.equals(that.key) && Objects.equals(dataSource, that.dataSource);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, dataSource);
    }

    @Override
    public String toString() {
        return "QuickFilterContext{" +
               "key='" + key + '\'' +
               ", dataSource=" + dataSource +
               '}';
    }
}
