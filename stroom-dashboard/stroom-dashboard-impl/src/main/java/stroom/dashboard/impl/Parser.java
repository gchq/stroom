package stroom.dashboard.impl;

import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerNode;
import stroom.index.shared.IndexDoc;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.Query;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.SearchRequest;
import stroom.query.language.Token;
import stroom.query.language.TokenException;
import stroom.query.language.TokenGroup;
import stroom.query.language.TokenGroup.Builder;
import stroom.query.language.TokenType;
import stroom.query.language.Tokeniser;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.statistics.impl.hbase.shared.StroomStatsStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;

import java.util.List;
import java.util.Set;
import javax.inject.Inject;

public class Parser {

    public SearchRequest parse(final String string, final SearchRequest in) {
        // Get a list of tokens.
        TokenGroup tokenGroup = new Tokeniser().extractTokens(string);

        // Remove whitespace.
        tokenGroup = removeWhitespace(tokenGroup);

        // Assume we have a root bracket group.
        final List<Token> tokens = tokenGroup.getChildren();

        // Nested within this we should have one or more pipe groups.
        if (tokens.size() == 0) {
            throw new TokenException(tokenGroup, "No tokens");
        }

        // Resolve data source.
        final Token firstPipeGroup = tokens.get(0);
        if (!TokenType.PIPE_GROUP.equals(firstPipeGroup.getTokenType())) {
            throw new TokenException(firstPipeGroup, "Expected pipe group");
        }
        final String dataSourceName = getDataSourceString((TokenGroup) tokens.get(0));
        final DocRef dataSource = new DocRef(null, null, dataSourceName);
        //resolveDataSource((TokenGroup) tokens.get(0));

        // Get remaining tokens.
        final List<Token> remaining = tokens.subList(1, tokens.size());

        // Create expression.
        ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.AND);
        createExpression(remaining, builder);
        final ExpressionOperator expression = builder.build();

        // Try to make a query.
        Query query = new Query(
                dataSource,
                expression,
                in.getQuery().getParams(),
                in.getQuery().getTimeRange());

        // Create result requests.
//        TableSettings tableSettings = new TableSettings()

        final List<ResultRequest> resultRequests = in.getResultRequests();
        return new SearchRequest(in.getKey(),
                query,
                resultRequests,
                in.getDateTimeSettings(),
                in.incremental(),
                in.getTimeout());
    }

    private void error(final List<Token> tokens, final int index, final String message) {
        if (tokens.size() < index) {
            throw new TokenException(tokens.get(index), message);
        }
        throw new TokenException(null, message);
    }

    private void createExpression(final List<Token> tokens,
                                  final ExpressionOperator.Builder parentBuilder) {
        for (final Token token : tokens) {
            // This should be a pipe group.
            if (!TokenType.PIPE_GROUP.equals(token.getTokenType())) {
                throw new TokenException(token, "Expected pipe group");
            }

            processPipeGroup(((TokenGroup) token).getChildren(), parentBuilder);
        }
    }

    private void processPipeGroup(
            final List<Token> tokens,
            final ExpressionOperator.Builder parentBuilder) {
        for (final Token token : tokens) {
            final TokenType tokenType = token.getTokenType();

            // The first token is expected to be a command.
            if (TokenType.COMMANDS.contains(tokenType)) {
                processCommand(tokenType, tokens.subList(1, tokens.size()), parentBuilder);
                break;
            } else {
                throw new TokenException(token, "Expected command");
            }
        }
    }

    private TokenGroup removeWhitespace(final TokenGroup tokenGroup) {
        final TokenGroup.Builder builder = new Builder()
                .tokenType(tokenGroup.getTokenType())
                .chars(tokenGroup.getChars())
                .start(tokenGroup.getStart())
                .end(tokenGroup.getEnd());


        // Remove whitespace.
        for (final Token token : tokenGroup.getChildren()) {
            if (token instanceof TokenGroup) {
                builder.add(removeWhitespace(((TokenGroup) token)));
            } else if (!TokenType.WHITESPACE.equals(token.getTokenType())) {
                builder.add(token);
            }
        }
        return builder.build();
    }

//    private List<Token> removeWhitespace(final List<Token> tokens) {
//        // Remove whitespace.
//        final List<Token> cleaned = new ArrayList<>(tokens.size());
//        for (final Token token : tokens) {
//            if (!TokenType.WHITESPACE.equals(token.getTokenType())) {
//                cleaned.add(token);
//            }
//        }
//        return cleaned;
//    }

    private void addTermGroup(
            final TokenGroup tokenGroup,
            final ExpressionOperator.Builder parentBuilder) {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.AND);
        final List<Token> remaining = addTerm(tokenGroup.getChildren(), builder);
        processPipeGroup(remaining, builder);
        parentBuilder.addOperator(builder.build());
    }

    private List<Token> addTerm(
            final List<Token> tokens,
            final ExpressionOperator.Builder parentBuilder) {
        List<Token> remaining = tokens;
        if (tokens.size() > 0 && tokens.get(0) instanceof TokenGroup) {
            addTermGroup(((TokenGroup) tokens.get(0)), parentBuilder);
            remaining = tokens.subList(1, tokens.size());
        }

        if (remaining.size() > 0) {
            final Token field = remaining.get(0);

            if (remaining.size() == 1) {
                throw new TokenException(field, field.getText() + " has no condition");
            }
            if (remaining.size() == 2) {
                throw new TokenException(field, field.getText() + " has no value");
            }

            final Token condition = remaining.get(1);
            final Token value = remaining.get(2);

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

            remaining = remaining.subList(3, remaining.size());
        }
        return remaining;
    }

    private void processCommand(
            final TokenType command,
            final List<Token> tokens,
            final ExpressionOperator.Builder parentBuilder) {
        List<Token> remaining;

        ExpressionOperator.Builder builder = parentBuilder;
        if (command.equals(TokenType.NOT)) {
            builder = ExpressionOperator.builder().op(Op.NOT);
        } else if (command.equals(TokenType.OR)) {
            parentBuilder.op(Op.OR);
        }

        // Expect next token to be group or a field, condition, value combination.
        if (tokens.size() > 0 && (tokens.get(0) instanceof TokenGroup)) {
            addTermGroup((TokenGroup) tokens.get(0), parentBuilder);
            remaining = tokens.subList(1, tokens.size());
        } else {
            remaining = addTerm(tokens, parentBuilder);
        }

        if (command.equals(TokenType.NOT)) {
            parentBuilder.addOperator(builder.build());
        }

        processPipeGroup(remaining, parentBuilder);
    }
//
//    private static final Set<TokenType> ALL_LOGIC_TYPES = Set.of(
//            TokenType.WHERE,
//            TokenType.AND,
//            TokenType.OR,
//            TokenType.NOT);
//    private static final Set<TokenType> WHERE_ONLY = Set.of(
//            TokenType.WHERE);
//    private static final Set<TokenType> INNER_LOGIC_TYPES = Set.of(
//            TokenType.AND,
//            TokenType.OR,
//            TokenType.NOT);
//    private static final Set<TokenType> PIPE_ONLY = Set.of(
//            TokenType.PIPE);
//    private static final Set<TokenType> WHITESPACE_ONLY = Set.of(
//            TokenType.WHITESPACE);
//
//    private int addTerm(final ExpressionOperator.Builder parentBuilder,
//                        final Token parentToken,
//                        final List<Token> tokens,
//                        final int start,
//                        final int end) {
//        final Set<TokenType> valueTypes = Set.of(
//                TokenType.UNKNOWN,
//                TokenType.SINGLE_QUOTED_STRING,
//                TokenType.DOUBLE_QUOTED_STRING,
//                TokenType.STRING);
//        final Set<TokenType> conditionTypes = Set.of(
//                TokenType.EQUALS,
//                TokenType.NOT_EQUALS,
//                TokenType.GREATER_THAN,
//                TokenType.GREATER_THAN_OR_EQUAL_TO,
//                TokenType.LESS_THAN,
//                TokenType.LESS_THAN_OR_EQUAL_TO,
//                TokenType.IS_NULL,
//                TokenType.IS_NOT_NULL);
//
//        final int fieldIndex = next(tokens, start, end, valueTypes, Set.of(TokenType.WHITESPACE));
//        if (fieldIndex == -1) {
//            throw new TokenException(parentToken, parentToken.toString() + " has no field: " + parentToken);
//        }
//        final int conditionIndex = next(tokens, fieldIndex + 1, end, conditionTypes, Set.of(TokenType.WHITESPACE));
//        if (conditionIndex == -1) {
//            throw new TokenException(parentToken, parentToken.toString() + " has no condition: " + parentToken);
//        }
//        final int valueIndex = next(tokens, conditionIndex + 1, end, valueTypes, Set.of(TokenType.WHITESPACE));
//        if (valueIndex == -1) {
//            throw new TokenException(parentToken, parentToken.toString() + " has no value: " + parentToken);
//        }
//
//        final Token field = tokens.get(fieldIndex);
//        final Token condition = tokens.get(conditionIndex);
//        final Token value = tokens.get(valueIndex);
//
//        // If we have a where clause then we expect the next token to contain an expression.
//        Condition cond;
//        boolean not = false;
//        switch (condition.getTokenType()) {
//            case EQUALS:
//                cond = Condition.EQUALS;
//                break;
//            case NOT_EQUALS:
//                cond = Condition.EQUALS;
//                not = true;
//                break;
//            case GREATER_THAN:
//                cond = Condition.GREATER_THAN;
//                break;
//            case GREATER_THAN_OR_EQUAL_TO:
//                cond = Condition.GREATER_THAN_OR_EQUAL_TO;
//                break;
//            case LESS_THAN:
//                cond = Condition.LESS_THAN;
//                break;
//            case LESS_THAN_OR_EQUAL_TO:
//                cond = Condition.LESS_THAN_OR_EQUAL_TO;
//                break;
//            case IS_NULL:
//                cond = Condition.IS_NULL;
//                break;
//            case IS_NOT_NULL:
//                cond = Condition.IS_NOT_NULL;
//                break;
//            default:
//                throw new TokenException(condition, "Unknown condition: " + condition);
//        }
//
//        final ExpressionTerm expressionTerm = ExpressionTerm
//                .builder()
//                .field(field.getText())
//                .condition(cond)
//                .value(value.getText())
//                .build();
//
//        if (not) {
//            parentBuilder
//                    .addOperator(ExpressionOperator
//                            .builder()
//                            .op(Op.NOT)
//                            .addTerm(expressionTerm)
//                            .build());
//        } else {
//            parentBuilder.addTerm(expressionTerm);
//        }
//
//        return valueIndex + 1;
//    }
//
//    private int next(final List<Token> tokens,
//                     final int start,
//                     final int end,
//                     final Set<TokenType> expectedType,
//                     final Set<TokenType> skip) {
//        int result = -1;
//        for (int i = start; i <= end; i++) {
//            final Token token = tokens.get(i);
//            if (expectedType.contains(token.getTokenType())) {
//                result = i;
//            } else if (!skip.contains(token.getTokenType())) {
//                throw new TokenException(token, "Unexpected token: " + token);
//            }
//        }
//        return result;
//    }
//
//    private DocRef resolveDataSource(final TokenGroup tokenGroup) {
//        final String dataSource = getDataSourceString(tokenGroup);
//        if (dataSource == null) {
//            throw new RuntimeException("No datasource specified");
//        }
//
//        final List<ExplorerNode> explorerNodes = explorerNodeService.getDescendants(null);
//        final List<ExplorerNode> filteredNodes =
//                explorerNodes
//                        .stream()
//                        .filter(node -> (
//                                node.getType().equals(IndexDoc.DOCUMENT_TYPE) ||
//                                        node.getType().equals(StatisticStoreDoc.DOCUMENT_TYPE) ||
//                                        node.getType().equals(StroomStatsStoreDoc.DOCUMENT_TYPE) ||
//                                        node.getType().equals(ElasticIndexDoc.DOCUMENT_TYPE) ||
//                                        node.getType().equals(SolrIndexDoc.DOCUMENT_TYPE)) &&
//                                node.getName().equals(dataSource))
//                        .toList();
//
//        // TODO : Also consider 'searchables' such as meta.
//
//        if (filteredNodes.size() == 0) {
//            throw new RuntimeException("No datasource found with name '" + dataSource + "'");
//        }
//        return filteredNodes.stream().findFirst().get().getDocRef();
//    }
//
    private String getDataSourceString(final TokenGroup tokenGroup) {
        String dataSource = null;
        for (final Token token : tokenGroup.getChildren()) {
            if (token instanceof TokenGroup) {
                return getDataSourceString((TokenGroup) token);
            } else {
                dataSource = token.getText();
                dataSource = dataSource.trim();
                break;

            }
        }
        return dataSource;
    }
}
