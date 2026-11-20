/*
 * Copyright 2023 Crown Copyright
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
import stroom.query.api.Param;
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
import stroom.query.api.TimeRange;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.api.datasource.QueryFieldProvider;
import stroom.query.api.token.AbstractToken;
import stroom.query.api.token.FunctionGroup;
import stroom.query.api.token.KeywordGroup;
import stroom.query.api.token.QuotedStringToken;
import stroom.query.api.token.Token;
import stroom.query.api.token.TokenException;
import stroom.query.api.token.TokenGroup;
import stroom.query.api.token.TokenType;
import stroom.query.common.v2.CompiledColumns;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.common.v2.SearchResultStoreConfig;
import stroom.query.language.functions.Expression;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.ExpressionParser;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ParamFactory;
import stroom.query.language.functions.TimeWindows;
import stroom.query.language.functions.UserTimeZoneUtil;
import stroom.query.language.token.StructureBuilder;
import stroom.query.language.token.Tokeniser;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.text.ParseException;
import java.time.ZoneId;
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
import java.util.stream.Collectors;

public class SearchRequestFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SearchRequestFactory.class);

    public static final String TABLE_COMPONENT_ID = "table";
    /// The name of the hidden column that the rows of a query with a window clause are grouped by.
    public static final String WINDOW_COLUMN_NAME = "__window__";
    private static final String WINDOW_KEY_FUNCTION = "windowKey()";
    private static final String WINDOW_START_FUNCTION = "windowStart()";
    private static final String WINDOW_EXAMPLE = "as in 'window EventTime by 1d advance 1h offset 6h'";
    // The most windows one event may be in. A query that would put an event in more is refused.
    public static final String VIS_COMPONENT_ID = "vis";

    private final VisualisationTokenConsumer visualisationTokenConsumer;
    private final DataSourceResolver dataSourceResolver;
    private final Provider<QueryFieldProvider> queryFieldProviderProvider;
    private final SecurityContext securityContext;
    private final Provider<SearchResultStoreConfig> resultStoreConfigProvider;

    /// Creates the factory with the default limits on a query.
    ///
    /// @param visualisationTokenConsumer Parses the `show` clause.
    /// @param dataSourceResolver         Finds the data source that a query names.
    /// @param queryFieldProviderProvider Gives the fields of a data source.
    /// @param securityContext            The security context.
    public SearchRequestFactory(final VisualisationTokenConsumer visualisationTokenConsumer,
                                final DataSourceResolver dataSourceResolver,
                                final Provider<QueryFieldProvider> queryFieldProviderProvider,
                                final SecurityContext securityContext) {
        this(visualisationTokenConsumer,
                dataSourceResolver,
                queryFieldProviderProvider,
                securityContext,
                SearchResultStoreConfig::new);
    }

    /// Creates the factory.
    ///
    /// @param visualisationTokenConsumer Parses the `show` clause.
    /// @param dataSourceResolver         Finds the data source that a query names.
    /// @param queryFieldProviderProvider Gives the fields of a data source.
    /// @param securityContext            The security context.
    /// @param resultStoreConfigProvider  Gives the configured limits on a query.
    @Inject
    public SearchRequestFactory(final VisualisationTokenConsumer visualisationTokenConsumer,
                                final DataSourceResolver dataSourceResolver,
                                final Provider<QueryFieldProvider> queryFieldProviderProvider,
                                final SecurityContext securityContext,
                                final Provider<SearchResultStoreConfig> resultStoreConfigProvider) {
        this.visualisationTokenConsumer = visualisationTokenConsumer;
        this.dataSourceResolver = dataSourceResolver;
        this.queryFieldProviderProvider = queryFieldProviderProvider;
        this.securityContext = securityContext;
        this.resultStoreConfigProvider = resultStoreConfigProvider;
    }

    public void extractDataSourceOnly(final String string,
                                      final Consumer<DocRef> consumer) {
        newBuilder().extractDataSourceOnly(string, consumer);
    }

    /// Finds the native query, such as SQL, in the `sql` clause of a query that may be incomplete, as
    /// it is while being typed, for listing the fields it returns.
    ///
    /// @param string The query text.
    /// @return The native query, or empty if there is no complete `sql` clause.
    public Optional<String> extractNativeQuery(final String string) {
        try {
            final TokenGroup tokenGroup = StructureBuilder.create(Tokeniser.parse(string));
            // Only the sql clause straight after from is for the from source; a later one is a
            // joined source's.
            return tokenGroup.getChildren()
                    .stream()
                    .limit(2)
                    .filter(token -> TokenType.SQL.equals(token.getTokenType()))
                    .findFirst()
                    .map(token -> ((KeywordGroup) token).getChildren())
                    .filter(children -> !children.isEmpty())
                    .map(List::getFirst)
                    .filter(token -> token instanceof QuotedStringToken)
                    .map(AbstractToken::getUnescapedText)
                    .filter(sql -> !sql.isBlank());
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to find the native query in '{}'", string), e);
            return Optional.empty();
        }
    }

    /// Finds the data source of the last join line in a query that may be incomplete, as it is while
    /// being typed, for deciding what can follow it.
    ///
    /// @param string The query text.
    /// @return The data source of the last join line, or empty if there is no join line or its data
    /// source is not a name that can be found yet.
    public Optional<DocRef> extractLastJoinDataSource(final String string) {
        try {
            final TokenGroup tokenGroup = StructureBuilder.create(Tokeniser.parse(string));
            final Optional<AbstractToken> source = tokenGroup.getChildren()
                    .stream()
                    .filter(token -> TokenType.JOIN.equals(token.getTokenType()))
                    .reduce((first, second) -> second)
                    .map(token -> ((KeywordGroup) token).getChildren())
                    .filter(children -> !children.isEmpty())
                    .map(List::getFirst)
                    .filter(TokenType::isString);
            return source.map(token -> securityContext.useAsReadResult(() ->
                    dataSourceResolver.resolveDataSourceRef(token.getUnescapedText())));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to find the joined data source in '{}'", string), e);
            return Optional.empty();
        }
    }

    public SearchRequest create(final String string,
                                final SearchRequest in,
                                final ExpressionContext expressionContext) {
        return newBuilder().create(string, in, expressionContext);
    }

    // A builder for a whole query, rather than a bracketed query inside one.
    private Builder newBuilder() {
        return new Builder(
                visualisationTokenConsumer,
                dataSourceResolver,
                queryFieldProviderProvider,
                securityContext,
                resultStoreConfigProvider.get().getMaxWindowCopiesPerEvent(),
                false);
    }


    // --------------------------------------------------------------------------------


    private static class Builder implements JoinClauses.Parser {

        private final VisualisationTokenConsumer visualisationTokenConsumer;
        private final DataSourceResolver dataSourceResolver;
        private final Provider<QueryFieldProvider> queryFieldProviderProvider;
        private final SecurityContext securityContext;
        // The most times a query with a window clause may add one event to its results.
        private final int maxWindowCopiesPerEvent;

        private ExpressionContext expressionContext;
        private final FieldIndex fieldIndex;
        private Map<String, String> paramMap;
        private final Map<String, Expression> expressionMap;
        private final Set<String> addedFields = new HashSet<>();
        private final List<AbstractToken> additionalFields = new ArrayList<>();
        // The expressions of the select clause's columns, by the names of the columns.
        private final Map<String, Expression> columnExpressions = new HashMap<>();
        private final HavingExpressions havingExpressions;
        private boolean inHaving;
        private boolean hasWindow;
        // The windows of the query's window clause, which are parsed before any other clause so that
        // a window function can be written in a clause that comes before it.
        private HoppingWindow window;

        // True if this builder parses a bracketed query inside another query.
        private final boolean bracketed;
        // The params and time range of the request, which bracketed and joined queries share.
        private List<Param> params;
        private TimeRange timeRange;
        // The where and filter clauses, which are only placed once any joins are known.
        private ExpressionOperator whereExpression;
        private ExpressionOperator filterExpression;
        // The from and join lines, and how where is placed around them.
        private final JoinClauses joinClauses;

        Builder(final VisualisationTokenConsumer visualisationTokenConsumer,
                final DataSourceResolver dataSourceResolver,
                final Provider<QueryFieldProvider> queryFieldProviderProvider,
                final SecurityContext securityContext,
                final int maxWindowCopiesPerEvent,
                final boolean bracketed) {
            this.maxWindowCopiesPerEvent = maxWindowCopiesPerEvent;
            this.visualisationTokenConsumer = visualisationTokenConsumer;
            this.dataSourceResolver = dataSourceResolver;
            this.queryFieldProviderProvider = queryFieldProviderProvider;
            this.securityContext = securityContext;
            this.bracketed = bracketed;
            this.fieldIndex = new FieldIndex();
            this.paramMap = Collections.emptyMap();
            this.expressionMap = new HashMap<>();
            this.havingExpressions = new HavingExpressions(expressionMap, fieldIndex);
            this.joinClauses = new JoinClauses(this, fieldIndex, bracketed);
        }

        @Override
        public List<Param> getParams() {
            return params;
        }

        @Override
        public TimeRange getTimeRange() {
            return timeRange;
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
                    params = in.getQuery().getParams();
                    timeRange = in.getQuery().getTimeRange();
                    paramMap = ParamUtil.createParamMap(in.getQuery().getParams());
                    queryBuilder.params(in.getQuery().getParams());
                    queryBuilder.timeRange(in.getQuery().getTimeRange());
                    // A native query given by the caller is kept, unless the query has a sql clause.
                    queryBuilder.nativeQuery(in.getQuery().getNativeQuery());
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
                checkNativeQuery(query);

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

        // A data source that takes a native query must have one, and no other may.
        private void checkNativeQuery(final Query query) {
            checkNativeQuery(query, joinClauses.getSqlToken(), joinClauses.getFromToken());
        }

        @Override
        public void checkNativeQuery(final Query query, final AbstractToken sqlToken, final AbstractToken fromToken) {
            final DocRef dataSource = query.getDataSource();
            final boolean takesNativeQuery = dataSourceResolver.takesNativeQuery(dataSource);
            final boolean hasNativeQuery = NullSafe.isNonBlankString(query.getNativeQuery());
            if (hasNativeQuery && !takesNativeQuery) {
                final String what = sqlToken == null
                        // The SQL came with the request, not from a sql clause.
                        ? "The query has SQL, but SQL can only be run against a data source that runs SQL"
                        : "A sql clause can only be used with a data source that runs SQL";
                throw new TokenException(sqlToken, what + ", such as a JDBC or Amazon Athena Data Connection, "
                                                   + "or an LDAP one, which takes its search filter there. '"
                                                   + dataSource.getName() + "' does neither.");
            } else if (takesNativeQuery && !hasNativeQuery) {
                throw new TokenException(fromToken, "'" + dataSource.getName() + "' needs the SQL to run, in a sql "
                                               + "clause after from, e.g. from \"" + dataSource.getName()
                                               + "\" sql \"\"\"select * from my_table\"\"\", or for an LDAP "
                                               + "directory its search filter, e.g. sql "
                                               + "\"\"\"(objectClass=person)\"\"\"");
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
            if (dataSourceToken instanceof final TokenGroup bracketedQuery
                && !bracketedQuery.getChildren().isEmpty()) {
                addDataSource(bracketedQuery.getChildren(), consumer, isLenient);
                return tokens.subList(1, tokens.size());
            }
            final String dataSourceName = resolveDataSourceName(dataSourceToken);
            final DocRef dataSourceDocRef = securityContext.useAsReadResult(() ->
                    dataSourceResolver.resolveDataSourceRef(dataSourceName));

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
                final List<AbstractToken> termTokens) {
            final ExpressionTerm expressionTerm;

            if (NullSafe.isEmptyCollection(termTokens)) {
                throw new RuntimeException("createTerm called with empty list");
            } else if (termTokens.size() < 3) {
                throw new TokenException(termTokens.getFirst(), "Incomplete term");
            }
            final List<AbstractToken> tokens;
            if (inHaving) {
                // A comparison of two expressions becomes a test of one hidden column.
                final ExpressionTerm comparison = havingExpressions.createComparisonTerm(termTokens);
                if (comparison != null) {
                    return comparison;
                }
                tokens = havingExpressions.replaceLeftExpression(termTokens);
                if (tokens.size() < 3) {
                    throw new TokenException(termTokens.getLast(), "Incomplete term");
                }
            } else {
                tokens = termTokens;
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
                    dictionaryRef = dataSourceResolver.findDictionaryDoc(dictionaryName);
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
                    if (functionGroup.getName().equalsIgnoreCase("param")) {
                        return resolveParam(functionGroup);

                    } else if (HavingExpressions.findDatePoint(token.getUnescapedText()) == null) {
                        throw new TokenException(token, "Unexpected function in value");
                    } else {
                        dateExpression = true;
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

        // The `from` clause accepts param('key') as well as a string literal, so
        // `from param('EventStore')` resolves here rather than throwing "Expected a token of type
        // string". The alternative, substituting param() references into the query text on the
        // client before sending it, is a blind String.replace: it rewrites matches inside quoted
        // literals and comments, has to escape the value for the literal it lands in, and shifts
        // every error offset the editor highlights. Resolving here avoids all of that: the value
        // travels as an ordinary Param and never touches the query text.
        //
        // Known gap, deliberately not addressed here: QueryServiceImpl.validateQuery builds its
        // request with no QueryContext, so paramMap is empty there and a query using param() in a
        // `from` clause will fail validation while succeeding at search. Closing that means
        // changing the validateQuery REST signature to carry params.

        /**
         * The data source name, resolving a {@code param('key')} reference if that is what was
         * given.
         *
         * <p>A {@code ${key}} token is <b>not</b> resolved, and deliberately so: it reaches here as
         * a {@link TokenType#PARAM} whose unescaped text is the key rather than its value, and
         * nothing substitutes it on this path — so {@code from ${EventStore}} would look up a data
         * source literally named {@code EventStore}. That is a pre-existing upstream behaviour and
         * is left exactly as it was; this method only adds the {@code param('key')} form, which
         * previously threw.</p>
         *
         * @param dataSourceToken the token following {@code from}
         * @return the data source name; never null
         */
        private String resolveDataSourceName(final AbstractToken dataSourceToken) {
            if (dataSourceToken instanceof final FunctionGroup functionGroup
                && "param".equalsIgnoreCase(functionGroup.getName())) {
                final String resolved = resolveParam(functionGroup);
                if (resolved == null) {
                    // Distinguished from "no such data source", which is what a null name would
                    // otherwise present as several frames later.
                    throw new TokenException(functionGroup,
                            "No value supplied for the parameter used as the data source");
                }
                return resolved;
            }

            if (!TokenType.isString(dataSourceToken)) {
                throw new TokenException(dataSourceToken, "Expected a token of type string");
            }
            return dataSourceToken.getUnescapedText();
        }

        private String resolveParam(final FunctionGroup functionGroup) {
            if (functionGroup.getChildren().isEmpty()) {
                throw new TokenException(functionGroup, "Expected param name");
            } else if (functionGroup.getChildren().size() > 1) {
                throw new TokenException(functionGroup.getChildren().get(1), "Unexpected token");
            } else {
                final AbstractToken child = functionGroup.getChildren().getFirst();
                // Quoted string tokens are accepted as well as a bare TokenType.STRING, because
                // param('EventStore') — the quoted form every caller actually writes — would
                // otherwise throw "Expected param name" in every position. A quoted token
                // unescapes to the same key (QuotedStringToken.getUnescapedText), so accepting the
                // quoted forms changes nothing but which spellings work. PARAM is deliberately not
                // accepted: param(${x}) would read as a nested reference and resolve to the inner
                // key rather than its value.
                final TokenType childType = child.getTokenType();
                if (!TokenType.STRING.equals(childType)
                    && !TokenType.SINGLE_QUOTED_STRING.equals(childType)
                    && !TokenType.DOUBLE_QUOTED_STRING.equals(childType)) {
                    throw new TokenException(child, "Expected param name");
                }
                return paramMap.get(child.getUnescapedText());
            }
        }

        @Override
        public ExpressionOperator processLogic(final List<AbstractToken> tokens) {
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
                // The exists and in forms at the top level of where have already become joins.
                if (WhereSubqueries.isSubquery(token)) {
                    throw new TokenException(token, WhereSubqueries.MISPLACED_MESSAGE);
                }
                if (termTokens.isEmpty() && token instanceof final KeywordGroup keywordGroup) {
                    out.add(processLogic(keywordGroup.getChildren()));
                } else if (termTokens.isEmpty() && token instanceof final TokenGroup tokenGroup
                           && !(inHaving && HavingExpressions.isExpression(tokenGroup))) {
                    // Brackets hold conditions, or in a having clause may start an expression.
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

            // An eval clause can come before the window clause and use a window function, so the
            // windows are needed before the clauses are worked through in order.
            for (final AbstractToken token : tokens) {
                if (window == null
                    && token instanceof final KeywordGroup keywordGroup
                    && TokenType.WINDOW.equals(keywordGroup.getTokenType())) {
                    window = parseWindow(keywordGroup);
                    expressionContext = expressionContext.copy().window(window).build();
                }
            }

            havingExpressions.findSelectNames(tokens);

            List<AbstractToken> remaining = new LinkedList<>(tokens);
            while (!remaining.isEmpty()) {
                final AbstractToken token = remaining.getFirst();

                if (token instanceof final KeywordGroup keywordGroup) {
                    joinClauses.checkJoinHasKeys(keywordGroup);
                    switch (keywordGroup.getTokenType()) {
                        case FROM -> {
                            checkTokenOrder(token, consumedTokens);
                            joinClauses.processFrom(keywordGroup, queryBuilder);
                            remaining.removeFirst();
                        }
                        case SQL -> {
                            checkTokenOrder(token, consumedTokens);
                            joinClauses.processSqlClause(keywordGroup, consumedTokens, queryBuilder);
                            remaining.removeFirst();
                        }
                        case JOIN -> {
                            checkTokenOrder(token, consumedTokens);
                            joinClauses.processJoin(keywordGroup);
                            remaining.removeFirst();
                        }
                        case ON -> {
                            checkTokenOrder(token, consumedTokens);
                            joinClauses.processOn(keywordGroup);
                            remaining.removeFirst();
                        }
                        case WHERE -> {
                            checkTokenOrder(token, consumedTokens);
                            remaining.set(0, joinClauses.addWhereSubqueryJoins(keywordGroup, queryBuilder));
                            remaining =
                                    addExpression(remaining,
                                            consumedTokens,
                                            Set.of(TokenType.FROM, TokenType.SQL, TokenType.JOIN, TokenType.ON),
                                            TokenType.WHERE,
                                            expression -> whereExpression = expression);
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
                                    tableSettingsBuilder,
                                    queryBuilder);
                            remaining.removeFirst();
                        }
                        case FILTER -> {
                            checkTokenOrder(token, consumedTokens);
                            remaining =
                                    addExpression(remaining,
                                            consumedTokens,
                                            Set.of(
                                                    TokenType.FROM,
                                                    TokenType.SQL,
                                                    TokenType.JOIN,
                                                    TokenType.ON,
                                                    TokenType.WHERE,
                                                    TokenType.EVAL,
                                                    TokenType.WINDOW),
                                            TokenType.FILTER,
                                            expression -> filterExpression = expression);
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
                            if (bracketed) {
                                throw new TokenException(token, "A bracketed query can't show a visualisation");
                            }
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
            joinClauses.checkNoPendingJoin();

            // Place the where and filter clauses, now that any joins are known.
            joinClauses.placeConditions(queryBuilder, tableSettingsBuilder, whereExpression, filterExpression);

            // Ensure StreamId and EventId fields exist if there is no grouping. A bracketed query's
            // rows are not events, so neither it nor a query reading one has them.
            if (groupDepth == 0 && !hasWindow && !bracketed && !joinClauses.hasBracketedFrom()) {
                if (!addedFields.contains(SpecialColumns.RESERVED_STREAM_ID)) {
                    tableSettingsBuilder.addColumns(SpecialColumns.RESERVED_STREAM_ID_COLUMN);
                    addedFields.add(SpecialColumns.RESERVED_STREAM_ID);
                }
                if (!addedFields.contains(SpecialColumns.RESERVED_EVENT_ID)) {
                    tableSettingsBuilder.addColumns(SpecialColumns.RESERVED_EVENT_ID_COLUMN);
                    addedFields.add(SpecialColumns.RESERVED_EVENT_ID);
                }
                if (!addedFields.contains(SpecialColumns.RESERVED_ANNOTATION_ID)) {
                    tableSettingsBuilder.addColumns(SpecialColumns.RESERVED_ANNOTATION_ID_COLUMN);
                    addedFields.add(SpecialColumns.RESERVED_ANNOTATION_ID);
                }
            }

            // Add the hidden columns that the where conditions on joined rows test.
            joinClauses.getPostJoinColumns().forEach((field, columnName) ->
                    tableSettingsBuilder.addColumns(createFieldColumn(columnName, field)));

            // Add missing fields if needed, with the expressions of the having clause.
            additionalFields.addAll(havingExpressions.resolve(expressionContext, columnExpressions, addedFields));
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
            if (hasWindow) {
                checkWindowCopies(tokens.getFirst(), tableSettings);
            }

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

        // Adds the windows of a window clause to the table, once the clauses before it are known.
        private void processWindow(final KeywordGroup keywordGroup,
                                   final TableSettings.Builder builder,
                                   final Query.Builder queryBuilder) {
            if (joinClauses.hasWindowedJoin()) {
                throw new TokenException(keywordGroup, "A window clause can't be used in a query that has a "
                                                       + "join with a time window, as in 'within 10m after'");
            }
            checkWindowFieldIsDate(keywordGroup.getChildren().getFirst(), window.getTimeField(), queryBuilder);

            // Make sure the time field is fetched.
            fieldIndex.create(window.getTimeField());
            builder.window(window);
            hasWindow = true;
        }

        // Refuses a query whose lag() and lead() functions would have each row added to its result
        // store too many times.
        private void checkWindowCopies(final AbstractToken token, final TableSettings tableSettings) {
            final int windowShifts;
            try {
                windowShifts = CompiledColumns
                        .create(expressionContext, tableSettings.getColumns(), new HashMap<>())
                        .getValueReferenceIndex()
                        .getWindowShifts()
                        .length;
            } catch (final RuntimeException e) {
                throw new TokenException(token, e.getMessage());
            }
            final int windowsPerTime = TimeWindows.create(window).getWindowsPerTime();
            if (TimeWindows.getCopiesPerTime(windowsPerTime, windowShifts) > maxWindowCopiesPerEvent) {
                throw new TokenException(token, TimeWindows.tooManyCopiesMessage(
                        windowsPerTime, windowShifts, maxWindowCopiesPerEvent));
            }
        }

        // Parses a window clause, as in `window EventTime by 1d advance 1h offset 6h`.
        private HoppingWindow parseWindow(final KeywordGroup keywordGroup) {
            final List<AbstractToken> children = new ArrayList<>(keywordGroup.getChildren());

            // Get the time field.
            if (children.isEmpty()) {
                throw new TokenException(keywordGroup, "Expected the time field of the window, " + WINDOW_EXAMPLE);
            }
            final AbstractToken fieldToken = children.removeFirst();
            if (!TokenType.isString(fieldToken)) {
                throw new TokenException(fieldToken, "Expected the time field of the window, " + WINDOW_EXAMPLE);
            }
            final String field = fieldToken.getUnescapedText();

            // Get `by` and the size.
            if (children.isEmpty() || !TokenType.BY.equals(children.getFirst().getTokenType())) {
                throw new TokenException(children.isEmpty()
                        ? fieldToken
                        : children.getFirst(), "Expected 'by' and the size of the window, " + WINDOW_EXAMPLE);
            }
            final AbstractToken byToken = children.removeFirst();
            final String size = removeWindowDuration(children, byToken, "the size of the window");

            // Get `advance` and `offset`, in either order.
            String advance = null;
            String offset = null;
            while (!children.isEmpty()) {
                final AbstractToken optionToken = children.removeFirst();
                final String option = TokenType.isString(optionToken)
                        ? optionToken.getUnescapedText().toLowerCase(Locale.ROOT)
                        : "";
                switch (option) {
                    case "advance" -> {
                        if (advance != null) {
                            throw new TokenException(optionToken, "The window already has an advance");
                        }
                        advance = removeWindowDuration(children, optionToken, "how far the window advances");
                    }
                    case "offset" -> {
                        if (offset != null) {
                            throw new TokenException(optionToken, "The window already has an offset");
                        }
                        final boolean earlier = !children.isEmpty()
                                                && TokenType.MINUS.equals(children.getFirst().getTokenType());
                        if (earlier) {
                            children.removeFirst();
                        }
                        offset = (earlier
                                ? "-"
                                : "") + removeWindowDuration(children, optionToken, "the offset of the window");
                    }
                    case "using" -> throw new TokenException(optionToken, "A window clause no longer takes "
                            + "'using'. Write the function where its value is wanted, as in 'count()', and "
                            + "use lag() for the value of an earlier window, as in 'lag(count(), 1)'");
                    default -> throw new TokenException(optionToken, "Expected 'advance' or 'offset', "
                                                                     + WINDOW_EXAMPLE);
                }
            }

            final ZoneId zoneId = UserTimeZoneUtil.getZoneId(expressionContext.getDateTimeSettings());
            final HoppingWindow window = new HoppingWindow(field, size, advance, offset, zoneId.getId());
            final TimeWindows timeWindows;
            try {
                timeWindows = TimeWindows.create(window);
            } catch (final IllegalArgumentException e) {
                throw new TokenException(keywordGroup, e.getMessage());
            }
            if (timeWindows.getWindowsPerTime() > maxWindowCopiesPerEvent) {
                throw new TokenException(keywordGroup, TimeWindows.tooManyCopiesMessage(
                        timeWindows.getWindowsPerTime(), 0, maxWindowCopiesPerEvent));
            }
            return window;
        }

        private String removeWindowDuration(final List<AbstractToken> children,
                                            final AbstractToken before,
                                            final String what) {
            if (children.isEmpty()) {
                throw new TokenException(before, "Expected " + what + ", " + WINDOW_EXAMPLE);
            }
            final AbstractToken token = children.removeFirst();
            if (!TokenType.DURATION.equals(token.getTokenType())) {
                throw new TokenException(token, "Expected " + what + " as a duration such as 1d or 30m, "
                                                + WINDOW_EXAMPLE);
            }
            return token.getUnescapedText();
        }

        // Refuses a window field that the data source says is not a date. A field the data source
        // doesn't list is let through, as an extraction pipeline can add fields of its own; rows that
        // turn out to have no time in it are left out when the query runs.
        private void checkWindowFieldIsDate(final AbstractToken fieldToken,
                                            final String field,
                                            final Query.Builder queryBuilder) {
            final Query query = queryBuilder.build();
            final DocRef dataSource = query.getDataSource();
            if (dataSource == null || joinClauses.hasBracketedFrom()) {
                return;
            }
            // The filter matches any field with the name in it, so every match is asked for to be
            // sure that the field itself is among them.
            final FindFieldCriteria criteria = new FindFieldCriteria(
                    PageRequest.unlimited(),
                    FindFieldCriteria.DEFAULT_SORT_LIST,
                    dataSource,
                    field,
                    null,
                    query.getNativeQuery());
            final ResultPage<QueryField> resultPage = queryFieldProviderProvider.get().findFields(criteria);
            for (final QueryField queryField : NullSafe.list(NullSafe.get(resultPage, ResultPage::getValues))) {
                if (field.equals(queryField.getFldName())
                    && queryField.getFldType() != null
                    && !FieldType.DATE.equals(queryField.getFldType())) {
                    throw new TokenException(fieldToken, "The window field '" + field + "' is not a date field, "
                            + "it is " + queryField.getFldType().getDisplayValue());
                }
            }
        }

        // The hidden columns that the rows of a query with a window clause are grouped by. The first
        // holds the window's key, which is what tells windows apart. The second holds the window's
        // start under the name that a result store keyed by time, such as a table builder rule's,
        // takes a row's time from.
        private List<Column> createWindowColumns(final AbstractToken token) {
            return List.of(
                    createWindowColumn(token, WINDOW_COLUMN_NAME, WINDOW_KEY_FUNCTION),
                    createWindowColumn(token, FieldIndex.DEFAULT_TIME_FIELD_NAME, WINDOW_START_FUNCTION));
        }

        private Column createWindowColumn(final AbstractToken token, final String name, final String function) {
            final ExpressionParser expressionParser = new ExpressionParser(new ParamFactory(expressionMap));
            try {
                final Expression expression = expressionParser.parse(expressionContext, fieldIndex, function);
                return Column.builder()
                        .id(name)
                        .name(name)
                        .expression(expression.toString())
                        .group(0)
                        .visible(false)
                        .special(true)
                        .build();
            } catch (final ParseException e) {
                throw new TokenException(token, e.getMessage());
            }
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

            // Group the rows by window if we have a time window.
            if (hasWindow) {
                columns.addAll(createWindowColumns(keywordGroup));
            }
            tableSettingsBuilder.addColumns(columns);
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
            final Query query = queryBuilder.build();
            final DocRef dataSource = query.getDataSource();
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

                // The fields of a data source that takes a native query depend on it.
                final FindFieldCriteria criteria = new FindFieldCriteria(
                        PageRequest.createDefault(),
                        FindFieldCriteria.DEFAULT_SORT_LIST,
                        dataSource,
                        filter,
                        null,
                        query.getNativeQuery());
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

            columnExpressions.put(columnName, expression);
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
            columnExpressions.put(columnName, expression);
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

        // A hidden column that holds a field as it is, for a condition to test. The field is named by
        // its plain name, so a quoted name in the where clause still names the field, not a value.
        private Column createFieldColumn(final String columnName, final String field) {
            final Token fieldToken = new Token(TokenType.STRING, field.toCharArray(), 0, field.length() - 1);
            final ExpressionParser expressionParser = new ExpressionParser(new ParamFactory(Collections.emptyMap()));
            try {
                final Expression expression = expressionParser.parse(
                        expressionContext,
                        fieldIndex,
                        Collections.singletonList(fieldToken));
                return Column.builder()
                        .id(columnName)
                        .name(columnName)
                        .expression(expression.toString())
                        .visible(false)
                        .special(true)
                        .build();
            } catch (final ParseException e) {
                throw new TokenException(fieldToken, e.getMessage());
            }
        }

        // Parses one side of a key as an expression, to check it and to put it in the same form as the
        // expressions of columns.
        @Override
        public String parseKeyExpression(final List<AbstractToken> tokens) {
            final ExpressionParser expressionParser = new ExpressionParser(new ParamFactory(expressionMap));
            try {
                return expressionParser.parse(expressionContext, fieldIndex, tokens).toString();
            } catch (final ParseException e) {
                throw new TokenException(tokens.getFirst(), e.getMessage());
            }
        }

        @Override
        public DocRef resolveDataSource(final AbstractToken dataSourceToken) {
            final String dataSourceName = resolveDataSourceName(dataSourceToken);
            return securityContext.useAsReadResult(() ->
                    dataSourceResolver.resolveDataSourceRef(dataSourceName));
        }

        // Parses a bracketed query, as in 'from (from X where Y group by Z select Z, count())', with its
        // own fields and eval, but the params and time range of the query it is in.
        @Override
        public ParsedQuery parseBracketedQuery(final TokenGroup tokenGroup) {
            final List<AbstractToken> children = tokenGroup.getChildren();
            if (children.isEmpty() || !TokenType.FROM.equals(children.getFirst().getTokenType())) {
                throw new TokenException(tokenGroup, "A bracketed query must start with from");
            }

            final Builder builder = new Builder(
                    visualisationTokenConsumer,
                    dataSourceResolver,
                    queryFieldProviderProvider,
                    securityContext,
                    maxWindowCopiesPerEvent,
                    true);
            // The bracketed query has the windows of its own window clause, if any, and not this query's.
            builder.expressionContext = expressionContext.copy().window(null).build();
            builder.paramMap = paramMap;
            builder.params = params;
            builder.timeRange = timeRange;

            final Query.Builder queryBuilder = Query.builder()
                    .params(params)
                    .timeRange(timeRange);
            final List<ResultRequest> resultRequests = builder.addTableSettings(
                    null, children, new ArrayList<>(), queryBuilder);
            Query query = queryBuilder.build();
            // The query it is in only takes a bracketed query's source, where and table, so its own
            // joins, alias or bracketed query would be dropped without a word.
            if (query.needsJoinSupport()) {
                throw new TokenException(tokenGroup, "A bracketed query can't have joins, an alias or a "
                                                     + "bracketed query of its own yet");
            }
            builder.checkNativeQuery(query);
            if (query.getExpression() == null) {
                query = query.copy().expression(ExpressionOperator.builder().build()).build();
            }
            final TableSettings tableSettings = resultRequests.getFirst().getMappings().getFirst();
            return new ParsedQuery(query, tableSettings);
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
}
