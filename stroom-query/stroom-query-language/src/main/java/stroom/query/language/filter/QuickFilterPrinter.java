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

import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.QueryField;
import stroom.query.language.filter.SimpleStringExpressionParser.FieldProvider;
import stroom.util.shared.NullSafe;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The inverse of {@link SimpleStringExpressionParser}: renders an expression tree in the quick
 * filter surface syntax, so that a tree built in the Advanced Query dialog can be written back
 * into the filter box.
 * <p>
 * Contract, checked by {@code TestQuickFilterPrinter}: for every tree this can print,
 * {@code parse(print(tree))} is {@code tree}; and for every text the parser accepts,
 * {@code print(parse(text))} is a canonical spelling of {@code text}. Output is deterministic -
 * children in tree order, single spaces, no trailing space - so the history de-duplication sees
 * one spelling.
 * <p>
 * What it cannot print, it refuses to print, naming the reason, rather than emitting text that
 * would parse to a different tree: disabled items (the syntax has no way to say "ignore this"),
 * conditions with no sigil ({@code IN}, {@code BETWEEN}, the doc-ref and user-ref conditions),
 * and terms on fields the surface does not declare.
 * <p>
 * Two things are printed by the parser's rules rather than the spec's, until the parser changes:
 * {@code NOT_EQUALS} prints as {@code !=}, which the parser currently reads back as
 * {@code NOT(EQUALS)} (spec §5.1); and a chars-anywhere term prints as the regex the parser turned
 * it into, {@code /a.*?b.*?c}, not {@code ~abc} (spec §5.2).
 */
public final class QuickFilterPrinter {

    /**
     * The conditions the surface syntax can spell, by their sigil: the parser's own list, so the
     * two cannot drift, plus the two negated-equals conditions the spec assigns {@code !=} and
     * {@code !==} to.
     */
    private static final Map<Condition, String> SIGILS = Stream.concat(
                    SimpleStringExpressionParser.SUPPORTED_CONDITIONS.stream(),
                    Stream.of(Condition.NOT_EQUALS, Condition.NOT_EQUALS_CASE_SENSITIVE))
            .collect(Collectors.toUnmodifiableMap(c -> c, Condition::getOperator));

    /**
     * The sigils the parser itself matches, in the order it tries them.
     */
    private static final List<String> PARSER_SIGILS = SimpleStringExpressionParser.SUPPORTED_CONDITIONS
            .stream()
            .map(Condition::getOperator)
            .toList();

    /**
     * Characters that would be read as a sigil, negation, chars-anywhere or escape if a value
     * started with them, so such a value is quoted.
     */
    private static final String LEADING_SPECIALS = "!~\\+=^$><?/";

    private QuickFilterPrinter() {
    }

    /**
     * @param expression    the tree; null or childless prints as the empty string
     * @param fieldProvider the same fields the text will be parsed against
     * @throws QuickFilterPrintException if any part of the tree has no textual form
     */
    public static String print(final ExpressionOperator expression, final FieldProvider fieldProvider) {
        if (expression == null || !expression.hasChildren()) {
            return "";
        }
        checkEnabled(expression);
        final Printer printer = new Printer(fieldProvider);
        // The parser wraps a top-level list of items in an AND, so an AND root prints without
        // brackets; any other root is a single item and prints as itself.
        return expression.op() == Op.AND
                ? printer.children(expression, " ")
                : printer.item(expression);
    }

    /**
     * True if the parser could read this condition back, i.e. it has a sigil or is a default.
     */
    public static boolean isPrintable(final Condition condition) {
        return SIGILS.containsKey(condition);
    }

    private static void checkEnabled(final ExpressionItem item) {
        if (!item.enabled()) {
            throw new QuickFilterPrintException(
                    "A disabled " + describe(item) + " has no filter text form. Delete it or enable it.");
        }
    }

    private static String describe(final ExpressionItem item) {
        return item instanceof final ExpressionTerm term
                ? "term on '" + term.getField() + "'"
                : "operator";
    }


    // --------------------------------------------------------------------------------


    private static final class Printer {

        private final FieldProvider fieldProvider;
        private final List<QueryField> defaultFields;

        Printer(final FieldProvider fieldProvider) {
            this.fieldProvider = fieldProvider;
            this.defaultFields = NullSafe.list(fieldProvider.getDefaultFields());
        }

        String children(final ExpressionOperator operator, final String separator) {
            return operator.getChildren()
                    .stream()
                    .map(child -> {
                        checkEnabled(child);
                        return child instanceof ExpressionOperator
                                ? bracketed((ExpressionOperator) child)
                                : item(child);
                    })
                    .collect(Collectors.joining(separator));
        }

        /**
         * A nested operator. The parser's precedence is implicit-AND lowest, then {@code or},
         * then {@code and}, so rather than reason about it every nested operator is bracketed -
         * except the two shapes that read back as a single item without brackets.
         */
        private String bracketed(final ExpressionOperator operator) {
            final Optional<String> collapsed = collapseDefaultFieldGroup(operator, false);
            if (collapsed.isPresent()) {
                return collapsed.get();
            }
            if (operator.op() == Op.NOT) {
                return item(operator);
            }
            return wrap(item(operator));
        }

        /**
         * The tokeniser only recognises a keyword after whitespace, the start of the text or a
         * ')' - never directly after '(' - so a bracketed group that opens with 'not' gets a
         * space after the bracket.
         */
        private static String wrap(final String inner) {
            return inner.startsWith("not ")
                    ? "( " + inner + ")"
                    : "(" + inner + ")";
        }

        String item(final ExpressionItem item) {
            checkEnabled(item);
            if (item instanceof final ExpressionTerm term) {
                return term(term, false);
            }
            final ExpressionOperator operator = (ExpressionOperator) item;
            final List<ExpressionItem> children = NullSafe.list(operator.getChildren());
            return switch (operator.op()) {
                case AND -> children(operator, " ");
                case OR -> collapseDefaultFieldGroup(operator, false)
                        .orElseGet(() -> children(operator, " or "));
                case NOT -> {
                    if (children.size() != 1) {
                        throw new QuickFilterPrintException(
                                "A NOT with " + children.size() + " children has no filter text form.");
                    }
                    final ExpressionItem child = children.getFirst();
                    checkEnabled(child);
                    // Fold the negation into the term where the syntax allows it - '!abc',
                    // 'name:!^abc' - and spell it out where it does not.
                    if (child instanceof final ExpressionTerm term) {
                        yield term(term, true);
                    }
                    final ExpressionOperator childOperator = (ExpressionOperator) child;
                    final Optional<String> collapsed = collapseDefaultFieldGroup(childOperator, true);
                    yield collapsed.orElseGet(() -> "not " + wrap(item(childOperator)));
                }
            };
        }

        /**
         * A bare term ORs across the default fields, each with its own default condition - or,
         * when written with a sigil, all with that condition. Recognise that exact shape and
         * print it back as the bare term; anything else is printed as what it is.
         */
        private Optional<String> collapseDefaultFieldGroup(final ExpressionOperator operator,
                                                           final boolean negated) {
            if (operator.op() != Op.OR || defaultFields.size() < 2) {
                return Optional.empty();
            }
            final List<ExpressionItem> children = NullSafe.list(operator.getChildren());
            if (children.size() != defaultFields.size()
                || !children.stream().allMatch(c -> c instanceof ExpressionTerm && c.enabled())) {
                return Optional.empty();
            }
            final List<ExpressionTerm> terms = children.stream().map(ExpressionTerm.class::cast).toList();
            final String value = terms.getFirst().getValue();
            if (value == null || !SIGILS.containsKey(terms.getFirst().getCondition())) {
                // Let the term path refuse it with a reason, rather than quietly printing the
                // bare form, which would read back as the default condition.
                return Optional.empty();
            }
            boolean allDefaultConditions = true;
            boolean allSameCondition = true;
            for (int i = 0; i < terms.size(); i++) {
                final ExpressionTerm term = terms.get(i);
                final QueryField field = defaultFields.get(i);
                if (!sameField(term.getField(), field) || !Objects.equals(term.getValue(), value)) {
                    return Optional.empty();
                }
                allDefaultConditions &= term.getCondition() == defaultCondition(field);
                allSameCondition &= term.getCondition() == terms.getFirst().getCondition();
            }
            if (allDefaultConditions) {
                return Optional.of(bare(null, negated, value));
            }
            if (allSameCondition) {
                return Optional.of(bare(sigil(terms.getFirst()), negated, value));
            }
            return Optional.empty();
        }

        private String term(final ExpressionTerm term, final boolean negated) {
            final String fieldName = term.getField();
            if (NullSafe.isBlankString(fieldName)) {
                throw new QuickFilterPrintException("A term with no field has no filter text form.");
            }
            final Condition condition = term.getCondition();
            if (condition == null) {
                throw new QuickFilterPrintException(
                        "The term on '" + fieldName + "' has no condition.");
            }
            if (!SIGILS.containsKey(condition)) {
                throw new QuickFilterPrintException("'" + condition.getDisplayValue()
                                                    + "' on '" + fieldName
                                                    + "' cannot be written as filter text.");
            }
            if (term.getValue() == null) {
                throw new QuickFilterPrintException(
                        "The term on '" + fieldName + "' has no value.");
            }

            // A term on the sole default field prints bare, as the user would type it; every
            // other term needs its qualifier.
            final boolean isBare = defaultFields.size() == 1 && sameField(fieldName, defaultFields.getFirst());
            final QueryField field = isBare
                    ? defaultFields.getFirst()
                    : fieldProvider.getQualifiedField(fieldName)
                            .orElseThrow(() -> new QuickFilterPrintException(
                                    defaultFields.stream().anyMatch(f -> sameField(fieldName, f))
                                            ? "'" + fieldName + "' is a default field here but has no "
                                              + "qualifier, so a term on it alone cannot be written."
                                            : "This filter has no field '" + fieldName + "'."));
            final String sigil = condition == defaultCondition(field)
                    ? null
                    : sigil(term);
            final String qualifier = isBare
                    ? ""
                    : field.getFldName() + ":";

            // The parser does not read a lone '!' before a quoted value as negation, and cannot
            // fold a negation into a '!=' sigil, so those two cases spell the negation out.
            if (negated && ((sigil == null && needsQuoting(term.getValue(), null, !isBare))
                            || (sigil != null && sigil.startsWith("!")))) {
                return "not " + qualifier + prefix(sigil, false) + value(term.getValue(), sigil, !isBare);
            }
            return qualifier + prefix(sigil, negated) + value(term.getValue(), sigil, !isBare);
        }

        private String bare(final String sigil, final boolean negated, final String value) {
            // Same two cases as term(): a lone '!' before a quoted value is not read as
            // negation, and '!' cannot be folded onto a '!=' sigil.
            if (negated && ((sigil == null && needsQuoting(value, null, false))
                            || (sigil != null && sigil.startsWith("!")))) {
                return "not " + prefix(sigil, false) + value(value, sigil, false);
            }
            return prefix(sigil, negated) + value(value, sigil, false);
        }

        private static String prefix(final String sigil, final boolean negated) {
            return (negated
                    ? "!"
                    : "") + (sigil == null
                    ? ""
                    : sigil);
        }

        /**
         * Quote the value wherever the parser would otherwise read part of it as structure.
         */
        private String value(final String value, final String sigil, final boolean qualified) {
            if (needsQuoting(value, sigil, qualified)) {
                return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            }
            return value;
        }

        private boolean needsQuoting(final String value,
                                     final String sigil,
                                     final boolean qualified) {
            if (value.isEmpty()) {
                return true;
            }
            for (final char c : value.toCharArray()) {
                if (Character.isWhitespace(c) || c == '(' || c == ')' || c == '"' || c == '\'') {
                    return true;
                }
            }
            if (sigil == null) {
                // Nothing precedes the value, so a leading special would be read as structure.
                if (LEADING_SPECIALS.indexOf(value.charAt(0)) >= 0) {
                    return true;
                }
            } else {
                // The parser takes the longest sigil the text starts with; if the value's first
                // characters extend ours into a longer one ('=' + '=abc' reads as '=='), quote.
                // A leading '!' is stripped by the parser before it looks, so look past it too.
                final String parserSigil = sigil.startsWith("!")
                        ? sigil.substring(1)
                        : sigil;
                final String composed = parserSigil + value;
                final String matched = PARSER_SIGILS.stream()
                        .filter(composed::startsWith)
                        .findFirst()
                        .orElse("");
                if (!matched.equals(parserSigil)) {
                    return true;
                }
            }
            // Keywords are only recognised as whole whitespace-delimited words.
            final String lower = value.toLowerCase(Locale.ROOT);
            if (lower.equals("and") || lower.equals("or") || lower.equals("not")) {
                return true;
            }
            // ...and only when the character before the whitespace is not '=', so a value ending
            // in '=' would stop a following keyword being read as one.
            if (value.endsWith("=")) {
                return true;
            }
            // '//' and '/*' open comments wherever they appear.
            final String composed = (sigil == null
                    ? ""
                    : sigil) + value;
            if (composed.contains("//") || composed.contains("/*")) {
                return true;
            }
            // In a bare term, text before the first ':' that names a field is a qualifier.
            if (!qualified) {
                final int colon = value.indexOf(':');
                if (colon > 0 && fieldProvider.getQualifiedField(value.substring(0, colon)).isPresent()) {
                    return true;
                }
            }
            return false;
        }

        private static String sigil(final ExpressionTerm term) {
            return SIGILS.get(term.getCondition());
        }

        private static boolean sameField(final String name, final QueryField field) {
            return name != null && name.equalsIgnoreCase(field.getFldName());
        }

        /**
         * Mirrors {@code SimpleStringExpressionParser.defaultCondition}.
         */
        private static Condition defaultCondition(final QueryField field) {
            return field.supportsCondition(Condition.CONTAINS)
                    ? Condition.CONTAINS
                    : Condition.EQUALS;
        }
    }
}
