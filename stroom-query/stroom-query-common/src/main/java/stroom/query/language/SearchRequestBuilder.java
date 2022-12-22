package stroom.query.language;

import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.ParamSubstituteUtil;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.ResultRequest.Fetch;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.TableSettings;
import stroom.query.language.PipeGroup.PipeOperation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
        final Map<String, String> columnNames = new HashMap<>();

        int i = 0;
        for (; i < tokens.size(); i++) {
            final AbstractToken token = tokens.get(i);
            if (token instanceof PipeGroup) {
                final PipeGroup pipeGroup = (PipeGroup) token;
                if (PipeOperation.RENAME.equals(pipeGroup.getPipeOperation())) {
                    processRenamePipeOperation(pipeGroup, columnNames);

                } else if (PipeOperation.TABLE.equals(pipeGroup.getPipeOperation())) {
                    processTablePipeOperation(pipeGroup, columnNames, extractValues, resultRequests);

                } else {
                    break;
                }
            } else {
                break;
            }
        }

        if (i < tokens.size()) {
            return tokens.subList(i, tokens.size());
        }
        return Collections.emptyList();
    }

    private void processRenamePipeOperation(final PipeGroup pipeGroup,
                                            final Map<String, String> columnNames) {
        final List<AbstractToken> children = pipeGroup.getChildren();
        String field = null;
        boolean afterAs = false;
        for (final AbstractToken t : children) {
            if (TokenType.isString(t)) {
                if (afterAs) {
                    if (field == null) {
                        throw new TokenException(t, "Syntax exception");
                    }
                    columnNames.put(t.getText(), field);
                    field = null;
                    afterAs = false;
                } else if (field != null) {
                    throw new TokenException(t, "Syntax exception");
                } else {
                    field = t.getText();
                }
            } else if (TokenType.AS.equals(t.getTokenType())) {
                afterAs = true;
            } else if (TokenType.COMMA.equals(t.getTokenType())) {
                field = null;
            } else {
                throw new TokenException(t, "Unexpected token");
            }
        }
    }

    private void processTablePipeOperation(final PipeGroup pipeGroup,
                                           final Map<String, String> columnNames,
                                           final boolean extractValues,
                                           final List<ResultRequest> resultRequests) {
        TableSettings.Builder builder = TableSettings.builder();

        final List<AbstractToken> children = pipeGroup.getChildren();
        for (final AbstractToken t : children) {
            if (!TokenType.COMMA.equals(t.getTokenType())) {
                final String columnName = t.getText();
                final String fieldName = columnNames.getOrDefault(columnName, columnName);
                final Field field = Field.builder()
                        .id(fieldName)
                        .name(columnName)
                        .expression(ParamSubstituteUtil.makeParam(fieldName))
                        .build();
                builder.addFields(field);
            }
        }

        //        final DocRef resultPipeline = commonIndexingTestHelper.getSearchResultPipeline();
        final TableSettings tableSettings = builder
                .extractValues(extractValues)
//                .extractionPipeline(resultPipeline)
                .build();

        final ResultRequest tableResultRequest = new ResultRequest("1234",
                Collections.singletonList(tableSettings),
                null,
                null,
                ResultRequest.ResultStyle.TABLE,
                Fetch.ALL);
        resultRequests.add(tableResultRequest);
    }
}
