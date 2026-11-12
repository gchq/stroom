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

package stroom.query.language;

import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/// Splits the `where` clause of a query with joins into the parts each search can apply itself and
/// the part that must run on the joined rows.
///
/// The clause is split at its top-level `and`. A part that names only fields of the first source
/// is pushed down to the first source's search, with any `<alias>.` prefix of the first source
/// removed. A part that names only fields of one joined source is pushed down to that source's
/// search, without its prefix, when the join allows it (see
/// [stroom.query.api.JoinType#allowsJoinedConditionPushDown()]) and the joined source is not a
/// bracketed query. Every other part, including an `or` that names fields of more than one source,
/// runs on the joined rows, with its field names as written.
public final class JoinWhereSplitter {

    // The owner of fields that belong to the first source. An alias can't be empty, so this never
    // clashes with one.
    private static final String FIRST_SOURCE = "";

    private JoinWhereSplitter() {
        // Static methods only.
    }

    /// Splits a `where` clause.
    ///
    /// @param where         The `where` clause, or `null` if there is none.
    /// @param firstAlias    The alias of the first source, or `null` if it has none.
    /// @param firstIsQuery  True if the first source is a bracketed query, in which case nothing can
    ///                      be pushed down to it and the whole clause runs on the joined rows.
    /// @param joinedSources The joined sources, in the order they are joined.
    /// @return The parts of the clause.
    public static Split split(final ExpressionOperator where,
                              final String firstAlias,
                              final boolean firstIsQuery,
                              final List<JoinedSource> joinedSources) {
        final List<ExpressionItem> first = new ArrayList<>();
        final Map<String, List<ExpressionItem>> joined = new LinkedHashMap<>();
        final List<ExpressionItem> postJoin = new ArrayList<>();

        for (final ExpressionItem part : getTopLevelParts(where)) {
            if (firstIsQuery) {
                postJoin.add(part);
            } else {
                final Set<String> owners = new HashSet<>();
                collectOwners(part, firstAlias, joinedSources, owners);
                final String owner = owners.size() == 1
                        ? owners.iterator().next()
                        : null;
                if (owner == null) {
                    // Names fields of more than one source.
                    postJoin.add(part);
                } else if (owner.equals(FIRST_SOURCE)) {
                    first.add(removePrefix(part, firstAlias));
                } else {
                    final JoinedSource joinedSource = findJoinedSource(owner, joinedSources);
                    if (joinedSource.pushDownAllowed()) {
                        joined.computeIfAbsent(owner, k -> new ArrayList<>())
                                .add(removePrefix(part, owner));
                    } else {
                        postJoin.add(part);
                    }
                }
            }
        }

        final Map<String, ExpressionOperator> joinedExpressions = new LinkedHashMap<>();
        joined.forEach((alias, items) -> joinedExpressions.put(alias, and(items)));
        return new Split(and(first), joinedExpressions, and(postJoin));
    }

    /// Joins two conditions with `and`, either of which may be missing.
    ///
    /// @param first  The first condition, or `null`.
    /// @param second The second condition, or `null`.
    /// @return Both conditions joined, the one that is present, or `null` if neither is.
    public static ExpressionOperator and(final ExpressionOperator first, final ExpressionOperator second) {
        if (first == null) {
            return second;
        } else if (second == null) {
            return first;
        }
        return ExpressionOperator.builder()
                .addOperator(first)
                .addOperator(second)
                .build();
    }

    // The parts of the clause that are joined by its top-level 'and'. The parser nests one 'and' in
    // another, as in 'a and b and c' becoming AND {AND {a, b}, c}, so nested ones are flattened.
    private static List<ExpressionItem> getTopLevelParts(final ExpressionOperator where) {
        final List<ExpressionItem> parts = new ArrayList<>();
        if (where != null && where.enabled()) {
            addAndParts(where, parts);
        }
        return parts;
    }

    private static void addAndParts(final ExpressionItem item, final List<ExpressionItem> parts) {
        if (item instanceof final ExpressionOperator operator && Op.AND.equals(operator.op())) {
            for (final ExpressionItem child : operator.getChildren()) {
                if (child.enabled()) {
                    addAndParts(child, parts);
                }
            }
        } else {
            parts.add(item);
        }
    }

    // Adds the source of every field the item names: FIRST_SOURCE or a joined source's alias.
    private static void collectOwners(final ExpressionItem item,
                                      final String firstAlias,
                                      final List<JoinedSource> joinedSources,
                                      final Set<String> owners) {
        if (item instanceof final ExpressionTerm term) {
            owners.add(getOwner(term.getField(), firstAlias, joinedSources));
        } else if (item instanceof final ExpressionOperator operator) {
            for (final ExpressionItem child : operator.getChildren()) {
                if (child.enabled()) {
                    collectOwners(child, firstAlias, joinedSources, owners);
                }
            }
        }
    }

    /// Finds the alias a field name is prefixed with: the longest of the aliases that, followed by a
    /// dot, starts the name, so an alias that contains a dot still works.
    ///
    /// @param field   The field name, e.g. `hr.Department`.
    /// @param aliases The aliases to look for.
    /// @return The alias, or `null` if the name starts with none of them.
    public static String findAlias(final String field, final Collection<String> aliases) {
        String found = null;
        if (field != null) {
            for (final String alias : aliases) {
                if (hasPrefix(field, alias) && (found == null || alias.length() > found.length())) {
                    found = alias;
                }
            }
        }
        return found;
    }

    /// Copies an expression with every field renamed.
    ///
    /// @param expression The expression, or `null`.
    /// @param rename     Gives the new name of a field from its old one.
    /// @return The renamed copy, or `null` if the expression is `null`.
    public static ExpressionOperator renameFields(final ExpressionOperator expression,
                                                  final Function<String, String> rename) {
        if (expression == null) {
            return null;
        }
        return (ExpressionOperator) rename(expression, rename);
    }

    private static ExpressionItem rename(final ExpressionItem item, final Function<String, String> rename) {
        if (item instanceof final ExpressionTerm term) {
            return term.getField() == null
                    ? term
                    : term.copy().field(rename.apply(term.getField())).build();
        } else if (item instanceof final ExpressionOperator operator) {
            final List<ExpressionItem> children = new ArrayList<>();
            for (final ExpressionItem child : operator.getChildren()) {
                children.add(rename(child, rename));
            }
            return operator.copy().children(children).build();
        }
        return item;
    }

    // The source a field belongs to: the joined source whose alias is its prefix, or the first source
    // if the longest alias prefix is the first source's own, or there is none.
    private static String getOwner(final String field,
                                   final String firstAlias,
                                   final List<JoinedSource> joinedSources) {
        final List<String> aliases = new ArrayList<>();
        joinedSources.forEach(joinedSource -> aliases.add(joinedSource.alias()));
        if (firstAlias != null) {
            aliases.add(firstAlias);
        }
        final String alias = findAlias(field, aliases);
        return alias == null || alias.equals(firstAlias)
                ? FIRST_SOURCE
                : alias;
    }

    private static boolean hasPrefix(final String field, final String alias) {
        return alias != null
               && field.length() > alias.length() + 1
               && field.startsWith(alias)
               && field.charAt(alias.length()) == '.';
    }

    private static JoinedSource findJoinedSource(final String alias, final List<JoinedSource> joinedSources) {
        return joinedSources.stream()
                .filter(joinedSource -> joinedSource.alias().equals(alias))
                .findFirst()
                .orElseThrow();
    }

    // A copy of the item with '<alias>.' taken off the front of every field name that has it.
    private static ExpressionItem removePrefix(final ExpressionItem item, final String alias) {
        if (alias == null) {
            return item;
        } else if (item instanceof final ExpressionTerm term) {
            final String field = term.getField();
            if (field != null && hasPrefix(field, alias)) {
                return term.copy().field(field.substring(alias.length() + 1)).build();
            }
            return term;
        } else if (item instanceof final ExpressionOperator operator) {
            final List<ExpressionItem> children = new ArrayList<>();
            for (final ExpressionItem child : operator.getChildren()) {
                children.add(removePrefix(child, alias));
            }
            return operator.copy().children(children).build();
        }
        return item;
    }

    private static ExpressionOperator and(final List<ExpressionItem> items) {
        if (items.isEmpty()) {
            return null;
        } else if (items.size() == 1 && items.getFirst() instanceof final ExpressionOperator operator) {
            return operator;
        }
        return ExpressionOperator.builder().children(items).build();
    }


    // --------------------------------------------------------------------------------


    /// A joined source, as far as splitting the `where` clause needs to know it.
    ///
    /// @param alias            The alias its fields are prefixed with.
    /// @param pushDownAllowed  True if a condition that names only its fields may be moved into its
    ///                         own search.
    public record JoinedSource(String alias, boolean pushDownAllowed) {

        public JoinedSource {
            Objects.requireNonNull(alias);
        }
    }

    /// The parts of a split `where` clause. Each is `null` when it has no conditions.
    ///
    /// @param first    The conditions for the first source's search, without its alias prefix.
    /// @param joined   The conditions for each joined source's search, by alias, without the alias
    ///                 prefix.
    /// @param postJoin The conditions to apply to the joined rows, with field names as written.
    public record Split(ExpressionOperator first,
                        Map<String, ExpressionOperator> joined,
                        ExpressionOperator postJoin) {

    }
}
