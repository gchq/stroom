package stroom.query.language;

import stroom.expression.api.ExpressionContext;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Filter;
import stroom.query.api.v2.Format;
import stroom.query.api.v2.HoppingWindow;
import stroom.query.api.v2.ParamSubstituteUtil;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.ResultRequest.Fetch;
import stroom.query.api.v2.ResultRequest.ResultStyle;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.Sort;
import stroom.query.api.v2.Sort.SortDirection;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.language.functions.Expression;
import stroom.query.language.functions.ExpressionParser;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Function;
import stroom.query.language.functions.FunctionFactory;
import stroom.query.language.functions.ParamFactory;
import stroom.query.language.token.AbstractToken;
import stroom.query.language.token.FunctionGroup;
import stroom.query.language.token.KeywordGroup;
import stroom.query.language.token.QuotedStringToken;
import stroom.query.language.token.StructureBuilder;
import stroom.query.language.token.Token;
import stroom.query.language.token.TokenException;
import stroom.query.language.token.TokenGroup;
import stroom.query.language.token.TokenType;
import stroom.query.language.token.Tokeniser;

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
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class SearchRequestBuilder {

    public static final String TABLE_COMPONENT_ID = "table";
    public static final String VIS_COMPONENT_ID = "vis";

    private final VisualisationTokenConsumer visualisationTokenConsumer;

    private ExpressionContext expressionContext;
    private final FieldIndex fieldIndex;
    private final Map<String, Expression> expressionMap;

    @Inject
    public SearchRequestBuilder(final VisualisationTokenConsumer visualisationTokenConsumer) {
        this.visualisationTokenConsumer = visualisationTokenConsumer;
        this.fieldIndex = new FieldIndex();
        this.expressionMap = new HashMap<>();
    }

    public void extractDataSourceNameOnly(final String string, final Consumer<String> consumer) {
        // Get a list of tokens.
        final List<Token> tokens = Tokeniser.parse(string);
        if (tokens.size() == 0) {
            throw new TokenException(null, "No tokens");
        }

        // Create structure.
        final TokenGroup tokenGroup = StructureBuilder.create(tokens);

        // Assume we have a root bracket group.
        final List<AbstractToken> childTokens = tokenGroup.getChildren();

        // Add data source.
        addDataSourceName(childTokens, new HashSet<>(), consumer, true);
    }

    public SearchRequest create(final String string,
                                final SearchRequest in,
                                final ExpressionContext expressionContext) {
        Objects.requireNonNull(in, "Null sample request");
        Objects.requireNonNull(expressionContext, "Null expression context");
        Objects.requireNonNull(expressionContext.getDateTimeSettings(), "Null date time settings");

        // Set the expression context.
        this.expressionContext = expressionContext;

        // Get a list of tokens.
        final List<Token> tokens = Tokeniser.parse(string);
        if (tokens.size() == 0) {
            throw new TokenException(null, "No tokens");
        }

        // Create structure.
        final TokenGroup tokenGroup = StructureBuilder.create(tokens);

        // Assume we have a root bracket group.
        final List<AbstractToken> childTokens = tokenGroup.getChildren();

        final Query.Builder queryBuilder = Query.builder();
        if (in.getQuery() != null) {
            queryBuilder.params(in.getQuery().getParams());
            queryBuilder.timeRange(in.getQuery().getTimeRange());
        }

        // Keep track of consumed tokens to check token order.
        final Set<TokenType> consumedTokens = new HashSet<>();

        // Add data source.
        List<AbstractToken> remaining = addDataSourceName(childTokens, consumedTokens, queryBuilder::dataSourceName);

        // Add where expression.
        remaining = addExpression(remaining, consumedTokens, TokenType.WHERE, queryBuilder::expression);

        // Try to make a query.
        Query query = queryBuilder.build();
        if (query.getDataSource() == null) {
            throw new TokenException(null, "No data source has been specified.");
        }
//        if (query.getExpression() == null) {
//            throw new TokenException(null, "No query expression has been defined, are you missing a `where` clause?");
//        }

        // Create result requests.
        final List<ResultRequest> resultRequests = new ArrayList<>();
        remaining = addTableSettings(remaining, consumedTokens, true, resultRequests);

        return new SearchRequest(
                in.getSearchRequestSource(),
                in.getKey(),
                query,
                resultRequests,
                in.getDateTimeSettings(),
                in.incremental(),
                in.getTimeout());
    }

    private List<AbstractToken> addDataSourceName(final List<AbstractToken> tokens,
                                                  final Set<TokenType> consumedTokens,
                                                  final Consumer<String> consumer) {
        return addDataSourceName(tokens, consumedTokens, consumer, false);
    }

    /**
     * @param isLenient Ignores any additional child tokens found. This is to support getting just
     *                  the data source from a partially written query, which is likely to not be
     *                  a valid query. For example, you may type
     *                  <pre>{@code from View sel}</pre> and at this point do code completion on 'sel'
     *                  so if this method is called it will treat 'sel as an extra child.
     */
    private List<AbstractToken> addDataSourceName(final List<AbstractToken> tokens,
                                                  final Set<TokenType> consumedTokens,
                                                  final Consumer<String> consumer,
                                                  final boolean isLenient) {
        AbstractToken token = tokens.get(0);

        // The first token must be `FROM`.
        if (!TokenType.FROM.equals(token.getTokenType())) {
            throw new TokenException(token, "Expected from");
        }

        final KeywordGroup keywordGroup = (KeywordGroup) token;
        if (keywordGroup.getChildren().size() == 0) {
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
        consumer.accept(dataSourceName);

        consumedTokens.add(token.getTokenType());
        return tokens.subList(1, tokens.size());
    }

    private List<AbstractToken> addExpression(final List<AbstractToken> tokens,
                                              final Set<TokenType> consumedTokens,
                                              final TokenType keyword,
                                              final Consumer<ExpressionOperator> expressionConsumer) {
        List<AbstractToken> whereGroup = new ArrayList<>();
        final Set<TokenType> localConsumedTokens = new HashSet<>();
        int i = 0;
        for (; i < tokens.size(); i++) {
            final AbstractToken token = tokens.get(i);

            // Check we have had a FROM
            if (i == 0) {
                checkConsumed(token, consumedTokens, new TokenType[]{TokenType.FROM}, new TokenType[]{});
            }

            if (token instanceof final KeywordGroup keywordGroup) {
                final TokenType tokenType = keywordGroup.getTokenType();
                if (keyword.equals(tokenType)) {
                    // Make sure we haven't already consumed the keyword token.
                    checkConsumed(token, localConsumedTokens, new TokenType[]{}, new TokenType[]{keyword});
                    localConsumedTokens.add(token.getTokenType());
                    consumedTokens.add(token.getTokenType());
                    whereGroup.add(keywordGroup);
                } else if (TokenType.AND.equals(tokenType) ||
                        TokenType.OR.equals(tokenType) ||
                        TokenType.NOT.equals(tokenType)) {
                    // Check we have already consumed the expected keyword token.
                    checkConsumed(token, localConsumedTokens, new TokenType[]{keyword}, new TokenType[]{});
                    localConsumedTokens.add(token.getTokenType());
                    consumedTokens.add(token.getTokenType());
                    whereGroup.add(keywordGroup);
                } else {
                    break;
                }
            }
        }

        if (whereGroup.size() > 0) {
            final ExpressionOperator expressionOperator = processLogic(whereGroup);
            expressionConsumer.accept(expressionOperator);
        } else {
            expressionConsumer.accept(ExpressionOperator.builder().build());
        }

        if (i < tokens.size()) {
            return tokens.subList(i, tokens.size());
        }
        return Collections.emptyList();
    }

    private void addTerm(
            final List<AbstractToken> tokens,
            final ExpressionOperator.Builder parentBuilder) {
        if (tokens.size() < 3) {
            throw new TokenException(tokens.get(0), "Incomplete term");
        }

        final AbstractToken fieldToken = tokens.get(0);
        final AbstractToken conditionToken = tokens.get(1);

        if (!TokenType.isString(fieldToken)) {
            throw new TokenException(fieldToken, "Expected field string");
        }
        if (!TokenType.CONDITIONS.contains(conditionToken.getTokenType())) {
            throw new TokenException(conditionToken, "Expected condition token");
        }

        final String field = fieldToken.getUnescapedText();
        final StringBuilder value = new StringBuilder();
        int end = addValue(tokens, value, 2);

        if (TokenType.BETWEEN.equals(conditionToken.getTokenType())) {
            if (tokens.size() == end) {
                throw new TokenException(tokens.get(tokens.size() - 1), "Expected between and");
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
        Condition cond;
        boolean not = false;
        switch (conditionToken.getTokenType()) {
            case EQUALS -> cond = Condition.EQUALS;
            case NOT_EQUALS -> {
                cond = Condition.EQUALS;
                not = true;
            }
            case GREATER_THAN -> cond = Condition.GREATER_THAN;
            case GREATER_THAN_OR_EQUAL_TO -> cond = Condition.GREATER_THAN_OR_EQUAL_TO;
            case LESS_THAN -> cond = Condition.LESS_THAN;
            case LESS_THAN_OR_EQUAL_TO -> cond = Condition.LESS_THAN_OR_EQUAL_TO;
            case IS_NULL -> cond = Condition.IS_NULL;
            case IS_NOT_NULL -> cond = Condition.IS_NOT_NULL;
            case BETWEEN -> cond = Condition.BETWEEN;
            default -> throw new TokenException(conditionToken, "Unknown condition: " + conditionToken);
        }

        final ExpressionTerm expressionTerm = ExpressionTerm
                .builder()
                .field(field)
                .condition(cond)
                .value(value.toString().trim())
                .build();

        if (not) {
            parentBuilder
                    .addOperator(ExpressionOperator
                            .builder()
                            .op(Op.NOT)
                            .addTerm(expressionTerm)
                            .build());
        } else {
            parentBuilder.addTerm(expressionTerm);
        }
    }

    private int addValue(final List<AbstractToken> tokens,
                         final StringBuilder value,
                         final int start) {
//        TokenType numericOperator = null;
//        boolean datePointMode = false;
//        boolean firstToken = true;
//

        final StringBuilder sb = new StringBuilder();
        for (int i = start; i < tokens.size(); i++) {
            final AbstractToken token = tokens.get(i);

            if (TokenType.BETWEEN_AND.equals(token.getTokenType())) {
                final String expression = sb.toString();

                if (i - start > 1) {
                    // If we have more than one token then perhaps this is a date expression.
                    try {
                        DateExpressionParser.parse(expression, expressionContext.getDateTimeSettings());
                    } catch (final RuntimeException e) {
                        throw new TokenException(tokens.get(start + 1), "Unexpected token");
                    }
                }

                value.append(expression);
                return i;
            } else {
                sb.append(token.getUnescapedText());
            }
        }

        final String expression = sb.toString();
        if (tokens.size() - start > 1) {
            // If we have more than one token then perhaps this is a date expression.
            try {
                DateExpressionParser.parse(expression, expressionContext.getDateTimeSettings());
            } catch (final RuntimeException e) {
                throw new TokenException(tokens.get(start + 1), "Unexpected token");
            }
        }
        value.append(expression);

        return tokens.size();

//            } else if (TokenType.NUMERIC_OPERATOR.contains(token.tokenType)) {
//                if (firstToken) {
//                    // At the start of a value only treat as a numeric operator if + or -.
//                    if (isPlusOrMinus(token.tokenType)) {
//                        numericOperator = token.tokenType;
//                    } else if (!TokenType.MULTIPLICATION.equals(token.tokenType)) {
//                        // We allow `*` to be treated as text but all other operators are forbidden at the start.
//                        throw new TokenException(token, "Unexpected numeric operator");
//                    }
//
//                    value.append(token.getUnescapedText());
//
//                } else {
//                    if (numericOperator != null) {
//                        // Only allow multiple +- operators.
//                        if (!isPlusOrMinus(numericOperator)) {
//                            throw new TokenException(token, "Unexpected numeric operator");
//                        }
//                    }
//
//                    value.append(token.getUnescapedText());
//                    numericOperator = token.tokenType;
//                }
//
//            } else {
//                // Only allow subsequent value tokens if they follow an operator.
//                if (!firstToken && numericOperator == null) {
//                    throw new TokenException(token, "Unexpected token");
//                }
//
//                // Make sure we have a value token, e.g. a string or number.
//                if (!TokenType.VALUES.contains(token.tokenType)) {
//                    // We allow date point functions in values (e.g. `day()`) so see if we have one of those.
//                    if (isDatePoint(token)) {
//                        if (!firstToken) {
//                            throw new TokenException(token, "Unexpected date point function");
//                        } else {
//                            datePointMode = true;
//                        }
//                    } else {
//                        throw new TokenException(token, "Expected value or date point function");
//                    }
//                }
//
//                if (numericOperator != null) {
//                    if (datePointMode) {
//                        validateDuration(token, numericOperator);
//                    } else {
//                        validateNumber(token);
//                    }
//                }
//
//                value.append(token.getUnescapedText());
//                numericOperator = null;
//            }
//
//            firstToken = false;
//        }
//        return tokens.size();
    }


//    private boolean isDatePoint(final AbstractToken token) {
//        final String text = token.getText();
//        for (final DateExpressionParser.DatePoint datePoint : DateExpressionParser.DatePoint.values()) {
//            if (datePoint.getFunction().equals(text)) {
//                return true;
//            }
//        }
//        return false;
//    }

    private boolean isPlusOrMinus(final TokenType numericOperator) {
        return numericOperator.equals(TokenType.PLUS) || numericOperator.equals(TokenType.MINUS);
    }

//    private void validateDuration(final AbstractToken token, final TokenType numericOperator) {
//        // Make sure expression is duration.
//        if (!isPlusOrMinus(numericOperator)) {
//            throw new TokenException(token, "Unexpected operator on duration");
//        }
//        if (token instanceof final FunctionGroup functionGroup) {
//            if (!functionGroup.getName().equals("duration") &&
//                    !functionGroup.getName().equals("period")) {
//                throw new TokenException(token, "Expected duration");
//            }
//        } else {
//            throw new TokenException(token, "Expected duration");
//        }
//    }

    private boolean isNumber(final AbstractToken token) {
        // See if this might be a quantity.
        if (Character.isDigit(token.getText().charAt(0))) {
            validateNumber(token);
            return true;
        }
        return false;
    }

    private void validateNumber(final AbstractToken token) {
        // Make sure expression is a number.
        try {
            Double.parseDouble(token.getText());
        } catch (final RuntimeException e) {
            throw new TokenException(token, "Unable to parse number");
        }
    }

    private ExpressionOperator processLogic(final List<AbstractToken> tokens) {
        ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.AND);
        final List<AbstractToken> termTokens = new ArrayList<>();

        int i = 0;
        for (; i < tokens.size(); i++) {
            final AbstractToken token = tokens.get(i);
            final TokenType tokenType = token.getTokenType();

            final boolean logic =
                    TokenType.AND.equals(tokenType) ||
                            TokenType.OR.equals(tokenType) ||
                            TokenType.NOT.equals(tokenType);

            if (!(token instanceof KeywordGroup) &&
                    !(token instanceof TokenGroup) &&
                    !logic) {

                // Treat token as part of a term.
                termTokens.add(token);

            } else {
                // Add current term.
                if (termTokens.size() > 0) {
                    addTerm(termTokens, builder);
                    termTokens.clear();
                }

                if (token instanceof final KeywordGroup keywordGroup) {
                    switch (tokenType) {
                        case WHERE, HAVING, FILTER, AND -> builder = addAnd(builder, keywordGroup.getChildren());
                        case OR -> builder = addOr(builder, keywordGroup.getChildren());
                        case NOT -> builder = addNot(builder, keywordGroup.getChildren());
                        default -> throw new TokenException(token, "Unexpected pipe operation in query");
                    }

                } else if (token instanceof final TokenGroup tokenGroup) {
                    builder = addAnd(builder, tokenGroup.getChildren());

                } else if (logic) {
                    final List<AbstractToken> remaining = tokens.subList(i + 1, tokens.size());
                    i = tokens.size();

                    switch (tokenType) {
                        case AND -> builder = addAnd(builder, remaining);
                        case OR -> builder = addOr(builder, remaining);
                        case NOT -> builder = addNot(builder, remaining);
                        default -> throw new TokenException(token, "Unexpected token");
                    }
                }
            }
        }

        // Add remaining term.
        if (termTokens.size() > 0) {
            addTerm(termTokens, builder);
            termTokens.clear();
        }

        return builder.build();
    }

    private ExpressionOperator.Builder addAnd(final ExpressionOperator.Builder builder,
                                              final List<AbstractToken> tokens) {
        ExpressionOperator.Builder nextBuilder = builder;
        final ExpressionOperator childOperator = processLogic(tokens);
        if (childOperator.hasChildren()) {
            nextBuilder = ExpressionOperator.builder().op(Op.AND);
            final ExpressionOperator current = builder.build();
            if (current.hasChildren()) {
                nextBuilder.addOperator(current);
            }
            nextBuilder.addOperator(childOperator);
        }
        return nextBuilder;
    }

    private ExpressionOperator.Builder addOr(final ExpressionOperator.Builder builder,
                                             final List<AbstractToken> tokens) {
        ExpressionOperator.Builder nextBuilder = builder;
        final ExpressionOperator childOperator = processLogic(tokens);
        if (childOperator.hasChildren()) {
            nextBuilder = ExpressionOperator.builder().op(Op.OR);
            final ExpressionOperator current = builder.build();
            if (current.hasChildren()) {
                nextBuilder.addOperator(current);
            }
            nextBuilder.addOperator(childOperator);
        }
        return nextBuilder;
    }

    private ExpressionOperator.Builder addNot(final ExpressionOperator.Builder builder,
                                              final List<AbstractToken> tokens) {
        final ExpressionOperator childOperator = processLogic(tokens);
        if (childOperator.hasChildren()) {
            final ExpressionOperator not = ExpressionOperator
                    .builder()
                    .op(Op.NOT)
                    .addOperator(childOperator)
                    .build();
            builder.addOperator(not);
        }
        return builder;
    }

    private List<AbstractToken> addTableSettings(final List<AbstractToken> tokens,
                                                 final Set<TokenType> consumedTokens,
                                                 final boolean extractValues,
                                                 final List<ResultRequest> resultRequests) {
        final Map<String, String> variables = new HashMap<>();
        final Map<String, Sort> sortMap = new HashMap<>();
        final Map<String, Integer> groupMap = new HashMap<>();
        final Map<String, Filter> filterMap = new HashMap<>();
        int groupDepth = 0;

        final TableSettings.Builder tableSettingsBuilder = TableSettings.builder();
        TableSettings visTableSettings = null;

        List<AbstractToken> remaining = new LinkedList<>(tokens);
        while (remaining.size() > 0) {
            final AbstractToken token = remaining.get(0);

            // Check we have seen FROM and haven't already seen the SELECT.
            if (!TokenType.EVAL.equals(token.getTokenType())) {
                checkConsumed(token,
                        consumedTokens,
                        new TokenType[]{TokenType.FROM},
                        new TokenType[]{token.getTokenType()});
            }
            consumedTokens.add(token.getTokenType());

            if (token instanceof final KeywordGroup keywordGroup) {
                switch (keywordGroup.getTokenType()) {
                    case FILTER -> remaining =
                            addExpression(remaining,
                                    consumedTokens,
                                    TokenType.FILTER,
                                    tableSettingsBuilder::valueFilter);
                    case EVAL -> {
                        processEval(
                                keywordGroup,
                                variables);
                        remaining.remove(0);
                    }
                    case WINDOW -> {
                        processWindow(
                                keywordGroup,
                                tableSettingsBuilder);
                        remaining.remove(0);
                    }
                    case SORT -> {
                        processSortBy(
                                keywordGroup,
                                sortMap);
                        remaining.remove(0);
                    }
                    case GROUP -> {
                        processGroupBy(
                                keywordGroup,
                                groupMap,
                                groupDepth);
                        groupDepth++;
                        remaining.remove(0);
                        consumedTokens.remove(token.getTokenType());
                    }
                    case HAVING -> remaining =
                            addExpression(remaining,
                                    consumedTokens,
                                    TokenType.HAVING,
                                    tableSettingsBuilder::aggregateFilter);
                    case SELECT -> {
                        processSelect(
                                keywordGroup,
                                variables,
                                sortMap,
                                groupMap,
                                filterMap,
                                tableSettingsBuilder);
                        remaining.remove(0);
                    }
                    case LIMIT -> {
                        processLimit(
                                keywordGroup,
                                tableSettingsBuilder);
                        remaining.remove(0);
                    }
                    case VIS -> {
                        final TableSettings parentTableSettings = tableSettingsBuilder.build();
                        visTableSettings = visualisationTokenConsumer
                                .processVis(keywordGroup, parentTableSettings);
                        remaining.remove(0);
                    }
                }
            } else {
                break;
            }
        }

        //        final DocRef resultPipeline = commonIndexingTestHelper.getSearchResultPipeline();
        final TableSettings tableSettings = tableSettingsBuilder
                .extractValues(extractValues)
//                .extractionPipeline(resultPipeline)
//                .showDetail(true)
                .build();

        final ResultRequest tableResultRequest = new ResultRequest(TABLE_COMPONENT_ID,
                Collections.singletonList(tableSettings),
                null,
                null,
                null,
                ResultStyle.TABLE,
                Fetch.ALL);
        resultRequests.add(tableResultRequest);

        if (visTableSettings != null) {
            final List<TableSettings> tableSettingsList = new ArrayList<>();
            tableSettingsList.add(tableSettings);
            tableSettingsList.add(visTableSettings);
            final ResultRequest qlVisResultRequest = new ResultRequest(VIS_COMPONENT_ID,
                    tableSettingsList,
                    null,
                    null,
                    null,
                    ResultStyle.QL_VIS,
                    Fetch.ALL);
            resultRequests.add(qlVisResultRequest);
        }

        return remaining;
    }

    private void processWindow(final KeywordGroup keywordGroup,
                               final TableSettings.Builder builder) {
        final List<AbstractToken> children = keywordGroup.getChildren();

        String field;
        String durationString;
        String advanceString = null;
        if (children.size() > 0) {
            AbstractToken token = children.get(0);
            if (!TokenType.isString(token)) {
                throw new TokenException(token, "Syntax exception");
            }
            field = token.getUnescapedText();
        } else {
            throw new TokenException(keywordGroup, "Expected field");
        }

        if (children.size() > 1) {
            AbstractToken token = children.get(1);
            if (!TokenType.BY.equals(token.getTokenType())) {
                throw new TokenException(token, "Syntax exception, expected by");
            }
        } else {
            throw new TokenException(keywordGroup, "Syntax exception, expected by");
        }

        if (children.size() > 2) {
            AbstractToken token = children.get(2);
            if (!TokenType.isString(token)) {
                throw new TokenException(token, "Syntax exception, expected by");
            }
            durationString = token.getUnescapedText();
        } else {
            throw new TokenException(keywordGroup, "Syntax exception, expected window duration");
        }

        if (children.size() > 3) {
            AbstractToken token = children.get(3);
            if (!TokenType.isString(token)) {
                throw new TokenException(token, "Syntax exception, expected advance");
            }
            if (!token.getUnescapedText().equals("advance")) {
                throw new TokenException(token, "Syntax exception, expected advance");
            }

            if (children.size() > 4) {
                token = children.get(4);
                if (!TokenType.isString(token)) {
                    throw new TokenException(token, "Syntax exception, expected advance duration");
                }
                advanceString = token.getUnescapedText();
            } else {
                throw new TokenException(keywordGroup, "Syntax exception, expected advance duration");
            }
        }

        builder.window(HoppingWindow.builder()
                .timeField(field)
                .windowSize(durationString)
                .advanceSize(advanceString == null
                        ? durationString
                        : advanceString)
                .build());
    }

    private void processEval(final KeywordGroup keywordGroup,
                             final Map<String, String> variables) {
        final List<AbstractToken> children = keywordGroup.getChildren();

        // Check we have a variable name.
        if (children.size() == 0) {
            throw new TokenException(keywordGroup, "Expected variable name following eval");
        }
        final AbstractToken variableToken = children.get(0);
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
        if (children.size() < 2) {
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

//        // Turn the rest into a string.
//        final StringBuilder sb = new StringBuilder();
//        addFunctionChildren(children, sb, variables, 2, false);
//        variables.put(variable, sb.toString());
    }

//    private void addFunctionToken(final AbstractToken token,
//                                  final StringBuilder sb,
//                                  final Map<String, String> variables) {
//        if (token instanceof FunctionGroup) {
//            processFunction(((FunctionGroup) token), sb, variables);
//        } else if (TokenType.STRING.equals(token.getTokenType())) {
//            // Non quoted strings are identifiers.
//            // See if there is already a mapping to the string.
//            final String innerFunction = variables.get(token.getUnescapedText());
//            if (innerFunction != null) {
//                sb.append(innerFunction);
//            } else {
//                // See if this might be a number.
//                if (isNumber(token)) {
//                    sb.append(token.getText());
//
//                } else {
//                    // Adapt the string into a param substitute for compatibility.
//                    sb.append(ParamSubstituteUtil.makeParam(token.getUnescapedText()));
//                }
//            }
//        } else if (token instanceof QuotedStringToken) {
//            sb.append(token.getText());
//        } else {
//            sb.append(token.getUnescapedText());
//        }
//    }
//
//    private void processFunction(final FunctionGroup functionGroup,
//                                 final StringBuilder sb,
//                                 final Map<String, String> variables) {
//        final StringBuilder functionString = new StringBuilder();
//        validateFunctionName(functionGroup);
//        functionString.append(functionGroup.getName());
//        functionString.append("(");
//        final List<AbstractToken> functionGroupChildren = functionGroup.getChildren();
//        addFunctionChildren(functionGroupChildren, functionString, variables, 0, true);
//        functionString.append(")");
//
//        validateFunction(functionGroup, functionString.toString());
//
//        sb.append(functionString);
//    }
//
//    private void validateFunction(final AbstractToken token, final String expression) {
//        try {
//            new ExpressionParser(new ParamFactory()).parse(expressionContext,
//                    new FieldIndex(),
//                    expression);
//        } catch (final ParseException e) {
//            throw new TokenException(token, e.getMessage());
//        }
//    }
//
//    private void addFunctionChildren(final List<AbstractToken> functionGroupChildren,
//                                     final StringBuilder sb,
//                                     final Map<String, String> variables,
//                                     final int start,
//                                     final boolean allowComma) {
//        TokenType numericOperator = null;
//        boolean firstToken = true;
//        for (int i = start; i < functionGroupChildren.size(); i++) {
//            final AbstractToken token = functionGroupChildren.get(i);
//            if (TokenType.NUMERIC_OPERATOR.contains(token.getTokenType())) {
//                if (firstToken) {
//                    // At the start of a value only treat as a numeric operator if + or -.
//                    if (isPlusOrMinus(token.getTokenType())) {
//                        numericOperator = token.getTokenType();
//                    } else if (!TokenType.MULTIPLICATION.equals(token.getTokenType())) {
//                        // We allow `*` to be treated as text but all other operators are forbidden at the start.
//                        throw new TokenException(token, "Unexpected numeric operator");
//                    }
//                } else {
//                    if (numericOperator != null) {
//                        // Only allow multiple +- operators.
//                        if (!isPlusOrMinus(numericOperator)) {
//                            throw new TokenException(token, "Unexpected numeric operator");
//                        }
//                    }
//                    numericOperator = token.getTokenType();
//                }
//            }
//
//            // Only allow subsequent tokens if they follow an operator.
//            if (!firstToken && numericOperator == null) {
//                throw new TokenException(token, "Unexpected token");
//            }
//
//            addFunctionToken(token, sb, variables);
//            firstToken = false;
//
//            if (TokenType.COMMA.equals(token.getTokenType())) {
//                if (allowComma) {
//                    // Begin new set of tokens.
//                    firstToken = true;
//                } else {
//                    throw new TokenException(token, "Unexpected comma");
//                }
//            }
//        }
//    }
//
//    private void validateFunctionName(final FunctionGroup functionGroup) {
//        final Function function;
//        try {
//            // Validate function name.
//            function = FunctionFactory
//                    .create(expressionContext, functionGroup.getName());
//        } catch (final RuntimeException e) {
//            throw new TokenException(functionGroup, e.getMessage());
//        }
//
//        if (function == null) {
//            throw new TokenException(functionGroup, "Unknown function name: " + functionGroup.getName());
//        }
//    }

    private void processSelect(final KeywordGroup keywordGroup,
                               final Map<String, String> functions,
                               final Map<String, Sort> sortMap,
                               final Map<String, Integer> groupMap,
                               final Map<String, Filter> filterMap,
                               final TableSettings.Builder tableSettingsBuilder) {
        final List<AbstractToken> children = keywordGroup.getChildren();
        String fieldName = null;
        String columnName = null;
        boolean afterAs = false;

        for (final AbstractToken t : children) {
            if (TokenType.isString(t)) {
                if (afterAs) {
                    if (columnName != null) {
                        throw new TokenException(t, "Syntax exception, duplicate column name");
                    } else {
                        columnName = t.getUnescapedText();
                    }
                } else if (fieldName == null) {
                    fieldName = t.getUnescapedText();
                } else {
                    throw new TokenException(t, "Syntax exception, expected AS");
                }
            } else if (TokenType.AS.equals(t.getTokenType())) {
                if (fieldName == null) {
                    throw new TokenException(t, "Syntax exception, expected field name");
                }

                afterAs = true;

            } else if (TokenType.COMMA.equals(t.getTokenType())) {
                if (fieldName == null) {
                    throw new TokenException(t, "Syntax exception, expected field name");
                }

                addField(fieldName, columnName, sortMap, groupMap, filterMap, tableSettingsBuilder);

                fieldName = null;
                columnName = null;
                afterAs = false;
            }
        }

        // Add final field if we have one.
        if (fieldName != null) {
            addField(fieldName, columnName, sortMap, groupMap, filterMap, tableSettingsBuilder);
        }
    }

    private void addField(final String fieldName,
                          final String columnName,
                          final Map<String, Sort> sortMap,
                          final Map<String, Integer> groupMap,
                          final Map<String, Filter> filterMap,
                          final TableSettings.Builder tableSettingsBuilder) {
        Expression expression = expressionMap.get(fieldName);
        if (expression == null) {
            ExpressionParser expressionParser = new ExpressionParser(new ParamFactory(expressionMap));
            try {
                expression = expressionParser.parse(expressionContext, fieldIndex, fieldName);
            } catch (final ParseException e) {
                // TODO: throw token exception....
            }
        }

        Format format = Format.GENERAL;
            if (expression != null) {
                switch (expression.getCommonReturnType()) {
                    case DATE -> format = Format.DATE_TIME;
                    case LONG, INTEGER, DOUBLE, FLOAT -> format = Format.NUMBER;
                    default -> {
                    }
                }
            }

        final Field field = Field.builder()
                .id(fieldName)
                .name(columnName != null
                        ? columnName
                        : fieldName)
                .expression(expression)
                .sort(sortMap.get(fieldName))
                .group(groupMap.get(fieldName))
                .filter(filterMap.get(fieldName))
                .format(format)
                .build();
        tableSettingsBuilder.addFields(field);
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
            } else if (TokenType.COMMA.equals(t.getTokenType())) {
                // Expected.
            } else {
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
            if (first && TokenType.BY.equals(t.getTokenType())) {
                // Ignore
            } else {
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
                            throw new TokenException(t, "Syntax exception, expected sort direction 'asc' or 'desc'");
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
            if (first && TokenType.BY.equals(t.getTokenType())) {
                // Ignore
            } else {
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

    private void checkConsumed(final AbstractToken token,
                               final Set<TokenType> consumedTokens,
                               final TokenType[] expectedConsumed,
                               final TokenType[] expectedNotConsumed) {
        for (final TokenType tokenType : expectedConsumed) {
            if (!consumedTokens.contains(tokenType)) {
                throw new TokenException(token,
                        "Expected token " + tokenType + " before " + token.getTokenType());
            }
        }
        for (final TokenType tokenType : expectedNotConsumed) {
            if (consumedTokens.contains(tokenType)) {
                throw new TokenException(token,
                        "Unexpected token " + token.getTokenType() + " after " + tokenType);
            }
        }
    }
}
