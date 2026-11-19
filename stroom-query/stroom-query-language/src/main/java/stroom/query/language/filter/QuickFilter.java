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

package stroom.query.language.filter;

import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.datasource.QueryField;
import stroom.query.api.token.TokenException;
import stroom.query.language.filter.SimpleStringExpressionParser.FieldProvider;
import stroom.util.shared.NullSafe;

import java.util.List;

/**
 * Folds the text a user typed into a quick filter together with any structural expression the
 * screen composed itself.
 * <p>
 * Several criteria carry both: a {@code quickFilter} string, which is the user's own input and
 * belongs to the surface syntax, and an {@code expression} holding terms the screen adds on the
 * user's behalf - "children of this group", "only groups". The two are ANDed rather than merged,
 * so a filter can only ever narrow what the screen already asked for.
 */
public final class QuickFilter {

    private QuickFilter() {
    }

    /**
     * Parse the text a user typed into a quick filter. Most surfaces want this: they have no
     * expression of their own, so there is nothing to compose with.
     *
     * @param quickFilter     what the user typed, or null/blank
     * @param defaultFields   the fields a bare, unqualified term ORs across
     * @param qualifiedFields every field the user can name with a qualifier
     * @return the parsed expression, or null if there was nothing to parse
     * @throws TokenException if the text cannot be parsed, or names a condition a field cannot
     *                        honour. Callers should turn this into an empty result carrying the
     *                        reason rather than an error - the filter re-queries on a debounce, so
     *                        a half-typed term is an expected transient state.
     */
    public static ExpressionOperator parse(final String quickFilter,
                                           final List<QueryField> defaultFields,
                                           final List<QueryField> qualifiedFields) {
        if (NullSafe.isBlankString(quickFilter)) {
            return null;
        }
        final FieldProvider fieldProvider = new FieldProviderImpl(defaultFields, qualifiedFields);
        return SimpleStringExpressionParser
                .create(fieldProvider, quickFilter)
                .orElse(null);
    }

    /**
     * For the two surfaces that compose terms of their own - {@code FindUserCriteria} and
     * {@code AdvancedDocumentFindRequest} - fold the user's text together with the screen's own
     * expression. ANDed, so a filter can only ever narrow what the screen already asked for.
     *
     * @param expression the screen's own terms, or null
     * @see #parse(String, List, List)
     */
    public static ExpressionOperator and(final ExpressionOperator expression,
                                         final String quickFilter,
                                         final List<QueryField> defaultFields,
                                         final List<QueryField> qualifiedFields) {
        final ExpressionOperator parsed = parse(quickFilter, defaultFields, qualifiedFields);
        if (parsed == null) {
            return expression;
        }
        return expression == null || !expression.hasChildren()
                ? parsed
                : ExpressionOperator
                        .builder()
                        .op(Op.AND)
                        .addOperators(expression, parsed)
                        .build();
    }
}
