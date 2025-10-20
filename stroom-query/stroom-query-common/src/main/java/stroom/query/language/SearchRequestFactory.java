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

package stroom.query.language;

import stroom.docref.DocRef;
import stroom.query.api.Column;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.GroupSelection;
import stroom.query.api.HoppingWindow;
import stroom.query.api.IncludeExcludeFilter;
import stroom.query.api.ParamUtil;
import stroom.query.api.Query;
import stroom.query.api.ResultRequest;
import stroom.query.api.ResultRequest.Fetch;
import stroom.query.api.ResultRequest.ResultStyle;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchRequestSource;
import stroom.query.api.Sort;
import stroom.query.api.Sort.SortDirection;
import stroom.query.api.SpecialColumns;
import stroom.query.api.TableSettings;
import stroom.query.api.Window;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.api.datasource.QueryFieldProvider;
import stroom.query.api.token.AbstractToken;
import stroom.query.api.token.FunctionGroup;
import stroom.query.api.token.KeywordGroup;
import stroom.query.api.token.Token;
import stroom.query.api.token.TokenException;
import stroom.query.api.token.TokenGroup;
import stroom.query.api.token.TokenType;
import stroom.query.common.v2.CompiledWindow;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.common.v2.DateExpressionParser.DatePoint;
import stroom.query.language.functions.Expression;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.ExpressionParser;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ParamFactory;
import stroom.query.language.token.StructureBuilder;
import stroom.query.language.token.Tokeniser;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SearchRequestFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SearchRequestFactory.class);

    public static final String TABLE_COMPONENT_ID = "table";
    public static final String VIS_COMPONENT_ID = "vis";

    private final VisualisationTokenConsumer visualisationTokenConsumer;
    private final DocResolver docResolver;
    private final Provider<QueryFieldProvider> queryFieldProviderProvider;
    private final SecurityContext securityContext;

    @Inject
    public SearchRequestFactory(final VisualisationTokenConsumer visualisationTokenConsumer,
                                final DocResolver docResolver,
                                final Provider<QueryFieldProvider> queryFieldProviderProvider,
                                final SecurityContext securityContext) {
        this.visualisationTokenConsumer = visualisationTokenConsumer;
        this.docResolver = docResolver;
        this.queryFieldProviderProvider = queryFieldProviderProvider;
        this.securityContext = securityContext;
    }

    public void extractDataSourceOnly(final String string,
                                      final Consumer<DocRef> consumer) {
        new Builder(visualisationTokenConsumer, docResolver, queryFieldProviderProvider, securityContext)
                .extractDataSourceOnly(string, consumer);
    }

    public SearchRequest create(final String string,
                                final SearchRequest in,
                                final ExpressionContext expressionContext) {
        return new Builder(visualisationTokenConsumer, docResolver, queryFieldProviderProvider, securityContext)
                .create(string, in, expressionContext);
    }


    // --------------------------------------------------------------------------------


    private static class Builder {

        private final VisualisationTokenConsumer visualisationTokenConsumer;
        private final DocResolver docResolver;
        private final Provider<QueryFieldProvider> queryFieldProviderProvider;
        private final SecurityContext securityContext;

        private ExpressionContext expressionContext;
        private final FieldIndex fieldIndex;
        private Map<String, String> paramMap;
        private final Map<String, Expression> expressionMap;
        private final Set<String> addedFields = new HashSet<>();
        private final List<AbstractToken> additionalFields = new ArrayList<>();
        private boolean inHaving;
        private Optional<CompiledWindow> optionalCompiledWindow = Optional.empty();

        Builder(final VisualisationTokenConsumer visualisationTokenConsumer,
                final DocResolver docResolver,
                final Provider<QueryFieldProvider> queryFieldProviderProvider,
                final SecurityContext securityContext) {
            this.visualisationTokenConsumer = visualisationTokenConsumer;
            this.docResolver = docResolver;
            this.queryFieldProviderProvider = queryFieldProviderProvider;
            this.securityContext = securityContext;
            this.fieldIndex = new FieldIndex();
            this.paramMap = Collections.emptyMap();
            this.expressionMap = new HashMap<>();
        }

        void extractDataSourceOnly(final String string, final Consumer<DocRef> consumer) {
            // Get a list of tokens.
            final List<Token> tokens = Tokeniser.parse(string);


            if (tokens.isEmpty()) {
                throw new TokenException(null, "No tokens");
            }

            // Create structure.
            final TokenGroup tokenGroup = StructureBuilder.create(tokens);

            // Assume we have a root bracket group.
            final List<AbstractToken> childTokens = tokenGroup.getChildren();

            // Add data source.
            addDataSource(childTokens, consumer, true);
        }

        SearchRequest create(final String string,
                             final SearchRequest in,
                             final ExpressionContext expressionContext) {
            try {
                Objects.requireNonNull(in, "Null sample request");
                Objects.requireNonNull(expressionContext, "Null expression context");
                Objects.requireNonNull(expressionContext.getDateTimeSettings(), "Null date time settings");

                // Set the expression context.
                this.expressionContext = expressionContext;

                // Get a list of tokens.
                final List<Token> tokens = Tokeniser.parse(string);
                if (tokens.isEmpty()) {
                    throw new TokenException(null, "No tokens");
                }

                // Create structure.
                final TokenGroup tokenGroup = StructureBuilder.create(tokens);

                // Assume we have a root bracket group.
                final List<AbstractToken> childTokens = tokenGroup.getChildren();

                final Query.Builder queryBuilder = Query.builder();
                if (in.getQuery() != null) {
                    paramMap = ParamUtil.createParamMap(in.getQuery().getParams());
                    queryBuilder.params(in.getQuery().getParams());
                    queryBuilder.timeRange(in.getQuery().getTimeRange());
                }

                // Keep track of consumed tokens to check token order.
                final List<TokenType> consumedTokens = new ArrayList<>();

                // Create result requests.
                final List<ResultRequest> resultRequests = addTableSettings(
                        in.getSearchRequestSource(), childTokens, consumedTokens, queryBuilder);

                // Try to make a query.
                Query query = queryBuilder.build();
                if (query.getDataSource() == null) {
                    throw new TokenException(null, "No data source has been specified.");
                }

                // Make sure there is a non-null expression.
                if (query.getExpression() == null) {
                    query = query.copy()
                            .expression(ExpressionOperator.builder().build())
                            .build();
                }

                return new SearchRequest(
                        in.getSearchRequestSource(),
                        in.getKey(),
                        query,
                        resultRequests,
                        in.getDateTimeSettings(),
                        in.incremental(),
                        in.getTimeout());

            } catch (final Throwable e) {
                LOGGER.debug(() -> "Error creating search request from '" + string + "'", e);
                throw e;
            }
        }

        /**
         * @param isLenient Ignores any additional child tokens found. This is to support getting just
         *                  the data source from a partially written query, which is likely to not be
         *                  a valid query. For example, you may type
         *                  <pre>{@code from View sel}</pre> and at this point do code completion on 'sel'
         *                  so if this method is called it will treat 'sel as an extra child.
         */
        private List<AbstractToken> addDataSource(final List<AbstractToken> tokens,
                                                  final Consumer<DocRef> consumer,
                                                  final boolean isLenient) {
            final AbstractToken token = tokens.getFirst();

            // The first token must be `FROM`.
            if (!TokenType.FROM.equals(token.getTokenType())) {
                throw new TokenException(token, "Expected from");
            }

            final KeywordGroup keywordGroup = (KeywordGroup) token;
            if (keywordGroup.getChildren().isEmpty()) {
                throw new TokenException(token, "Expected data source value");
            } else if (keywordGroup.getChildren().size() > 1 && !isLenient) {
                throw new TokenException(keywordGroup.getChildren().get(1),
                        "Unexpected data source child tokens: " + keywordGroup.getChildren()
                                .stream()
                                .map(token2 -> token2.getTokenType() + ":[" + token2.getText() + "]")
                                .collect(Collectors.joining(", ")));
            }

            final AbstractToken dataSourceToken = keywordGroup.getChildren().get(0);
            if (!TokenType.isString(dataSourceToken)) {
                throw new TokenException(dataSourceToken, "Expected a token of type string");
            }
            final String dataSourceName = dataSourceToken.getUnescapedText();
            final DocRef dataSourceDocRef = securityContext.useAsReadResult(() ->
                    docResolver.resolveDataSourceRef(dataSourceName));

            consumer.accept(dataSourceDocRef);

            return tokens.subList(1, tokens.size());
        }

        private List<AbstractToken> addExpression(final List<AbstractToken> tokens,
                                                  final List<TokenType> consumedTokens,
                                                  final Set<TokenType> allowConsumed,
                                                  final TokenType keyword,
                                                  final Consumer<ExpressionOperator> expressionConsumer) {
            final List<AbstractToken> whereGroup = new ArrayList<>();
            final List<TokenType> localConsumedTokens = new ArrayList<>(consumedTokens);
            final Set<TokenType> allowFollowing = new HashSet<>(allowConsumed);
            int i = 0;
            for (; i < tokens.size(); i++) {
                final AbstractToken token = tokens.get(i);
                if (token instanceof final KeywordGroup keywordGroup) {
                    final TokenType tokenType = keywordGroup.getTokenType();
                    if (keyword.equals(tokenType)) {
                        // Make sure we haven't already consumed the keyword token.
                        checkTokenOrder(token, localConsumedTokens, Set.of(TokenType.FROM), allowConsumed);
                        localConsumedTokens.add(tokenType);
                        consumedTokens.add(tokenType);
                        allowFollowing.add(tokenType);
                        allowFollowing.addAll(TokenType.ALL_LOGICAL_OPERATORS);
                        whereGroup.add(keywordGroup);
                    } else if (TokenType.ALL_LOGICAL_OPERATORS.contains(tokenType)) {
                        // Check we have already consumed the expected keyword token.
                        checkTokenOrder(token, localConsumedTokens, Set.of(TokenType.FROM, keyword), allowFollowing);
                        localConsumedTokens.add(tokenType);
                        whereGroup.add(keywordGroup);
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }

            if (!whereGroup.isEmpty()) {
                final ExpressionOperator expressionOperator = processLogic(whereGroup);
                // Simplify expression.
                final ExpressionOperator simplifiedExpression = ExpressionUtil.simplify(expressionOperator);
                expressionConsumer.accept(simplifiedExpression);
            } else {
                expressionConsumer.accept(ExpressionOperator.builder().build());
            }

            if (i < tokens.size()) {
                return tokens.subList(i, tokens.size());
            }
            return Collections.emptyList();
        }


        private ExpressionTerm createTerm(
                final List<AbstractToken> tokens) {
            final ExpressionTerm expressionTerm;

            if (NullSafe.isEmptyCollection(tokens)) {
                throw new RuntimeException("createTerm called with empty list");
            } else if (tokens.size() < 3) {
                throw new TokenException(tokens.getFirst(), "Incomplete term");
            }

            final AbstractToken fieldToken = tokens.get(0);
            final AbstractToken conditionToken = tokens.get(1);

            if (!TokenType.isString(fieldToken) && !TokenType.PARAM.equals(fieldToken.getTokenType())) {
                throw new TokenException(fieldToken, "Expected field string");
            }
            if (!TokenType.ALL_CONDITIONS.contains(conditionToken.getTokenType())) {
                throw new TokenException(conditionToken, "Expected condition token");
            }

            if (TokenType.IN.equals(conditionToken.getTokenType())) {
                expressionTerm = createInTerm(tokens);

            } else {
                final String field = fieldToken.getUnescapedText();
                final StringBuilder value = new StringBuilder();
                int end = addValue(tokens, value, 2);

                if (TokenType.BETWEEN.equals(conditionToken.getTokenType())) {
                    if (tokens.size() == end) {
                        throw new TokenException(tokens.getLast(), "Expected between and");
                    }

                    final AbstractToken betweenAnd = tokens.get(end);
                    if (!TokenType.BETWEEN_AND.equals(betweenAnd.getTokenType())) {
                        throw new TokenException(betweenAnd, "Expected between and");
                    }
                    value.append(", ");
                    end = addValue(tokens, value, end + 1);
                }

                // Check we consumed all tokens to get the value(s).
                if (end < tokens.size()) {
                    throw new TokenException(tokens.get(end), "Unexpected token");
                }

                // If we have a where clause then we expect the next token to contain an expression.
                final Condition cond;
                switch (conditionToken.getTokenType()) {
                    case EQUALS -> cond = Condition.EQUALS;
                    case NOT_EQUALS -> cond = Condition.NOT_EQUALS;
                    case GREATER_THAN -> cond = Condition.GREATER_THAN;
                    case GREATER_THAN_OR_EQUAL_TO -> cond = Condition.GREATER_THAN_OR_EQUAL_TO;
                    case LESS_THAN -> cond = Condition.LESS_THAN;
                    case LESS_THAN_OR_EQUAL_TO -> cond = Condition.LESS_THAN_OR_EQUAL_TO;
                    case IS_NULL -> cond = Condition.IS_NULL;
                    case IS_NOT_NULL -> cond = Condition.IS_NOT_NULL;
                    case BETWEEN -> cond = Condition.BETWEEN;
                    default -> throw new TokenException(conditionToken, "Unknown condition: " + conditionToken);
                }

                expressionTerm = ExpressionTerm
                        .builder()
                        .field(field)
                        .condition(cond)
                        .value(value.toString().trim())
                        .build();
            }

            if (inHaving) {
                // Remember the tokens used for having clauses as we need to ensure they are added.
                additionalFields.add(fieldToken);
                if (tokens.size() >= 3) {
                    final AbstractToken valueToken = tokens.get(2);
                    if (TokenType.STRING.equals(valueToken.getTokenType()) ||
                        TokenType.PARAM.equals(valueToken.getTokenType())) {
                        additionalFields.add(valueToken);
                    }
                }
            }

            return expressionTerm;
        }

        private ExpressionTerm createInTerm(
                final List<AbstractToken> tokens) {
            final ExpressionTerm expressionTerm;

            final AbstractToken fieldToken = tokens.get(0);
            final AbstractToken conditionToken = tokens.get(1);
            final AbstractToken valueToken = tokens.get(2);

            if (TokenType.DICTIONARY.equals(valueToken.getTokenType())) {
                if (tokens.size() < 4) {
                    throw new TokenException(valueToken, "Expected dictionary name");
                }
                if (tokens.size() > 4) {
                    throw new TokenException(tokens.get(4), "Unexpected token");
                }
                final AbstractToken dictionaryNameToken = tokens.get(3);
                final Set<TokenType> stringTypes = Set.of(
                        TokenType.SINGLE_QUOTED_STRING,
                        TokenType.DOUBLE_QUOTED_STRING,
                        TokenType.STRING);
                if (!stringTypes.contains(dictionaryNameToken.getTokenType())) {
                    throw new TokenException(dictionaryNameToken, "Expected dictionary name not " +
                                                                  dictionaryNameToken.getTokenType() +
                                                                  " token");
                }
                final String field = fieldToken.getUnescapedText();
                final String dictionaryName = dictionaryNameToken.getUnescapedText().trim();
                final DocRef dictionaryRef;
                try {
                    dictionaryRef = docResolver.resolveDocRef("Dictionary", dictionaryName);
                } catch (final RuntimeException e) {
                    throw new TokenException(dictionaryNameToken, e.getMessage());
                }

                expressionTerm = ExpressionTerm
                        .builder()
                        .field(field)
                        .condition(Condition.IN_DICTIONARY)
                        .docRef(dictionaryRef)
                        .build();

            } else if (valueToken instanceof final TokenGroup tokenGroup) {
                if (tokens.size() > 3) {
                    throw new TokenException(tokens.get(3), "Unexpected token");
                }

                final Set<TokenType> allowedTypes = Set.of(
                        TokenType.SINGLE_QUOTED_STRING,
                        TokenType.DOUBLE_QUOTED_STRING,
                        TokenType.STRING,
                        TokenType.NUMBER,
                        TokenType.DATE_TIME);
                final List<AbstractToken> children = tokenGroup.getChildren();
                final StringBuilder sb = new StringBuilder();
                AbstractToken lastToken = null;
                for (final AbstractToken token : children) {
                    if (TokenType.COMMA.equals(token.getTokenType())) {
                        if (lastToken == null) {
                            throw new TokenException(token, "Unexpected leading comma");
                        } else if (TokenType.COMMA.equals(lastToken.getTokenType())) {
                            throw new TokenException(token, "Unexpected comma");
                        }
                    } else if (allowedTypes.contains(token.getTokenType())) {
                        if (lastToken != null) {
                            if (!TokenType.COMMA.equals(lastToken.getTokenType())) {
                                throw new TokenException(token, "Expected comma delimited");
                            }
                            sb.append(", ");
                        }
                        sb.append(token.getUnescapedText());
                    } else {
                        throw new TokenException(token, "Unexpected token");
                    }
                    lastToken = token;
                }
                if (lastToken != null && TokenType.COMMA.equals(lastToken.getTokenType())) {
                    throw new TokenException(lastToken, "Unexpected trailing comma");
                }

                final String field = fieldToken.getUnescapedText();
                expressionTerm = ExpressionTerm
                        .builder()
                        .field(field)
                        .condition(Condition.IN)
                        .value(sb.toString())
                        .build();

            } else {
                throw new TokenException(valueToken, "Expected parentheses after IN clause");
            }

            return expressionTerm;
        }

        private int addValue(final List<AbstractToken> tokens,
                             final StringBuilder value,
                             final int start) {
            for (int i = start; i < tokens.size(); i++) {
                final AbstractToken token = tokens.get(i);
                if (TokenType.BETWEEN_AND.equals(token.getTokenType())) {
                    final String val = parseValueTokens(tokens.subList(start, i));
                    if (val != null) {
                        value.append(val);
                    }
                    return i;
                }
            }

            final String val = parseValueTokens(tokens.subList(start, tokens.size()));
            if (val != null) {
                value.append(val);
            }
            return tokens.size();
        }

        private String parseValueTokens(final List<AbstractToken> tokens) {
            if (tokens.isEmpty()) {
                return "";
            }

            boolean dateExpression = false;
            boolean numericExpression = false;
            final StringBuilder sb = new StringBuilder();
            for (final AbstractToken token : tokens) {
                if (TokenType.FUNCTION_GROUP.equals(token.getTokenType())) {
                    final FunctionGroup functionGroup = (FunctionGroup) token;
                    DatePoint foundFunction = null;
                    if (functionGroup.getName().equalsIgnoreCase("param")) {
                        return resolveParam(functionGroup);

                    } else {
                        final String function = token.getUnescapedText();
                        for (final DatePoint datePoint : DatePoint.values()) {
                            if (datePoint.getFunction().equals(function)) {
                                foundFunction = datePoint;
                                break;
                            }
                        }
                        if (foundFunction == null) {
                            throw new TokenException(token, "Unexpected function in value");
                        } else {
                            dateExpression = true;
                        }
                    }
                } else if (TokenType.DURATION.equals(token.getTokenType())) {
                    dateExpression = true;
                } else if (TokenType.NUMBER.equals(token.getTokenType())) {
                    numericExpression = true;
                }

                sb.append(token.getUnescapedText());
            }

            final String expression = sb.toString();
            if (dateExpression) {
                DateExpressionParser.parse(tokens, expressionContext.getDateTimeSettings());

            } else if (numericExpression) {
                boolean seenSign = false;
                boolean seenNumber = false;
                for (final AbstractToken token : tokens) {
                    if (TokenType.PLUS.equals(token.getTokenType()) ||
                        TokenType.MINUS.equals(token.getTokenType())) {
                        if (seenSign || seenNumber) {
                            throw new TokenException(token, "Unexpected token");
                        }
                        seenSign = true;
                    } else if (TokenType.NUMBER.equals(token.getTokenType())) {
                        if (seenNumber) {
                            throw new TokenException(token, "Unexpected token");
                        }
                        seenNumber = true;
                    } else {
                        throw new TokenException(token, "Unexpected token");
                    }
                }
            } else if (tokens.size() > 1) {
                throw new TokenException(tokens.get(1), "Unexpected token");
            }

            return expression;
        }

        private String resolveParam(final FunctionGroup functionGroup) {
            if (functionGroup.getChildren().isEmpty()) {
                throw new TokenException(functionGroup, "Expected param name");
            } else if (functionGroup.getChildren().size() > 1) {
                throw new TokenException(functionGroup.getChildren().get(1), "Unexpected token");
            } else {
                final AbstractToken child = functionGroup.getChildren().getFirst();
                if (!TokenType.STRING.equals(child.getTokenType())) {
                    throw new TokenException(child, "Expected param name");
                }
                return paramMap.get(child.getUnescapedText());
            }
        }

        private ExpressionOperator processLogic(final List<AbstractToken> tokens) {
            // Replace all term tokens with expression items.
            List<Object> out = gatherTerms(tokens);

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

        private List<Object> gatherTerms(final List<AbstractToken> tokens) {
            final List<Object> out = new ArrayList<>(tokens.size());

            // Gather terms.
            final List<AbstractToken> termTokens = new ArrayList<>();
            for (final AbstractToken token : tokens) {
                if (termTokens.isEmpty() && token instanceof final KeywordGroup keywordGroup) {
                    out.add(processLogic(keywordGroup.getChildren()));
                } else if (termTokens.isEmpty() && token instanceof final TokenGroup tokenGroup) {
                    out.add(processLogic(tokenGroup.getChildren()));
                } else if (TokenType.AND.equals(token.getTokenType()) ||
                           TokenType.OR.equals(token.getTokenType()) ||
                           TokenType.NOT.equals(token.getTokenType())) {
                    if (!termTokens.isEmpty()) {
                        out.add(createTerm(termTokens));
                        termTokens.clear();
                    }
                    out.add(token);
                } else {
                    termTokens.add(token);
                }
            }
            if (!termTokens.isEmpty()) {
                out.add(createTerm(termTokens));
            }

            return out;
        }

        private List<Object> applyNotOperators(final List<Object> in) {
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

        private List<Object> applyAndOrOperators(final List<Object> in, final TokenType tokenType, final Op op) {
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

        private List<ResultRequest> addTableSettings(final SearchRequestSource searchRequestSource,
                                                     final List<AbstractToken> tokens,
                                                     final List<TokenType> consumedTokens,
                                                     final Query.Builder queryBuilder) {
            final List<ResultRequest> resultRequests = new ArrayList<>();

            final Map<String, Sort> sortMap = new HashMap<>();
            final Map<String, Integer> groupMap = new HashMap<>();
            final Map<String, IncludeExcludeFilter> filterMap = new HashMap<>();
            int groupDepth = 0;

            final TableSettings.Builder tableSettingsBuilder = TableSettings.builder();
            TableSettings visTableSettings = null;

            List<AbstractToken> remaining = new LinkedList<>(tokens);
            while (!remaining.isEmpty()) {
                final AbstractToken token = remaining.getFirst();

                if (token instanceof final KeywordGroup keywordGroup) {
                    switch (keywordGroup.getTokenType()) {
                        case FROM -> {
                            checkTokenOrder(token, consumedTokens);
                            remaining = addDataSource(remaining, queryBuilder::dataSource, false);
                        }
                        case WHERE -> {
                            checkTokenOrder(token, consumedTokens);
                            remaining =
                                    addExpression(remaining,
                                            consumedTokens,
                                            Set.of(TokenType.FROM),
                                            TokenType.WHERE,
                                            queryBuilder::expression);
                        }
                        case EVAL -> {
                            checkTokenOrder(token, consumedTokens);
                            processEval(keywordGroup);
                            remaining.removeFirst();
                        }
                        case WINDOW -> {
                            checkTokenOrder(token, consumedTokens);
                            processWindow(
                                    keywordGroup,
                                    tableSettingsBuilder);
                            remaining.removeFirst();
                        }
                        case FILTER -> {
                            checkTokenOrder(token, consumedTokens);
                            remaining =
                                    addExpression(remaining,
                                            consumedTokens,
                                            Set.of(
                                                    TokenType.FROM,
                                                    TokenType.WHERE,
                                                    TokenType.EVAL,
                                                    TokenType.WINDOW),
                                            TokenType.FILTER,
                                            tableSettingsBuilder::valueFilter);
                        }
                        case SORT -> {
                            checkTokenOrder(token, consumedTokens);
                            processSortBy(
                                    keywordGroup,
                                    sortMap);
                            remaining.removeFirst();
                        }
                        case GROUP -> {
                            checkTokenOrder(token, consumedTokens);
                            processGroupBy(
                                    keywordGroup,
                                    groupMap,
                                    groupDepth);
                            groupDepth++;
                            remaining.removeFirst();
                        }
                        case HAVING -> {
                            inHaving = true;
                            checkTokenOrder(token, consumedTokens);
                            remaining =
                                    addExpression(remaining,
                                            consumedTokens,
                                            inverse(Set.of(TokenType.LIMIT, TokenType.SELECT, TokenType.SHOW)),
                                            TokenType.HAVING,
                                            tableSettingsBuilder::aggregateFilter);
                            inHaving = false;
                        }
                        case SELECT -> {
                            checkTokenOrder(token, consumedTokens);
                            processSelect(
                                    keywordGroup,
                                    sortMap,
                                    groupMap,
                                    filterMap,
                                    tableSettingsBuilder,
                                    queryBuilder);
                            remaining.removeFirst();
                        }
                        case LIMIT -> {
                            checkTokenOrder(token, consumedTokens);
                            processLimit(
                                    keywordGroup,
                                    tableSettingsBuilder);
                            remaining.removeFirst();
                        }
                        case SHOW -> {
                            checkTokenOrder(token, consumedTokens);
                            final TableSettings parentTableSettings = tableSettingsBuilder.build();
                            visTableSettings = visualisationTokenConsumer
                                    .processVis(keywordGroup, parentTableSettings);
                            remaining.removeFirst();
                        }
                        default -> throw new TokenException(token, "Unexpected token");
                    }
                } else {
                    throw new TokenException(token, "Unexpected token");
                }

                consumedTokens.add(token.getTokenType());
            }

            // Ensure StreamId and EventId fields exist if there is no grouping.
            if (groupDepth == 0) {
                if (!addedFields.contains(SpecialColumns.RESERVED_ID)) {
                    tableSettingsBuilder.addColumns(SpecialColumns.RESERVED_ID_COLUMN);
                    addedFields.add(SpecialColumns.RESERVED_ID);
                }
                if (!addedFields.contains(SpecialColumns.RESERVED_STREAM_ID)) {
                    tableSettingsBuilder.addColumns(SpecialColumns.RESERVED_STREAM_ID_COLUMN);
                    addedFields.add(SpecialColumns.RESERVED_STREAM_ID);
                }
                if (!addedFields.contains(SpecialColumns.RESERVED_EVENT_ID)) {
                    tableSettingsBuilder.addColumns(SpecialColumns.RESERVED_EVENT_ID_COLUMN);
                    addedFields.add(SpecialColumns.RESERVED_EVENT_ID);
                }
            }

            // Add missing fields if needed.
            for (final AbstractToken token : additionalFields) {
                final String fieldName = token.getUnescapedText();
                if (!addedFields.contains(fieldName)) {
                    final String id = "__" + fieldName.replaceAll("\\s", "_") + "__";
                    tableSettingsBuilder.addColumns(createColumn(token,
                            id,
                            fieldName,
                            fieldName,
                            false,
                            true,
                            sortMap,
                            groupMap,
                            filterMap));
                }
            }

            final TableSettings tableSettings = tableSettingsBuilder
                    .extractValues(true)
                    .build();

            final ResultRequest tableResultRequest = ResultRequest.builder()
                    .componentId(TABLE_COMPONENT_ID)
                    .searchRequestSource(searchRequestSource)
                    .mappings(Collections.singletonList(tableSettings))
                    .resultStyle(ResultStyle.TABLE)
                    .fetch(Fetch.ALL)
                    .groupSelection(new GroupSelection())
                    .build();
            resultRequests.add(tableResultRequest);

            if (visTableSettings != null) {
                final List<TableSettings> tableSettingsList = new ArrayList<>();
                tableSettingsList.add(tableSettings);
                tableSettingsList.add(visTableSettings);
                final ResultRequest qlVisResultRequest = ResultRequest.builder()
                        .componentId(VIS_COMPONENT_ID)
                        .mappings(tableSettingsList)
                        .resultStyle(ResultStyle.QL_VIS)
                        .fetch(Fetch.ALL)
                        .groupSelection(new GroupSelection())
                        .build();
                resultRequests.add(qlVisResultRequest);
            }
            return resultRequests;
        }

        private void processWindow(final KeywordGroup keywordGroup,
                                   final TableSettings.Builder builder) {
            final List<AbstractToken> children = new ArrayList<>(keywordGroup.getChildren());

            final HoppingWindow.Builder hoppingWindowBuilder = HoppingWindow.builder();

            // Get field name.
            if (!children.isEmpty()) {
                final AbstractToken token = children.getFirst();
                if (!TokenType.isString(token)) {
                    throw new TokenException(token, "Syntax exception");
                }
                final String field = token.getUnescapedText();
                hoppingWindowBuilder.timeField(field);
                children.removeFirst();
            } else {
                throw new TokenException(keywordGroup, "Expected field");
            }

            // Get `by` and duration.
            final int byIndex = getTokenIndex(children, token -> TokenType.BY.equals(token.getTokenType()));
            if (byIndex == -1) {
                throw new TokenException(keywordGroup, "Syntax exception, expected by");
            } else if (children.size() > byIndex + 1) {
                final AbstractToken token = children.get(byIndex + 1);
                if (!TokenType.DURATION.equals(token.getTokenType())) {
                    throw new TokenException(token, "Syntax exception, expected valid window duration");
                }
                final String durationString = token.getUnescapedText();
                hoppingWindowBuilder.windowSize(durationString);
                hoppingWindowBuilder.advanceSize(durationString);

                // We found the duration so remove the tokens.
                children.remove(byIndex + 1);
                children.remove(byIndex);
            } else {
                throw new TokenException(children.get(byIndex), "Syntax exception, expected window duration");
            }

            // Get `advance` and duration.
            final int advanceIndex = getTokenIndex(children, token -> TokenType.isString(token) &&
                                                                      token.getUnescapedText().equalsIgnoreCase(
                                                                              "advance"));
            if (advanceIndex != -1) {
                if (children.size() > advanceIndex + 1) {
                    final AbstractToken token = children.get(advanceIndex + 1);
                    if (!TokenType.DURATION.equals(token.getTokenType())) {
                        throw new TokenException(token, "Syntax exception, expected valid advance duration");
                    }
                    final String advanceString = token.getUnescapedText();
                    hoppingWindowBuilder.advanceSize(advanceString);

                    // We found the duration so remove the tokens.
                    children.remove(advanceIndex + 1);
                    children.remove(advanceIndex);
                } else {
                    throw new TokenException(children.get(advanceIndex), "Syntax exception, expected advance duration");
                }
            }

            // Get `using` and function.
            final int usingIndex = getTokenIndex(children, token -> TokenType.isString(token) &&
                                                                    token.getUnescapedText().equalsIgnoreCase("using"));
            if (usingIndex != -1) {
                if (children.size() > usingIndex + 1) {
                    final AbstractToken token = children.get(usingIndex + 1);
//                    if (!TokenType.FUNCTION_GROUP.equals(token.getTokenType())) {
//                        throw new TokenException(token, "Syntax exception, expected valid using function");
//                    }
                    final String function = token.getUnescapedText();
                    hoppingWindowBuilder.function(function);

                    // We found the duration so remove the tokens.
                    children.remove(usingIndex + 1);
                    children.remove(usingIndex);
                } else {
                    throw new TokenException(children.get(usingIndex), "Syntax exception, expected using function");
                }
            }

            if (!children.isEmpty()) {
                throw new TokenException(children.getFirst(), "Unexpected token");
            }

            final Window window = hoppingWindowBuilder.build();
            final CompiledWindow compiledWindow = CompiledWindow.create(window);
            // Add time window fields, so they can be used by other expressions.
            compiledWindow.addWindowFields(expressionContext, fieldIndex, expressionMap);
            optionalCompiledWindow = Optional.of(compiledWindow);
            builder.window(window);
        }

        private void processEval(final KeywordGroup keywordGroup) {
            final List<AbstractToken> children = keywordGroup.getChildren();

            // Check we have a variable name.
            if (children.isEmpty()) {
                throw new TokenException(keywordGroup, "Expected variable name following eval");
            }
            final AbstractToken variableToken = children.getFirst();
            if (!TokenType.isString(variableToken)) {
                throw new TokenException(variableToken, "Expected variable name");
            }
            final String variable = variableToken.getUnescapedText();

            // Check we have equals operator.
            if (children.size() == 1) {
                throw new TokenException(variableToken, "Expected equals");
            }
            final AbstractToken equalsToken = children.get(1);
            if (!TokenType.EQUALS.equals(equalsToken.getTokenType())) {
                throw new TokenException(equalsToken, "Expected equals");
            }

            // Check we have a function expression.
            if (children.size() <= 2) {
                throw new TokenException(equalsToken, "Expected eval expression");
            }

            // Parse the expression to check it is valid.
            final List<AbstractToken> expressionTokens = children.subList(2, children.size());
            final ExpressionParser expressionParser = new ExpressionParser(new ParamFactory(expressionMap));
            try {
                final Expression expression = expressionParser.parse(expressionContext, fieldIndex, expressionTokens);
                expressionMap.put(variable, expression);
            } catch (final ParseException e) {
                throw new TokenException(keywordGroup, e.getMessage());
            }
        }

        private void processSelect(final KeywordGroup keywordGroup,
                                   final Map<String, Sort> sortMap,
                                   final Map<String, Integer> groupMap,
                                   final Map<String, IncludeExcludeFilter> filterMap,
                                   final TableSettings.Builder tableSettingsBuilder,
                                   final Query.Builder queryBuilder) {
            final List<AbstractToken> children = keywordGroup.getChildren();
            AbstractToken fieldToken = null;
            Expression fieldExpression = null;
            String columnName = null;
            boolean afterAs = false;
            boolean doneAs = false;
            final Map<String, AtomicInteger> columnCount = new HashMap<>();
            final List<Column> columns = new ArrayList<>();

            for (final AbstractToken token : children) {

                if (TokenType.FUNCTION_GROUP.equals(token.getTokenType())) {
                    // If a function has been used for a column then parse it.
                    if (fieldExpression != null || fieldToken != null) {
                        throw new TokenException(token, "Unexpected expression");
                    }

                    final FunctionGroup functionGroup = (FunctionGroup) token;
                    columnName = functionGroup.getText();
                    final ExpressionParser expressionParser = new ExpressionParser(new ParamFactory(expressionMap));
                    try {
                        fieldExpression = expressionParser
                                .parse(expressionContext, fieldIndex, Collections.singletonList(token));
                    } catch (final ParseException e) {
                        throw new TokenException(keywordGroup, e.getMessage());
                    }

                } else if (TokenType.isString(token) || TokenType.MULTIPLICATION.equals(token.getTokenType())) {
                    // If we have a string then it is either a field name or a column name if provided after AS.
                    // Note that we also allow `*` (TokenType.MULTIPLICATION) to add `all` fields.
                    if (afterAs) {
                        if (doneAs) {
                            throw new TokenException(token, "Syntax exception, unexpected column name");
                        } else {
                            columnName = token.getUnescapedText();
                            doneAs = true;
                        }
                    } else if (fieldToken == null) {
                        if (fieldExpression != null) {
                            throw new TokenException(token, "Unexpected field");
                        }

                        fieldToken = token;
                        columnName = token.getUnescapedText();
                    } else {
                        throw new TokenException(token, "Syntax exception, expected AS");
                    }

                } else if (TokenType.AS.equals(token.getTokenType())) {
                    // We found AS so prepare for a column name.
                    if (fieldToken == null && fieldExpression == null) {
                        throw new TokenException(token, "Syntax exception, expected field name or expression");
                    }
                    if (afterAs) {
                        throw new TokenException(token, "Unexpected AS");
                    }

                    afterAs = true;

                } else if (TokenType.COMMA.equals(token.getTokenType())) {
                    addColumn(sortMap,
                            groupMap,
                            filterMap,
                            queryBuilder,
                            columnCount,
                            columns,
                            token,
                            fieldToken,
                            columnName,
                            fieldExpression);

                    fieldToken = null;
                    fieldExpression = null;
                    columnName = null;
                    afterAs = false;
                    doneAs = false;
                }
            }

            // Add final field if we have one.
            if (fieldToken != null || fieldExpression != null) {
                addColumn(sortMap,
                        groupMap,
                        filterMap,
                        queryBuilder,
                        columnCount,
                        columns,
                        fieldToken,
                        fieldToken,
                        columnName,
                        fieldExpression);
            }

            // Modify columns if we have a time window.
            final List<Column> modifiedColumns = optionalCompiledWindow.map(compiledWindow ->
                    compiledWindow.addPeriodColumns(columns, expressionMap)).orElse(columns);
            tableSettingsBuilder.addColumns(modifiedColumns);
        }

        private void addColumn(final Map<String, Sort> sortMap,
                               final Map<String, Integer> groupMap,
                               final Map<String, IncludeExcludeFilter> filterMap,
                               final Query.Builder queryBuilder,
                               final Map<String, AtomicInteger> columnCount,
                               final List<Column> columns,
                               final AbstractToken token,
                               final AbstractToken fieldToken,
                               final String columnName,
                               final Expression fieldExpression) {

            if (fieldToken != null) {
                if (columnName.equals("*") ||
                    (TokenType.PARAM.equals(fieldToken.getTokenType()) && columnName.contains("*"))) {
                    expandStarredField(
                            sortMap,
                            groupMap,
                            filterMap,
                            queryBuilder,
                            columnCount,
                            columns,
                            fieldToken.getUnescapedText());

                } else {
                    final String columnId = createColumnId(columnCount, columnName);
                    columns.add(createColumn(
                            fieldToken,
                            columnId,
                            fieldToken.getUnescapedText(),
                            columnName,
                            true,
                            false,
                            sortMap,
                            groupMap,
                            filterMap));
                }

            } else if (fieldExpression != null) {
                final String columnId = createColumnId(columnCount, columnName);
                columns.add(createColumn(
                        columnId,
                        fieldExpression,
                        columnName,
                        sortMap,
                        groupMap,
                        filterMap));
            } else {
                throw new TokenException(token, "Syntax exception, expected field name");
            }
        }

        private void expandStarredField(final Map<String, Sort> sortMap,
                                        final Map<String, Integer> groupMap,
                                        final Map<String, IncludeExcludeFilter> filterMap,
                                        final Query.Builder queryBuilder,
                                        final Map<String, AtomicInteger> columnCount,
                                        final List<Column> columns,
                                        final String fieldNameFilter) {
            final DocRef dataSource = queryBuilder.build().getDataSource();
            if (dataSource != null) {
                String filter = fieldNameFilter;

                // Create a regex name filter if the user has used a *.
                if (filter.equals("*")) {
                    filter = null;
                } else if (filter.contains("*")) {
                    filter = filter.replaceAll("\\*", ".*");
                    filter = filter.replaceAll("\\?", ".?");
                    filter = "/" + filter;
                }

                final FindFieldCriteria criteria = new FindFieldCriteria(
                        PageRequest.createDefault(),
                        FindFieldCriteria.DEFAULT_SORT_LIST,
                        dataSource,
                        filter,
                        null);
                final ResultPage<QueryField> resultPage = queryFieldProviderProvider.get().findFields(criteria);
                final List<QueryField> fields = resultPage.getValues();
                for (final QueryField field : fields) {
                    final String fieldName = field.getFldName();
                    final char[] chars = fieldName.toCharArray();
                    final String columnId = createColumnId(columnCount, fieldName);
                    final Token t = new Token(TokenType.STRING, chars, 0, chars.length - 1);
                    columns.add(createColumn(
                            t,
                            columnId,
                            t.getUnescapedText(),
                            fieldName,
                            true,
                            false,
                            sortMap,
                            groupMap,
                            filterMap));
                }
            }
        }

        private String createColumnId(final Map<String, AtomicInteger> map, final String name) {
            final String cleanName = name
                    .trim()
                    .toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9]", "_")
                    .replaceAll("_+", "_");
            final int id = map.computeIfAbsent(cleanName, k -> new AtomicInteger()).incrementAndGet();
            return cleanName + "-" + id;
        }

        private Column createColumn(final AbstractToken token,
                                    final String id,
                                    final String fieldName,
                                    final String columnName,
                                    final boolean visible,
                                    final boolean special,
                                    final Map<String, Sort> sortMap,
                                    final Map<String, Integer> groupMap,
                                    final Map<String, IncludeExcludeFilter> filterMap) {
            addedFields.add(fieldName);
            Expression expression = expressionMap.get(fieldName);
            if (expression == null) {
                final ExpressionParser expressionParser = new ExpressionParser(new ParamFactory(expressionMap));
                try {
                    expression = expressionParser.parse(
                            expressionContext,
                            fieldIndex,
                            Collections.singletonList(token));
                } catch (final ParseException e) {
                    throw new TokenException(token, e.getMessage());
                }
            }

            final String expressionString = expression.toString();
            return Column.builder()
                    .id(id)
                    .name(columnName)
                    .expression(expressionString)
                    .sort(sortMap.get(fieldName))
                    .group(groupMap.get(fieldName))
                    .filter(filterMap.get(fieldName))
                    .visible(visible)
                    .special(special)
                    .build();
        }

        private Column createColumn(final String id,
                                    final Expression expression,
                                    final String columnName,
                                    final Map<String, Sort> sortMap,
                                    final Map<String, Integer> groupMap,
                                    final Map<String, IncludeExcludeFilter> filterMap) {
            addedFields.add(columnName);
            final String expressionString = expression.toString();
            return Column.builder()
                    .id(id)
                    .name(columnName)
                    .expression(expressionString)
                    .sort(sortMap.get(columnName))
                    .group(groupMap.get(columnName))
                    .filter(filterMap.get(columnName))
                    .visible(true)
                    .special(false)
                    .build();
        }

        private void processLimit(final KeywordGroup keywordGroup,
                                  final TableSettings.Builder tableSettingsBuilder) {
            final List<AbstractToken> children = keywordGroup.getChildren();
            for (final AbstractToken t : children) {
                if (TokenType.isString(t) || TokenType.NUMBER.equals(t.getTokenType())) {
                    try {
                        tableSettingsBuilder.addMaxResults(Long.parseLong(t.getUnescapedText()));
                    } catch (final NumberFormatException e) {
                        throw new TokenException(t, "Syntax exception, expected number");
                    }
                } else if (!TokenType.COMMA.equals(t.getTokenType())) {
                    // We expect numbers and commas.
                    throw new TokenException(t, "Syntax exception, expected number");
                }
            }
        }

        private void processSortBy(final KeywordGroup keywordGroup,
                                   final Map<String, Sort> sortMap) {
            String fieldName = null;
            SortDirection direction = null;
            final List<AbstractToken> children = keywordGroup.getChildren();
            boolean first = true;
            for (final AbstractToken t : children) {
                // Ignore BY
                if (!(first && TokenType.BY.equals(t.getTokenType()))) {
                    if (TokenType.isString(t)) {
                        if (fieldName == null) {
                            fieldName = t.getUnescapedText();
                        } else if (direction == null) {
                            try {
                                if (t.getUnescapedText().toLowerCase(Locale.ROOT).equalsIgnoreCase("asc")) {
                                    direction = SortDirection.ASCENDING;
                                } else if (t.getUnescapedText().toLowerCase(Locale.ROOT).equalsIgnoreCase("desc")) {
                                    direction = SortDirection.DESCENDING;
                                } else {
                                    direction = SortDirection.valueOf(t.getUnescapedText());
                                }
                            } catch (final IllegalArgumentException e) {
                                throw new TokenException(t,
                                        "Syntax exception, expected sort direction 'asc' or 'desc'");
                            }
                        }
                    } else if (TokenType.COMMA.equals(t.getTokenType())) {
                        if (fieldName == null) {
                            throw new TokenException(t, "Syntax exception, expected field name");
                        }
                        final Sort sort = new Sort(sortMap.size(),
                                direction != null
                                        ? direction
                                        : SortDirection.ASCENDING);
                        sortMap.put(fieldName, sort);
                        fieldName = null;
                        direction = null;
                    } else {
                        throw new TokenException(t, "Syntax exception, expected string");
                    }
                }

                first = false;
            }

            if (fieldName != null) {
                final Sort sort = new Sort(sortMap.size(),
                        direction != null
                                ? direction
                                : SortDirection.ASCENDING);
                sortMap.put(fieldName, sort);
            }
        }

        private void processGroupBy(final KeywordGroup keywordGroup,
                                    final Map<String, Integer> groupMap,
                                    final int groupDepth) {
            String fieldName = null;
            final List<AbstractToken> children = keywordGroup.getChildren();
            boolean first = true;
            for (final AbstractToken t : children) {
                // Ignore BY
                if (!(first && TokenType.BY.equals(t.getTokenType()))) {
                    if (TokenType.isString(t)) {
                        if (fieldName == null) {
                            fieldName = t.getUnescapedText();
                        } else {
                            throw new TokenException(t, "Syntax exception, expected comma");
                        }
                    } else if (TokenType.COMMA.equals(t.getTokenType())) {
                        if (fieldName == null) {
                            throw new TokenException(t, "Syntax exception, expected field name");
                        }
                        groupMap.put(fieldName, groupDepth);
                        fieldName = null;
                    } else {
                        throw new TokenException(t, "Syntax exception, expected field name");
                    }
                }

                first = false;
            }
            if (fieldName != null) {
                groupMap.put(fieldName, groupDepth);
            }
        }

        private void checkTokenOrder(final AbstractToken token,
                                     final List<TokenType> consumedTokens) {

            final TokenType tokenType = token.getTokenType();

            final Set<TokenType> keywordsRequiredBefore = TokenType.getKeywordsRequiredBefore(tokenType);

            for (final TokenType requiredType : keywordsRequiredBefore) {
                if (!consumedTokens.contains(requiredType)) {
                    throw new TokenException(token,
                            "Required token " + requiredType + " before " + tokenType);
                }
            }

            final Set<TokenType> keywordsValidBefore = TokenType.getKeywordsValidBefore(tokenType);

            for (final TokenType consumedType : consumedTokens) {
                if (!keywordsValidBefore.contains(consumedType)) {
                    throw new TokenException(token,
                            "Unexpected token " + tokenType + " after " + consumedType);
                }
            }
        }

        private void checkTokenOrder(final AbstractToken token,
                                     final List<TokenType> consumedTokens,
                                     final Set<TokenType> requireConsumed,
                                     final Set<TokenType> allowConsumed) {
            for (final TokenType tokenType : requireConsumed) {
                if (!consumedTokens.contains(tokenType)) {
                    throw new TokenException(token,
                            "Required token " + tokenType + " before " + token.getTokenType());
                }
            }
            for (final TokenType tokenType : consumedTokens) {
                if (!allowConsumed.contains(tokenType)) {
                    throw new TokenException(token,
                            "Unexpected token " + token.getTokenType() + " after " + tokenType);
                }
            }
        }

        private Set<TokenType> inverse(final Set<TokenType> tokenTypes) {
            final Set<TokenType> set = new HashSet<>(TokenType.ALL);
            set.removeAll(tokenTypes);
            return set;
        }
    }

    private static int getTokenIndex(final List<AbstractToken> tokens, final Predicate<AbstractToken> predicate) {
        for (int i = 0; i < tokens.size(); i++) {
            final AbstractToken token = tokens.get(i);
            if (predicate.test(token)) {
                return i;
            }
        }
        return -1;
    }
}
