/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.token.AbstractToken;
import stroom.query.api.token.KeywordGroup;
import stroom.query.api.token.Token;
import stroom.query.api.token.TokenException;
import stroom.query.api.token.TokenGroup;
import stroom.query.api.token.TokenType;
import stroom.query.language.token.StructureBuilder;
import stroom.query.language.token.Tokeniser;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class SimpleStringExpressionParser {

    // Add all supported conditions and sort them by longest operator string first, so we can match longest prefixes
    // first.
    private static final List<Condition> SUPPORTED_CONDITIONS = Stream.of(
                    Condition.CONTAINS,
                    Condition.EQUALS,
                    Condition.STARTS_WITH,
                    Condition.ENDS_WITH,
                    Condition.GREATER_THAN,
                    Condition.GREATER_THAN_OR_EQUAL_TO,
                    Condition.LESS_THAN,
                    Condition.LESS_THAN_OR_EQUAL_TO,
                    Condition.MATCHES_REGEX,
                    Condition.WORD_BOUNDARY,
                    Condition.CONTAINS_CASE_SENSITIVE,
                    Condition.EQUALS_CASE_SENSITIVE,
                    Condition.STARTS_WITH_CASE_SENSITIVE,
                    Condition.ENDS_WITH_CASE_SENSITIVE,
                    Condition.MATCHES_REGEX_CASE_SENSITIVE)
            .sorted(Comparator.comparingInt((Condition c) -> c.getOperator().length()).reversed())
            .toList();

    public static Optional<ExpressionOperator> create(final FieldProvider fieldProvider,
                                                      final String string) {
        if (NullSafe.isBlankString(string)) {
            return Optional.empty();
        }

        final char[] chars = string.toCharArray();
        final Token unknown = new Token(TokenType.UNKNOWN, chars, 0, chars.length - 1);

        // Tag quoted strings and comments.
        List<Token> tokens = Collections.singletonList(unknown);
        tokens = Tokeniser.extractQuotedTokens(tokens);
        tokens = Tokeniser.tagKeyword("and", TokenType.AND, tokens);
        tokens = Tokeniser.tagKeyword("or", TokenType.OR, tokens);
        tokens = Tokeniser.tagKeyword("not", TokenType.NOT, tokens);
        // Tag brackets.
        tokens = Tokeniser.split("\\(", 0, TokenType.OPEN_BRACKET, tokens);
        tokens = Tokeniser.split("\\)", 0, TokenType.CLOSE_BRACKET, tokens);
        // Tag whitespace.
        tokens = Tokeniser.split("\\s+", 0, TokenType.WHITESPACE, tokens);
        // Tag everything else as a string.
        tokens = Tokeniser.categorise(TokenType.STRING, tokens);

        final TokenGroup tokenGroup = StructureBuilder.createBasic(tokens);
        return Optional.of(processLogic(tokenGroup.getChildren(), fieldProvider));
    }

    private static ExpressionOperator processLogic(final List<AbstractToken> tokens,
                                                   final FieldProvider fieldProvider) {
        // Replace all term tokens with expression items.
        List<Object> out = gatherTerms(tokens, fieldProvider);

        // Apply NOT operators.
        out = applyNotOperators(out);

        // Apply AND operators.
        out = applyAndOrOperators(out, TokenType.AND, Op.AND);

        // Apply OR operators.
        out = applyAndOrOperators(out, TokenType.OR, Op.OR);

        // Gather final expression items.
        final List<ExpressionItem> list = new ArrayList<>(out.size());
        for (final Object object : out) {
            if (object instanceof final ExpressionItem expressionItem) {
                list.add(expressionItem);
            } else if (object instanceof final AbstractToken token) {
                throw new TokenException(token, "Unexpected token");
            }
        }

        if (list.size() == 1 && list.getFirst() instanceof final ExpressionOperator expressionOperator) {
            return expressionOperator;
        }

        return ExpressionOperator
                .builder()
                .op(Op.AND)
                .children(list)
                .build();
    }


    private static List<Object> gatherTerms(final List<AbstractToken> tokens,
                                            final FieldProvider fieldProvider) {
        final List<Object> out = new ArrayList<>(tokens.size());

        // Gather terms.
        final List<AbstractToken> termTokens = new ArrayList<>();
        for (final AbstractToken token : tokens) {
            if (token.getTokenType().equals(TokenType.WHITESPACE)) {
                if (!termTokens.isEmpty()) {
                    createTerm(termTokens, fieldProvider).ifPresent(out::add);
                    termTokens.clear();
                }
            } else if (termTokens.isEmpty() && token instanceof final KeywordGroup keywordGroup) {
                out.add(processLogic(keywordGroup.getChildren(), fieldProvider));
            } else if (termTokens.isEmpty() && token instanceof final TokenGroup tokenGroup) {
                out.add(processLogic(tokenGroup.getChildren(), fieldProvider));
            } else if (TokenType.AND.equals(token.getTokenType()) ||
                       TokenType.OR.equals(token.getTokenType()) ||
                       TokenType.NOT.equals(token.getTokenType())) {
                if (!termTokens.isEmpty()) {
                    createTerm(termTokens, fieldProvider).ifPresent(out::add);
                    termTokens.clear();
                }
                out.add(token);
            } else {
                termTokens.add(token);
            }
        }
        if (!termTokens.isEmpty()) {
            createTerm(termTokens, fieldProvider).ifPresent(out::add);
        }

        return out;
    }

    private static List<Object> applyNotOperators(final List<Object> in) {
        final List<Object> out = new ArrayList<>(in.size());
        for (int i = 0; i < in.size(); i++) {
            final Object object = in.get(i);
            if (object instanceof final AbstractToken token && TokenType.NOT.equals(token.getTokenType())) {
                // Get next token.
                i++;
                if (i < in.size()) {
                    final Object next = in.get(i);
                    if (next instanceof final ExpressionItem expressionItem) {
                        final ExpressionOperator not = ExpressionOperator
                                .builder()
                                .op(Op.NOT)
                                .children(List.of(expressionItem))
                                .build();
                        out.add(not);
                    } else {
                        throw new TokenException(token, "Expected term after NOT");
                    }
                } else {
                    throw new TokenException(token, "Trailing NOT");
                }

            } else {
                out.add(object);
            }
        }
        return out;
    }

    private static List<Object> applyAndOrOperators(final List<Object> in, final TokenType tokenType, final Op op) {
        final List<Object> out = new ArrayList<>(in.size());
        Object previous = null;
        for (int i = 0; i < in.size(); i++) {
            final Object object = in.get(i);
            if (object instanceof final AbstractToken token && tokenType.equals(token.getTokenType())) {
                if (previous instanceof final ExpressionItem previousExpressionItem) {
                    // Get next token.
                    i++;
                    if (i < in.size()) {
                        final Object next = in.get(i);
                        if (next instanceof final ExpressionItem expressionItem) {
                            previous = ExpressionOperator
                                    .builder()
                                    .op(op)
                                    .children(List.of(previousExpressionItem, expressionItem))
                                    .build();
                        } else {
                            throw new TokenException(token, "Expected term after " + tokenType.name());
                        }
                    } else {
                        throw new TokenException(token, "Trailing " + tokenType.name());
                    }

                } else {
                    throw new TokenException(token, "Expected term before " + tokenType.name());
                }
            } else {
                if (previous != null) {
                    out.add(previous);
                }
                previous = object;
            }
        }
        if (previous != null) {
            out.add(previous);
        }
        return out;
    }

    private static Optional<ExpressionItem> createTerm(final List<AbstractToken> tokens,
                                                       final FieldProvider fieldProvider) {
        // Split tokens into whitespace separated groups and apply AND between them.
        final ExpressionOperator.Builder builder = ExpressionOperator.builder();
        final List<AbstractToken> current = new ArrayList<>();
        for (final AbstractToken token : tokens) {
            if (TokenType.WHITESPACE.equals(token.getTokenType())) {
                if (!current.isEmpty()) {
                    createInnerTerm(current, fieldProvider, builder);
                    current.clear();
                }
            } else {
                current.add(token);
            }
        }
        // Add remaining.
        if (!current.isEmpty()) {
            createInnerTerm(current, fieldProvider, builder);
        }

        final ExpressionOperator operator = builder.build();
        if (!operator.hasChildren()) {
            return Optional.empty();
        } else if (operator.getChildren().size() == 1) {
            return Optional.of(operator.getChildren().getFirst());
        } else {
            return Optional.of(operator);
        }
    }

    private static void createInnerTerm(final List<AbstractToken> in,
                                        final FieldProvider fieldProvider,
                                        final ExpressionOperator.Builder parent) {
        final List<AbstractToken> remaining = new ArrayList<>(in);
        while (!remaining.isEmpty()) {
            final AbstractToken token = remaining.removeFirst();

            Condition condition = null;
            boolean charsAnywhere = false;
            boolean not = false;
            String fieldName = "";
            String fieldValue = "";
            List<String> fields = fieldProvider.getDefaultFields();

            // See if this is a qualifier to get a field name,
            if (TokenType.STRING.equals(token.getTokenType())) {
                fieldValue = token.getUnescapedText();

                // Get the field prefix.
                final String fieldPrefix = getFieldPrefix(fieldValue);
                fieldValue = fieldValue.substring(fieldPrefix.length());

                fieldName = fieldPrefix;
                // Remove field prefix delimiter.
                if (fieldName.endsWith(":")) {
                    fieldName = fieldName.substring(0, fieldName.length() - 1);
                }

                // Resolve all fields.
                if (!fieldName.isEmpty()) {
                    final Optional<String> qualifiedField = fieldProvider.getQualifiedField(fieldName);
                    if (!qualifiedField.isEmpty()) {
                        fields = Collections.singletonList(qualifiedField.get());
                    } else {
                        throw new RuntimeException("Unknown field: " + fieldName);
                    }
                }

                // See if the condition is negated.
                if (fieldValue.length() > 1 && fieldValue.startsWith("!")) {
                    not = true;
                    fieldValue = fieldValue.substring(1);
                }

                // Resolve condition.
                for (final Condition c : SUPPORTED_CONDITIONS) {
                    final String operator = c.getOperator();
                    if (fieldValue.startsWith(operator)) {
                        condition = c;
                        fieldValue = fieldValue.substring(operator.length());
                        break;
                    }
                }
                if (condition == null) {
                    if (fieldValue.startsWith("~")) {
                        // Characters Anywhere Matching.
                        condition = Condition.MATCHES_REGEX;
                        fieldValue = fieldValue.substring(1);
                        charsAnywhere = true;
                    } else if (fieldValue.startsWith("\\")) {
                        // Escaped contains
                        condition = Condition.CONTAINS;
                        fieldValue = fieldValue.substring(1);
                    }
                }
            } else {
                fieldValue += token.getUnescapedText();
            }

            // Add remaining to field value until we hit whitespace.
            while (!remaining.isEmpty() && !remaining.getFirst().getTokenType().equals(TokenType.WHITESPACE)) {
                final AbstractToken next = remaining.removeFirst();
                fieldValue += next.getUnescapedText();
            }

            // If this is a chars anywhere condition then we need to alter the value so that we can use a regex for
            // chars anywhere matching.
            if (charsAnywhere) {
                final char[] chars = fieldValue.toCharArray();
                final StringBuilder sb = new StringBuilder();
                for (final char c : chars) {
                    if (sb.length() > 0) {
                        sb.append(".*?");
                    }
                    if (Character.isLetterOrDigit(c)) {
                        sb.append(c);
                    } else {
                        // Might be a special char so escape it
                        sb.append(Pattern.quote(String.valueOf(c)));
                    }
                }
                fieldValue = sb.toString();
            }

            if (!fields.isEmpty()) {
                if (condition == null) {
                    condition = Condition.CONTAINS;
                }

                if (not) {
                    final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.NOT);
                    addTerms(fields, condition, fieldValue, builder);
                    final ExpressionOperator notOperator = builder.build();
                    if (notOperator.hasChildren()) {
                        parent.addOperator(notOperator);
                    }
                } else {
                    addTerms(fields, condition, fieldValue, parent);
                }
            }
        }
    }

    private static void addTerms(final List<String> fields,
                                 final Condition condition,
                                 final String fieldValue,
                                 final ExpressionOperator.Builder parent) {
        if (fields.size() == 1) {
            parent.addTerm(ExpressionTerm
                    .builder()
                    .field(fields.getFirst())
                    .condition(condition)
                    .value(fieldValue)
                    .build());
        } else {
            final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.OR);
            for (final String field : fields) {
                builder.addTerm(ExpressionTerm
                        .builder()
                        .field(field)
                        .condition(condition)
                        .value(fieldValue)
                        .build());
            }
            parent.addOperator(builder.build());
        }
    }

    private static String getFieldPrefix(final String string) {
        final char[] chars = string.toCharArray();
        final StringBuilder sb = new StringBuilder();
        boolean escape = false;
        for (final char c : chars) {
            if (c == '\\') {
                escape = !escape;
                sb.append(c);

            } else {
                sb.append(c);
                if (!escape) {
                    if (c == ':') {
                        return sb.toString();
                    }
                }
                escape = false;
            }
        }
        return "";
    }


    // --------------------------------------------------------------------------------


    public interface FieldProvider {

        List<String> getDefaultFields();

        Optional<String> getQualifiedField(String string);
    }
}
