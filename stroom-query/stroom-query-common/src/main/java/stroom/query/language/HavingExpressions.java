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

import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.token.AbstractToken;
import stroom.query.api.token.FunctionGroup;
import stroom.query.api.token.KeywordGroup;
import stroom.query.api.token.Token;
import stroom.query.api.token.TokenException;
import stroom.query.api.token.TokenGroup;
import stroom.query.api.token.TokenType;
import stroom.query.common.v2.DateExpressionParser.DatePoint;
import stroom.query.language.functions.Expression;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.ExpressionParser;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ParamFactory;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Lets a condition of a `having` clause be written with expressions.
///
/// A having condition tests the columns of a grouped row by name, as in `having Events > 5`. So that
/// it can also be written as `having count() - lag(count(), 1) > 5`, an expression is given a name
/// and the condition is changed to test a column of that name. Once the whole query has been read,
/// [#resolve] parses the expressions and adds them to the expressions of the query, where each
/// becomes a hidden column of the table. They are parsed that late so that they can use the names
/// that the `select` clause gives to its columns, which comes after `having`.
final class HavingExpressions {

    // The names given to the expressions of a having clause start with this, and end with a number.
    static final String NAME_PREFIX = "having_";

    private static final String TRUE = "true";
    private static final String PARAM_FUNCTION = "param";
    private static final Set<TokenType> COMPARISONS = Set.of(
            TokenType.EQUALS,
            TokenType.NOT_EQUALS,
            TokenType.GREATER_THAN,
            TokenType.GREATER_THAN_OR_EQUAL_TO,
            TokenType.LESS_THAN,
            TokenType.LESS_THAN_OR_EQUAL_TO);
    private static final Set<TokenType> LOGIC = Set.of(
            TokenType.AND,
            TokenType.OR,
            TokenType.NOT);

    private final Map<String, Expression> expressionMap;
    private final FieldIndex fieldIndex;
    // The tokens of each expression that is waiting to be parsed, by the name it has been given.
    private final Map<String, List<AbstractToken>> pendingExpressions = new LinkedHashMap<>();
    // The name given to each expression, by the text of the expression, so that an expression that
    // is written twice gets one column.
    private final Map<String, String> namesByText = new HashMap<>();
    // The names that the select clause is written with, which are those of its fields and columns.
    private final Set<String> selectNames = new HashSet<>();

    /// Creates the helper for one query.
    ///
    /// @param expressionMap The named expressions of the query, which the expressions of the having
    ///                      clause are added to.
    /// @param fieldIndex    The fields that the query's expressions use.
    HavingExpressions(final Map<String, Expression> expressionMap,
                      final FieldIndex fieldIndex) {
        this.expressionMap = expressionMap;
        this.fieldIndex = fieldIndex;
    }

    /// Finds the date function, such as `now()`, that a function is.
    ///
    /// @param function The text of the function.
    /// @return The date function, or `null` if it is not one.
    static DatePoint findDatePoint(final String function) {
        for (final DatePoint datePoint : DatePoint.values()) {
            if (datePoint.getFunction().equals(function)) {
                return datePoint;
            }
        }
        return null;
    }

    /// Tells a bracketed expression, as in `(count() - 1) > 3`, from bracketed conditions, as in
    /// `(count() > 3 or max(Bytes) > 10)`.
    ///
    /// @param tokenGroup The brackets and what is in them.
    /// @return True if there is no condition in the brackets, so they hold an expression.
    static boolean isExpression(final TokenGroup tokenGroup) {
        for (final AbstractToken token : tokenGroup.getChildren()) {
            final TokenType tokenType = token.getTokenType();
            if (TokenType.ALL_CONDITIONS.contains(tokenType)
                || LOGIC.contains(tokenType)
                || (token instanceof final TokenGroup child && !isExpression(child))) {
                return false;
            }
        }
        return !tokenGroup.getChildren().isEmpty();
    }

    /// Finds the names that the `select` clause of a query is written with, so that a name on the
    /// right of a having condition can be told from text. The having clause comes before `select`,
    /// so this is done before the clauses are read.
    ///
    /// @param tokens The clauses of the query.
    void findSelectNames(final List<AbstractToken> tokens) {
        for (final AbstractToken token : tokens) {
            if (token instanceof final KeywordGroup keywordGroup
                && TokenType.SELECT.equals(keywordGroup.getTokenType())) {
                for (final AbstractToken child : keywordGroup.getChildren()) {
                    if (TokenType.isString(child)) {
                        selectNames.add(child.getUnescapedText());
                    }
                }
            }
        }
    }

    /// Makes a condition that compares with an expression, as in `count() > lag(count(), 1) * 2`,
    /// into a test of one column. A having condition can only test a column against a fixed value,
    /// so the whole comparison becomes an expression that is true or false.
    ///
    /// @param tokens The tokens of the condition.
    /// @return A term that tests the new expression for being true, or `null` if the right of the
    ///         condition is a value and not an expression. A name on its own is an expression if it
    ///         is the name of an `eval` or of a column of the `select` clause, and is text if not.
    ExpressionTerm createComparisonTerm(final List<AbstractToken> tokens) {
        final int conditionIndex = getConditionIndex(tokens);
        if (conditionIndex < 1
            || !COMPARISONS.contains(tokens.get(conditionIndex).getTokenType())
            || !isValueExpression(tokens.subList(conditionIndex + 1, tokens.size()))) {
            return null;
        }
        return ExpressionTerm
                .builder()
                .field(addExpression(tokens))
                .condition(Condition.EQUALS)
                .value(TRUE)
                .build();
    }

    /// Replaces an expression on the left of a condition, as in `count() - lag(count(), 1) > 5`,
    /// with the name it is given.
    ///
    /// @param tokens The tokens of the condition.
    /// @return The tokens of a condition on the named expression, or the same tokens if the left of
    ///         the condition is already the name of a column.
    List<AbstractToken> replaceLeftExpression(final List<AbstractToken> tokens) {
        final int conditionIndex = getConditionIndex(tokens);
        if (conditionIndex < 1) {
            return tokens;
        }
        final List<AbstractToken> left = tokens.subList(0, conditionIndex);
        final boolean columnName = left.size() == 1
                                   && (TokenType.isString(left.getFirst())
                                       || TokenType.PARAM.equals(left.getFirst().getTokenType()));
        if (columnName) {
            return tokens;
        }
        final List<AbstractToken> replaced = new ArrayList<>(tokens.size());
        replaced.add(createNameToken(addExpression(left)));
        replaced.addAll(tokens.subList(conditionIndex, tokens.size()));
        return replaced;
    }

    /// Parses the expressions of the having clause and adds them to the expressions of the query.
    /// This is done when the whole query has been read.
    ///
    /// @param expressionContext The context to parse expressions in.
    /// @param columnExpressions The expressions of the columns of the `select` clause, by the names
    ///                          of the columns, which the expressions can use.
    /// @param columnNames       The names of the columns that the query has so far.
    /// @return A token for the name of each expression, for the hidden columns to be made from.
    List<AbstractToken> resolve(final ExpressionContext expressionContext,
                                final Map<String, Expression> columnExpressions,
                                final Set<String> columnNames) {
        // A name tests the column of that name, as it does when it is the whole left of a condition.
        final Map<String, Expression> names = new HashMap<>(expressionMap);
        names.putAll(columnExpressions);
        final ExpressionParser expressionParser = new ExpressionParser(new ParamFactory(names));

        final List<AbstractToken> nameTokens = new ArrayList<>(pendingExpressions.size());
        pendingExpressions.forEach((name, tokens) -> {
            if (columnNames.contains(name)) {
                throw new TokenException(tokens.getFirst(),
                        "The having clause uses the name '" + name + "', so a column can't be called it");
            }
            try {
                expressionMap.put(name, expressionParser.parse(expressionContext, fieldIndex, tokens));
            } catch (final ParseException e) {
                throw new TokenException(tokens.getFirst(), e.getMessage());
            }
            nameTokens.add(createNameToken(name));
        });
        pendingExpressions.clear();
        return nameTokens;
    }

    private static Token createNameToken(final String name) {
        return new Token(TokenType.STRING, name.toCharArray(), 0, name.length() - 1);
    }

    private static int getConditionIndex(final List<AbstractToken> tokens) {
        for (int i = 0; i < tokens.size(); i++) {
            if (TokenType.ALL_CONDITIONS.contains(tokens.get(i).getTokenType())) {
                return i;
            }
        }
        return -1;
    }

    // The right of a condition is an expression, and not a value, if it has brackets, if it calls a
    // function other than those that a value can be written with, which are param() and the date
    // functions such as now(), if it works something out from a name, as in `Total / 2`, or if it is
    // the name of a column.
    private boolean isValueExpression(final List<AbstractToken> tokens) {
        for (final AbstractToken token : tokens) {
            if (token instanceof TokenGroup) {
                return true;
            }
            if (token instanceof final FunctionGroup functionGroup
                && !PARAM_FUNCTION.equalsIgnoreCase(functionGroup.getName())
                && findDatePoint(token.getUnescapedText()) == null) {
                return true;
            }
            if (TokenType.STRING.equals(token.getTokenType())
                && (tokens.size() > 1 || isColumnName(token.getUnescapedText()))) {
                return true;
            }
        }
        return false;
    }

    private boolean isColumnName(final String name) {
        return expressionMap.containsKey(name) || selectNames.contains(name);
    }

    // Gives an expression a name, and keeps it to be parsed when the whole query has been read.
    private String addExpression(final List<AbstractToken> tokens) {
        final StringBuilder text = new StringBuilder();
        tokens.forEach(token -> text.append(token.getText()).append(' '));
        return namesByText.computeIfAbsent(text.toString(), k -> {
            int number = pendingExpressions.size() + 1;
            while (expressionMap.containsKey(NAME_PREFIX + number)) {
                number++;
            }
            final String name = NAME_PREFIX + number;
            pendingExpressions.put(name, List.copyOf(tokens));
            return name;
        });
    }
}
