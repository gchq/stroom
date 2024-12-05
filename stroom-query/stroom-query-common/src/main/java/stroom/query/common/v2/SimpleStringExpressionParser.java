package stroom.query.common.v2;

import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.language.token.AbstractToken;
import stroom.query.language.token.KeywordGroup;
import stroom.query.language.token.StructureBuilder;
import stroom.query.language.token.Token;
import stroom.query.language.token.TokenException;
import stroom.query.language.token.TokenGroup;
import stroom.query.language.token.TokenType;
import stroom.query.language.token.Tokeniser;
import stroom.util.NullSafe;
import stroom.util.shared.GwtNullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class SimpleStringExpressionParser {

    // Add all supported conditions and sort them by longest operator string first so we can match longest prefixes
    // first.
    private static final List<Condition> SUPPORTED_CONDITIONS = Stream
            .of(
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
            .sorted(Comparator.comparingInt(c -> -c.getOperator().length()))
            .toList();

    public static Optional<ExpressionOperator> create(final FieldProvider fieldProvider,
                                                      final String string) {
        if (GwtNullSafe.isBlankString(string)) {
            return Optional.empty();
        }

        char[] chars = string.toCharArray();
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

        final TokenGroup tokenGroup = StructureBuilder.create(tokens);
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
            if (termTokens.isEmpty() && token instanceof final KeywordGroup keywordGroup) {
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
        final List<ExpressionItem> operators = new ArrayList<>();
        final List<AbstractToken> current = new ArrayList<>();
        for (final AbstractToken token : tokens) {
            if (TokenType.WHITESPACE.equals(token.getTokenType())) {
                if (!current.isEmpty()) {
                    final Optional<ExpressionItem> optional = createInnerTerm(current, fieldProvider);
                    optional.ifPresent(operators::add);
                    current.clear();
                }
            } else {
                current.add(token);
            }
        }
        // Add remaining.
        if (!current.isEmpty()) {
            final Optional<ExpressionItem> optional = createInnerTerm(current, fieldProvider);
            optional.ifPresent(operators::add);
        }

        if (operators.isEmpty()) {
            return Optional.empty();
        } else if (operators.size() == 1) {
            return Optional.of(operators.getFirst());
        } else {
            return Optional.of(ExpressionOperator.builder().children(operators).build());
        }
    }

    private static List<AbstractToken> collapseTokenGroups(final List<AbstractToken> tokens) {
        // Collapse remaining token groups.
        final List<AbstractToken> collapsed = new ArrayList<>();
        for (final AbstractToken token : tokens) {
            if (TokenType.isString(token)) {
                collapsed.add(token);
            } else if (token instanceof final TokenGroup tokenGroup) {
                collapsed.add(new Token(TokenType.STRING, token.getChars(), token.getStart(), token.getStart()));
                collapsed.addAll(collapseTokenGroups(tokenGroup.getChildren()));
                collapsed.add(new Token(TokenType.STRING, token.getChars(), token.getEnd(), token.getEnd()));
            } else {
                collapsed.add(new Token(TokenType.STRING, token.getChars(), token.getStart(), token.getEnd()));
            }
        }
        return collapsed;
    }

    private static List<AbstractToken> mergeTokenGroups(final List<AbstractToken> tokens) {
        // Collapse remaining token groups.
        AbstractToken lastToken = null;
        final List<AbstractToken> merged = new ArrayList<>();
        for (final AbstractToken token : tokens) {
            if (lastToken != null) {
                if (lastToken.getTokenType().equals(token.getTokenType())) {
                    // Expand token.
                    lastToken = new Token(
                            lastToken.getTokenType(),
                            lastToken.getChars(),
                            lastToken.getStart(),
                            token.getEnd());
                } else {
                    merged.add(lastToken);
                    lastToken = token;
                }
            } else {
                lastToken = token;
            }
        }
        if (lastToken != null) {
            merged.add(lastToken);
        }
        return merged;
    }

    private static Optional<ExpressionItem> createInnerTerm(final List<AbstractToken> in,
                                                            final FieldProvider fieldProvider) {
        // Collapse remaining token groups.
        List<AbstractToken> tokens = collapseTokenGroups(in);
        tokens = mergeTokenGroups(tokens);

        if (tokens.isEmpty()) {
            return Optional.empty();
        }

        Condition condition = null;
        String value;
        final AbstractToken token = tokens.getFirst();

        if (TokenType.STRING.equals(token.getTokenType())) {
            final String string = token.getUnescapedText();

            // See if the condition is negated.
            if (string.length() > 1 && string.startsWith("!")) {
                final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.NOT);
                final Token t = new Token(TokenType.STRING, token.getChars(), token.getStart() + 1, token.getEnd());
                final List<AbstractToken> remaining = new ArrayList<>(tokens.size());
                remaining.add(t);
                remaining.addAll(tokens.subList(1, tokens.size()));

                final Optional<ExpressionItem> child = createInnerTerm(remaining, fieldProvider);
                if (child.isEmpty()) {
                    return Optional.empty();
                }
                builder.children(Collections.singletonList(child.get()));
                return Optional.of(builder.build());

            } else {
                if (NullSafe.isEmptyString(string)) {
                    return Optional.empty();
                }

                value = string;

                for (Condition c : SUPPORTED_CONDITIONS) {
                    final String operator = c.getOperator();
                    if (string.startsWith(operator)) {
                        condition = c;
                        value = string.substring(operator.length());
                        break;
                    }
                }

                if (condition == null) {
                    if (string.startsWith("~")) {
                        // Characters Anywhere Matching.
                        condition = Condition.MATCHES_REGEX;
                        value = string.substring(1);
                        char[] chars = value.toCharArray();
                        final StringBuilder sb = new StringBuilder();
                        for (final char c : chars) {
                            sb.append(".*?");

                            if (c == '*') {
                                // TODO @AT Why is this * block here
                                sb.append(".*?");
                            } else if (Character.isLetterOrDigit(c)) {
                                sb.append(c);
                            } else {
                                // Might be a special char so escape it
                                sb.append(Pattern.quote(String.valueOf(c)));
                            }
                        }
                        value = sb.toString();
                    } else if (string.startsWith("\\")) {
                        // Escaped contains
                        condition = Condition.CONTAINS;
                        value = string.substring(1);
                    } else {
                        // Contains.
                        condition = Condition.CONTAINS;
                    }
                }

                // Get the field prefix.
                final String fieldPrefix = getFieldPrefix(value);

                // Resolve all fields.
                List<String> fields;
                String fieldName = fieldPrefix;
                // Remove field prefix delimiter.
                if (fieldName.endsWith(":")) {
                    fieldName = fieldName.substring(0, fieldName.length() - 1);
                }
                if (fieldName.isEmpty()) {
                    fields = fieldProvider.getDefaultFields();
                } else {
                    final Optional<String> qualifiedField = fieldProvider.getQualifiedField(fieldName);
                    if (qualifiedField.isEmpty()) {
                        throw new RuntimeException("Unexpected field: " + fieldName);
                    }
                    fields = Collections.singletonList(qualifiedField.get());
                }

                // Resolve the field value.
                String fieldValue = value.substring(fieldPrefix.length());
                fieldValue = fieldValue + concatStringTokens(tokens.subList(1, tokens.size()));

                return createExpressionItem(fields, condition, fieldValue);
            }

        } else {
            final List<String> fields = fieldProvider.getDefaultFields();
            final String fieldValue = concatStringTokens(tokens);

            return createExpressionItem(fields, Condition.CONTAINS, fieldValue);
        }
    }

    private static Optional<ExpressionItem> createExpressionItem(final List<String> fields,
                                                                 final Condition condition,
                                                                 final String fieldValue) {
        if (fields.isEmpty()) {
            throw new RuntimeException("No fields");
        }

        if (fields.size() == 1) {
            return Optional.of(ExpressionTerm
                    .builder()
                    .field(fields.getFirst())
                    .condition(condition)
                    .value(fieldValue)
                    .build());
        }

        final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.OR);
        for (final String field : fields) {
            builder.addTerm(ExpressionTerm
                    .builder()
                    .field(field)
                    .condition(condition)
                    .value(fieldValue)
                    .build());
        }

        return Optional.of(builder.build());
    }

    private static String concatStringTokens(final List<AbstractToken> tokens) {
        final StringBuilder sb = new StringBuilder();
        for (final AbstractToken token : tokens) {
            sb.append(token.getUnescapedText());
        }
        return sb.toString();
    }

    private static String getFieldPrefix(final String string) {
        char[] chars = string.toCharArray();
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

    public interface FieldProvider {

        List<String> getDefaultFields();

        Optional<String> getQualifiedField(String string);
    }
}
