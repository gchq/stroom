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

package stroom.ai.impl.tools.sequence;

import stroom.ai.impl.tools.sequence.EqlQueryWriter.Filter;
import stroom.ai.impl.tools.sequence.EqlQueryWriter.Step;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.FieldType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// A step's conditions as a Stroom expression, for the streaming join, which Stroom's Elasticsearch module makes into
/// Query DSL as a search's expression is: the same conditions the EQL and its pre-flight counts say. `CONTAINS`,
/// `STARTS_WITH` and `ENDS_WITH` on text are `*` patterns; `IS_NULL` and `IS_NOT_NULL` are the field's presence
/// (`= *`); `IN` takes its values separated by commas, as the steps do; `NOT_IN` is `NOT (IN)`; `caseInsensitive` is
/// the term's case-insensitive option. A condition on a string's length has no expression form, so it is refused
/// (EQL takes it).
public final class StreamFilters {

    /// The term option that matches a keyword whatever its case (Stroom's Elasticsearch query option).
    static final String CASE_INSENSITIVE = "caseInsensitive";
    private static final Set<FieldType> TEXT_TYPES = Set.of(FieldType.TEXT, FieldType.KEYWORD);

    private StreamFilters() {
        // Static helpers only.
    }

    /// @param step The step.
    /// @param what The step, for a refusal (e.g. `Step 2`).
    /// @return Its conditions (`where` all, `any_of` one of), or null if it has none.
    /// @throws WorkspaceException for a condition on a string's length.
    static ExpressionOperator expression(final Step step, final String what) {
        final List<ExpressionItem> items = new ArrayList<>();
        for (final Filter filter : step.filters()) {
            items.add(item(filter, step.types(), what));
        }
        if (!step.anyOf().isEmpty()) {
            final List<ExpressionItem> anyOf = new ArrayList<>();
            for (final Filter filter : step.anyOf()) {
                anyOf.add(item(filter, step.types(), what));
            }
            items.add(ExpressionOperator.builder().op(Op.OR).children(anyOf).build());
        }
        return items.isEmpty()
                ? null
                : ExpressionOperator.builder().op(Op.AND).children(items).build();
    }

    /// @param filter A condition.
    /// @param types  Its document's field types, by name.
    /// @param what   The step, for a refusal.
    /// @return It as an expression item.
    public static ExpressionItem item(final Filter filter, final Map<String, FieldType> types, final String what) {
        if (filter.length()) {
            throw new WorkspaceException(what + ": a condition on the length of '" + filter.field() + "' can only "
                                         + "run as EQL (engine=eql), not in the streaming join.");
        }
        final boolean text = TEXT_TYPES.contains(types.get(filter.field())) || types.get(filter.field()) == null;
        final String value = filter.value();
        return switch (filter.condition()) {
            case CONTAINS -> term(filter, Condition.EQUALS, "*" + value + "*", text);
            case STARTS_WITH -> term(filter, Condition.EQUALS, value + "*", text);
            case ENDS_WITH -> term(filter, Condition.EQUALS, "*" + value, text);
            case IS_NOT_NULL -> term(filter, Condition.EQUALS, "*", false);
            case IS_NULL -> not(term(filter, Condition.EQUALS, "*", false));
            case IN -> filter.negated()
                    ? not(term(filter, Condition.IN, value, text))
                    : term(filter, Condition.IN, value, text);
            default -> term(filter, filter.condition(), value, text);
        };
    }

    private static ExpressionItem term(final Filter filter,
                                       final Condition condition,
                                       final String value,
                                       final boolean text) {
        final ExpressionTerm.Builder builder = ExpressionTerm.builder()
                .field(filter.field())
                .condition(condition)
                .value(value);
        if (filter.caseInsensitive() && text) {
            builder.options(Map.of(CASE_INSENSITIVE, "true"));
        }
        return builder.build();
    }

    private static ExpressionItem not(final ExpressionItem item) {
        return ExpressionOperator.builder().op(Op.NOT).children(List.of(item)).build();
    }
}
