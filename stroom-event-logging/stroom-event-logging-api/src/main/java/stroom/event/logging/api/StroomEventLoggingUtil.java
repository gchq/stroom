package stroom.event.logging.api;

import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.util.shared.PageResponse;
import stroom.util.shared.Selection;

import event.logging.AdvancedQuery;
import event.logging.AdvancedQueryItem;
import event.logging.And;
import event.logging.CopyMoveOutcome;
import event.logging.MultiObject;
import event.logging.Not;
import event.logging.Or;
import event.logging.OtherObject;
import event.logging.Query;
import event.logging.ResultPage;
import event.logging.SimpleQuery;
import event.logging.Term;
import event.logging.TermCondition;

import java.math.BigInteger;
import java.util.stream.Collectors;

public class StroomEventLoggingUtil {
    private StroomEventLoggingUtil() {
    }

    public static OtherObject createOtherObject(final DocRef docRef) {
        return createOtherObject(docRef.getType(), docRef.getUuid(), docRef.getName());
    }

    public static MultiObject createOtherObjectMulti(final DocRef docRef) {
        if (docRef == null) {
            return null;
        } else {
            return MultiObject.builder()
                    .addObject(createOtherObject(docRef))
                    .build();
        }
    }

    public static OtherObject createOtherObject(final String type,
                                                final String uuid,
                                                final String name) {
        return OtherObject.builder()
                .withType(type)
                .withId(uuid)
                .withName(name)
                .build();
    }

    public static CopyMoveOutcome createCopyMoveOutcome(final Throwable throwable) {
        final CopyMoveOutcome outcome;
        if (throwable != null) {
            outcome = CopyMoveOutcome.builder()
                    .withSuccess(Boolean.FALSE)
                    .withDescription(throwable.getMessage() != null
                            ? throwable.getMessage()
                            : throwable.getClass().getName())
                    .build();
        } else {
            outcome = null;
        }
        return outcome;
    }

    public static ResultPage createResultPage(final stroom.util.shared.ResultPage<?> resultPage) {
        final ResultPage result;
        if (resultPage == null) {
            result = null;
        } else {
            final PageResponse pageResponse = resultPage.getPageResponse();
            if (resultPage.getPageResponse() != null) {
                result = ResultPage.builder()
                        .withFrom(BigInteger.valueOf(pageResponse.getOffset()))
                        .withPerPage(BigInteger.valueOf(pageResponse.getLength()))
                        .build();
            } else {
                result = null;
            }
        }
        return result;
    }

    public static void appendSelection(final Query.Builder<Void> queryBuilder, final Selection<?> selection) {
        if (selection != null) {
            if (selection.isMatchAll()) {
                queryBuilder
                        .withAdvanced(AdvancedQuery.builder()
                                .addAnd(And.builder()
                                        .build())
                                .build());
            } else if (selection.isMatchNothing()) {
                queryBuilder
                        .withAdvanced(AdvancedQuery.builder()
                                .addNot(Not.builder()
                                        .build())
                                .build());
            } else {
                queryBuilder.withSimple(SimpleQuery.builder()
                        .withInclude(selection.toString())
                        .build());
            }
        }
    }

    public static void appendExpression(final Query.Builder<Void> queryBuilder, final ExpressionItem expressionItem) {
        queryBuilder.withAdvanced(AdvancedQuery.builder()
                .withQueryItems(convertItem(expressionItem))
                .build());
    }

    private static AdvancedQueryItem convertItem(final ExpressionItem expressionItem) {
        if (expressionItem instanceof ExpressionTerm && expressionItem.enabled()) {
            final ExpressionTerm expressionTerm = (ExpressionTerm) expressionItem;

            return Term.builder()
                    .withName(expressionTerm.getField())
                    .withCondition(convertCondition(expressionTerm.getCondition()))
                    .withValue(expressionTerm.getValue())
                    .build();
        } else if (expressionItem instanceof ExpressionOperator && expressionItem.enabled()) {
            final ExpressionOperator expressionOperator = (ExpressionOperator) expressionItem;
            final AdvancedQueryItem operator;
            if (expressionOperator.op().equals(Op.AND)) {
                operator = And.builder()
                        .withQueryItems(expressionOperator.getChildren()
                                .stream()
                                .map(StroomEventLoggingUtil::convertItem)
                                .collect(Collectors.toList()))
                        .build();
            } else if (expressionOperator.op().equals(Op.OR)) {
                operator = Or.builder()
                        .withQueryItems(expressionOperator.getChildren()
                                .stream()
                                .map(StroomEventLoggingUtil::convertItem)
                                .collect(Collectors.toList()))
                        .build();
            } else if (expressionOperator.op().equals(Op.NOT)) {
                operator = Not.builder()
                        .withQueryItems(expressionOperator.getChildren()
                                .stream()
                                .map(StroomEventLoggingUtil::convertItem)
                                .collect(Collectors.toList()))
                        .build();
            } else {
                throw new RuntimeException("Unknown op " + expressionOperator.op());
            }
            return operator;
        } else {
            throw new RuntimeException("Unknown type " + expressionItem.getClass().getName());
        }
    }

    private static TermCondition convertCondition(final Condition condition) {
        final TermCondition termCondition;
        switch (condition) {
            case CONTAINS:
                termCondition = TermCondition.CONTAINS;
                break;
            case EQUALS:
                termCondition = TermCondition.EQUALS;
                break;
            case GREATER_THAN:
                termCondition = TermCondition.GREATER_THAN;
                break;
            case GREATER_THAN_OR_EQUAL_TO:
                termCondition = TermCondition.GREATER_THAN_EQUAL_TO;
                break;
            case LESS_THAN:
                termCondition = TermCondition.LESS_THAN;
                break;
            case LESS_THAN_OR_EQUAL_TO:
                termCondition = TermCondition.LESS_THAN_EQUAL_TO;
                break;
            default:
                throw new RuntimeException("Can't convert condition " + condition);
        }
        return termCondition;
    }
}
