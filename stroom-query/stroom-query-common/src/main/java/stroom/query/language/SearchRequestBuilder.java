package stroom.query.language;

import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Filter;
import stroom.query.api.v2.ParamSubstituteUtil;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.ResultRequest.Fetch;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.Sort;
import stroom.query.api.v2.Sort.SortDirection;
import stroom.query.api.v2.TableSettings;
import stroom.query.language.PipeGroup.PipeOperation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class SearchRequestBuilder {

    public SearchRequest create(final String string, final SearchRequest in) {
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
        if (in != null && in.getQuery() != null) {
            queryBuilder.params(in.getQuery().getParams());
            queryBuilder.timeRange(in.getQuery().getTimeRange());
        }

        // Add data source.
        List<AbstractToken> remaining = addDataSource(childTokens, queryBuilder::dataSource);

        // Add expression.
        remaining = addExpression(remaining, queryBuilder::expression);

        // Try to make a query.
        Query query = queryBuilder.build();

        // Create result requests.
        final List<ResultRequest> resultRequests = new ArrayList<>();
        remaining = addTableSettings(remaining, true, resultRequests);

        return new SearchRequest(in.getKey(),
                query,
                resultRequests,
                in.getDateTimeSettings(),
                in.incremental(),
                in.getTimeout());
    }

    private List<AbstractToken> addDataSource(final List<AbstractToken> tokens, final Consumer<DocRef> consumer) {
        final AbstractToken firstToken = tokens.get(0);
        if (!TokenType.STRING.equals(firstToken.getTokenType()) &&
                !TokenType.SINGLE_QUOTED_STRING.equals(firstToken.getTokenType()) &&
                !TokenType.DOUBLE_QUOTED_STRING.equals(firstToken.getTokenType())) {
            throw new TokenException(firstToken, "Expected string");
        }
        final String dataSourceName = firstToken.getText();
        final DocRef dataSource = new DocRef(null, null, dataSourceName);
        consumer.accept(dataSource);
        return tokens.subList(1, tokens.size());
    }

    private List<AbstractToken> addExpression(final List<AbstractToken> tokens,
                                              final Consumer<ExpressionOperator> expressionConsumer) {
        List<AbstractToken> whereGroup = null;
        int i = 0;
        for (; i < tokens.size(); i++) {
            final AbstractToken token = tokens.get(i);
            if (token instanceof PipeGroup) {
                final PipeGroup pipeGroup = (PipeGroup) token;
                if (PipeOperation.WHERE.equals(pipeGroup.getPipeOperation())) {
                    if (whereGroup == null) {
                        whereGroup = new ArrayList<>();
                    }
                    whereGroup.add(pipeGroup);
                } else if (PipeOperation.AND.equals(pipeGroup.getPipeOperation()) ||
                        PipeOperation.OR.equals(pipeGroup.getPipeOperation()) ||
                        PipeOperation.NOT.equals(pipeGroup.getPipeOperation())) {
                    if (whereGroup == null) {
                        throw new TokenException(token, "Unexpected token");
                    }
                    whereGroup.add(pipeGroup);
                } else {
                    break;
                }
            }
        }

        if (whereGroup != null && whereGroup.size() > 0) {
            final ExpressionOperator expressionOperator = processLogic(whereGroup);
            expressionConsumer.accept(expressionOperator);
        }

        if (i < tokens.size()) {
            return tokens.subList(i, tokens.size());
        }
        return Collections.emptyList();
    }

    private void addTerm(
            final List<AbstractToken> tokens,
            final ExpressionOperator.Builder parentBuilder) {
        final AbstractToken field = tokens.get(0);
        final AbstractToken condition = tokens.get(1);
        final AbstractToken value = tokens.get(2);

        // If we have a where clause then we expect the next token to contain an expression.
        Condition cond;
        boolean not = false;
        switch (condition.getTokenType()) {
            case EQUALS:
                cond = Condition.EQUALS;
                break;
            case NOT_EQUALS:
                cond = Condition.EQUALS;
                not = true;
                break;
            case GREATER_THAN:
                cond = Condition.GREATER_THAN;
                break;
            case GREATER_THAN_OR_EQUAL_TO:
                cond = Condition.GREATER_THAN_OR_EQUAL_TO;
                break;
            case LESS_THAN:
                cond = Condition.LESS_THAN;
                break;
            case LESS_THAN_OR_EQUAL_TO:
                cond = Condition.LESS_THAN_OR_EQUAL_TO;
                break;
            case IS_NULL:
                cond = Condition.IS_NULL;
                break;
            case IS_NOT_NULL:
                cond = Condition.IS_NOT_NULL;
                break;
            default:
                throw new TokenException(condition, "Unknown condition: " + condition);
        }

        final ExpressionTerm expressionTerm = ExpressionTerm
                .builder()
                .field(field.getText())
                .condition(cond)
                .value(value.getText())
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

    private ExpressionOperator processLogic(final List<AbstractToken> tokens) {
        ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.AND);
        int i = 0;
        for (; i < tokens.size(); i++) {
            final AbstractToken token = tokens.get(i);
            if (token instanceof PipeGroup) {
                final PipeGroup pipeGroup = (PipeGroup) token;
                final PipeOperation pipeOperation = pipeGroup.getPipeOperation();
                if (PipeOperation.WHERE.equals(pipeOperation) ||
                        PipeOperation.AND.equals(pipeOperation)) {
                    builder = addAnd(builder, pipeGroup.getChildren());

                } else if (PipeOperation.OR.equals(pipeOperation)) {
                    builder = addOr(builder, pipeGroup.getChildren());

                } else if (PipeOperation.NOT.equals(pipeOperation)) {
                    builder = addNot(builder, pipeGroup.getChildren());

                } else {
                    throw new TokenException(token, "Unexpected pipe operation in query");
                }

            } else if (token instanceof TokenGroup) {
                TokenGroup tokenGroup = (TokenGroup) token;
                builder = addAnd(builder, tokenGroup.getChildren());

            } else if (TokenType.AND.equals(token.getTokenType())) {
                final List<AbstractToken> remaining = tokens.subList(i + 1, tokens.size());
                i = tokens.size();
                builder = addAnd(builder, remaining);

            } else if (TokenType.OR.equals(token.getTokenType())) {
                final List<AbstractToken> remaining = tokens.subList(i + 1, tokens.size());
                i = tokens.size();
                builder = addOr(builder, remaining);

            } else if (TokenType.NOT.equals(token.getTokenType())) {
                final List<AbstractToken> remaining = tokens.subList(i + 1, tokens.size());
                i = tokens.size();
                builder = addNot(builder, remaining);

            } else {
                // This should be the start token for a term.
                // We must have at least 3 regular tokens.
                final List<AbstractToken> termTokens = tokens.subList(i, Math.min(i + 3, tokens.size()));
                for (final AbstractToken t : termTokens) {
                    if (!(t instanceof Token)) {
                        throw new TokenException(t, "Unexpected token for term");
                    }
                }

                i += 2;
                if (termTokens.size() == 3) {
                    addTerm(termTokens, builder);
                }
            }
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
                                                 final boolean extractValues,
                                                 final List<ResultRequest> resultRequests) {
//        final Map<String, String> columnNames = new HashMap<>();
        final Map<String, String> functions = new HashMap<>();
        final Map<String, Sort> sortMap = new HashMap<>();
        final Map<String, Integer> groupMap = new HashMap<>();
        final Map<String, Filter> filterMap = new HashMap<>();
        int groupDepth = 0;

        final TableSettings.Builder tableSettingsBuilder = TableSettings.builder();

        int i = 0;
        for (; i < tokens.size(); i++) {
            final AbstractToken token = tokens.get(i);
            if (token instanceof PipeGroup) {
                final PipeGroup pipeGroup = (PipeGroup) token;
                switch (pipeGroup.getPipeOperation()) {
                    case EVAL -> processEvalPipeOperation(
                            pipeGroup,
                            functions);
                    case SORT -> processSortPipeOperation(
                            pipeGroup,
                            sortMap);
                    case GROUP -> {
                        processGroupPipeOperation(
                                pipeGroup,
                                groupMap,
                                groupDepth);
                        groupDepth++;
                    }
                    case TABLE -> processTablePipeOperation(
                            pipeGroup,
                            functions,
                            sortMap,
                            groupMap,
                            filterMap,
                            tableSettingsBuilder);
                    case LIMIT -> processLimitPipeOperation(
                            pipeGroup,
                            tableSettingsBuilder);
                }

//                if (PipeOperation.RENAME.equals(pipeGroup.getPipeOperation())) {
//                    processRenamePipeOperation(pipeGroup, columnNames);
//
//                } else if (PipeOperation.EVAL.equals(pipeGroup.getPipeOperation())) {
//                    processEvalPipeOperation(pipeGroup, functions);
//
//                } else if (PipeOperation.TABLE.equals(pipeGroup.getPipeOperation())) {
//                    processTablePipeOperation(pipeGroup, columnNames, functions, extractValues, resultRequests);
//
//                } else {
//                    break;
//                }
            } else {
                break;
            }
        }

        //        final DocRef resultPipeline = commonIndexingTestHelper.getSearchResultPipeline();
        final TableSettings tableSettings = tableSettingsBuilder
                .extractValues(extractValues)
//                .extractionPipeline(resultPipeline)
                .showDetail(true)
                .build();

        final ResultRequest tableResultRequest = new ResultRequest("1234",
                Collections.singletonList(tableSettings),
                null,
                null,
                ResultRequest.ResultStyle.TABLE,
                Fetch.ALL);
        resultRequests.add(tableResultRequest);

        if (i < tokens.size()) {
            return tokens.subList(i, tokens.size());
        }
        return Collections.emptyList();
    }

//    private void processRenamePipeOperation(final PipeGroup pipeGroup,
//                                            final Map<String, String> columnNames) {
//        final List<AbstractToken> children = pipeGroup.getChildren();
//        String field = null;
//        boolean afterAs = false;
//        for (final AbstractToken t : children) {
//            if (TokenType.isString(t)) {
//                if (afterAs) {
//                    if (field == null) {
//                        throw new TokenException(t, "Syntax exception");
//                    }
//                    columnNames.put(t.getText(), field);
//                    field = null;
//                    afterAs = false;
//                } else if (field != null) {
//                    throw new TokenException(t, "Syntax exception");
//                } else {
//                    field = t.getText();
//                }
//            } else if (TokenType.AS.equals(t.getTokenType())) {
//                afterAs = true;
//            } else if (TokenType.COMMA.equals(t.getTokenType())) {
//                field = null;
//            } else {
//                throw new TokenException(t, "Unexpected token");
//            }
//        }
//    }

    private void processEvalPipeOperation(final PipeGroup pipeGroup,
                                          final Map<String, String> functions) {
        final List<AbstractToken> children = pipeGroup.getChildren();

        String field = null;
        if (children.size() > 0) {
            AbstractToken token = children.get(0);
            if (!TokenType.isString(token)) {
                throw new TokenException(token, "Syntax exception");
            }
            field = token.getText();
        }

        if (children.size() > 1) {
            AbstractToken token = children.get(1);
            if (!TokenType.EQUALS.equals(token.getTokenType())) {
                throw new TokenException(token, "Syntax exception, expected equals");
            }
        }

        if (children.size() > 2) {
            final StringBuilder sb = new StringBuilder();

            // Turn the rest into a string.
            for (int i = 2; i < children.size(); i++) {
                AbstractToken token = children.get(i);
                addFunctionToken(token, sb, functions);
            }

            functions.put(field, sb.toString());
        }
    }

    private void addFunctionToken(final AbstractToken token,
                                  final StringBuilder sb,
                                  final Map<String, String> functions) {
        if (token instanceof FunctionGroup) {
            processFunction(((FunctionGroup) token), sb, functions);
        } else if (TokenType.STRING.equals(token.getTokenType())) {
            // Non quoted strings are identifiers.
            // See if there is already a mapping to the string.
            final String innerFunction = functions.get(token.getText());
            if (innerFunction != null) {
                sb.append(innerFunction);
            } else {
                // Adapt the string into a param substitute for compatibility.
                sb.append(ParamSubstituteUtil.makeParam(token.getText()));
            }
        } else if (token instanceof QuotedStringToken) {
            sb.append(((QuotedStringToken) token).getRawText());
        } else {
            sb.append(token.getText());
        }
    }

    private void processFunction(final FunctionGroup functionGroup,
                                 final StringBuilder sb,
                                 final Map<String, String> functions) {
        sb.append(functionGroup.getName());
        sb.append("(");
        final List<AbstractToken> functionGroupChildren = functionGroup.getChildren();
        for (final AbstractToken token : functionGroupChildren) {
            addFunctionToken(token, sb, functions);
        }
        sb.append(")");
    }

    private void processTablePipeOperation(final PipeGroup pipeGroup,
                                           final Map<String, String> functions,
                                           final Map<String, Sort> sortMap,
                                           final Map<String, Integer> groupMap,
                                           final Map<String, Filter> filterMap,
                                           final TableSettings.Builder tableSettingsBuilder) {
        final List<AbstractToken> children = pipeGroup.getChildren();
        String fieldName = null;
        String columnName = null;
        boolean afterAs = false;

        for (final AbstractToken t : children) {
            if (TokenType.isString(t)) {
                if (afterAs) {
                    if (fieldName == null) {
                        throw new TokenException(t, "Syntax exception, expected field name");
                    } else if (columnName != null) {
                        throw new TokenException(t, "Syntax exception, duplicate column name");
                    } else {
                        columnName = t.getText();
                    }
                } else if (fieldName == null) {
                    fieldName = t.getText();
                } else {
                    throw new TokenException(t, "Syntax exception, expected AS");
                }
            } else if (fieldName != null && TokenType.AS.equals(t.getTokenType())) {
                afterAs = true;

            } else if (TokenType.COMMA.equals(t.getTokenType())) {
                if (fieldName == null) {
                    throw new TokenException(t, "Syntax exception, expected field name");
                }

                addField(fieldName, columnName, functions, sortMap, groupMap, filterMap, tableSettingsBuilder);

                fieldName = null;
                columnName = null;
                afterAs = false;
            }
        }

        // Add final field if we have one.
        if (fieldName != null) {
            addField(fieldName, columnName, functions, sortMap, groupMap, filterMap, tableSettingsBuilder);
        }
    }

    private void addField(final String fieldName,
                          final String columnName,
                          final Map<String, String> functions,
                          final Map<String, Sort> sortMap,
                          final Map<String, Integer> groupMap,
                          final Map<String, Filter> filterMap,
                          final TableSettings.Builder tableSettingsBuilder) {
        String expression = functions.get(fieldName);
        if (expression == null) {
            expression = ParamSubstituteUtil.makeParam(fieldName);
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
                .build();
        tableSettingsBuilder.addFields(field);
    }

    private void processLimitPipeOperation(final PipeGroup pipeGroup,
                                           final TableSettings.Builder tableSettingsBuilder) {
        final List<AbstractToken> children = pipeGroup.getChildren();
        for (final AbstractToken t : children) {
            if (TokenType.isString(t) || TokenType.NUMBER.equals(t.getTokenType())) {
                try {
                    tableSettingsBuilder.addMaxResults(Integer.parseInt(t.getText()));
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

    private void processSortPipeOperation(final PipeGroup pipeGroup,
                                          final Map<String, Sort> sortMap) {
        String fieldName = null;
        SortDirection direction = null;
        final List<AbstractToken> children = pipeGroup.getChildren();
        boolean first = true;
        for (final AbstractToken t : children) {
            if (first && TokenType.BY.equals(t.getTokenType())) {

            } else {
                if (TokenType.isString(t)) {
                    if (fieldName == null) {
                        fieldName = t.getText();
                    } else if (direction == null) {
                        try {
                            if (t.getText().toLowerCase(Locale.ROOT).equalsIgnoreCase("asc")) {
                                direction = SortDirection.ASCENDING;
                            } else if (t.getText().toLowerCase(Locale.ROOT).equalsIgnoreCase("desc")) {
                                direction = SortDirection.DESCENDING;
                            } else {
                                direction = SortDirection.valueOf(t.getText());
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

    private void processGroupPipeOperation(final PipeGroup pipeGroup,
                                           final Map<String, Integer> groupMap,
                                           final int groupDepth) {
        String fieldName = null;
        final List<AbstractToken> children = pipeGroup.getChildren();
        boolean first = true;
        for (final AbstractToken t : children) {
            if (first && TokenType.BY.equals(t.getTokenType())) {

            } else {
                if (TokenType.isString(t)) {
                    if (fieldName == null) {
                        fieldName = t.getText();
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
}
