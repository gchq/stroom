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

package stroom.query.api.datasource;

import stroom.util.shared.filter.FilterFieldDefinition;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Derives the {@link QueryField}s a quick filter surface declares from the
 * {@link FilterFieldDefinition}s it already has.
 * <p>
 * {@link FilterFieldDefinition} cannot carry a {@link ConditionSet} itself: it lives in
 * {@code stroom-util-shared}, and {@code ConditionSet} lives here in {@code stroom-query-api},
 * which already depends on {@code stroom-util-shared}. Rather than have every surface hand-write
 * a parallel set of constants that can drift from its field definitions, derive one from the
 * other.
 * <p>
 * The {@link QueryField} name is the field's <em>filter qualifier</em> ("submittime"), not its
 * display name ("Submit Time"), because the qualifier is what a user types and therefore what the
 * parser resolves a term against.
 */
public final class QuickFilterFields {

    private QuickFilterFields() {
    }

    /**
     * For a surface evaluated in memory by {@code ExpressionPredicateFactory}.
     * <p>
     * Every such surface extracts its values as strings - the task manager stringifies its submit
     * time, for instance - so {@link ConditionSet#ALL_UI_TEXT} is the right declaration for every
     * field on them, including ones whose underlying value is a date or a number.
     */
    public static QueryField uiText(final FilterFieldDefinition fieldDefinition) {
        return QueryField.createUiText(fieldDefinition.getFilterQualifier());
    }

    /**
     * The {@link QueryField}s for the subset of {@code fieldDefinitions} that a bare, unqualified
     * term matches against.
     *
     * @see #uiText(FilterFieldDefinition)
     */
    public static List<QueryField> uiTextDefaults(final List<FilterFieldDefinition> fieldDefinitions) {
        //noinspection SimplifyStreamApiCallChains // Cos GWT
        return fieldDefinitions
                .stream()
                .filter(FilterFieldDefinition::isDefaultField)
                .map(QuickFilterFields::uiText)
                .collect(Collectors.toList());
    }

    /**
     * @see #uiText(FilterFieldDefinition)
     */
    public static List<QueryField> uiText(final List<FilterFieldDefinition> fieldDefinitions) {
        //noinspection SimplifyStreamApiCallChains // Cos GWT
        return fieldDefinitions
                .stream()
                .map(QuickFilterFields::uiText)
                .collect(Collectors.toList());
    }
}
