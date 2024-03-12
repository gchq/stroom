package stroom.meta.shared;

import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.ExpressionUtil;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class MetaExpressionUtil {

    private MetaExpressionUtil() {
        // Utility class.
    }

    public static ExpressionOperator createStatusExpression(final Status status) {
        return ExpressionUtil.equalsDate(MetaFields.STATUS, status.getDisplayValue());
    }

    public static ExpressionOperator createDataIdSetExpression(final Set<Long> idSet) {
        Objects.requireNonNull(idSet);
        if (idSet.size() == 1) {
            // No point using an IN list for one ID
            return createDataIdExpression(idSet.iterator().next());
        } else {
            final String delimitedList = idSet.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            return ExpressionOperator.builder().op(Op.AND)
                    .addTerm(MetaFields.ID.getName(), Condition.IN, delimitedList)
                    .build();
        }
    }

    public static ExpressionOperator createDataIdExpression(final long id) {
        return ExpressionUtil.equalsId(MetaFields.ID, id);
    }

    public static ExpressionOperator createDataIdExpression(final long id, final Status status) {
        return ExpressionOperator.builder()
                .addIdTerm(MetaFields.ID, Condition.EQUALS, id)
                .addDateTerm(MetaFields.STATUS, Condition.EQUALS, status.getDisplayValue())
                .build();
    }

    public static ExpressionOperator createParentIdExpression(final long parentId, final Status status) {
        return ExpressionOperator.builder()
                .addIdTerm(MetaFields.PARENT_ID, Condition.EQUALS, parentId)
                .addDateTerm(MetaFields.STATUS, Condition.EQUALS, status.getDisplayValue())
                .build();
    }

    public static ExpressionOperator createTypeExpression(final String typeName, final Status status) {
        return ExpressionOperator.builder()
                .addDateTerm(MetaFields.TYPE, Condition.EQUALS, typeName)
                .addDateTerm(MetaFields.STATUS, Condition.EQUALS, status.getDisplayValue())
                .build();
    }

    public static ExpressionOperator createFolderExpression(final DocRef folder) {
        return ExpressionOperator.builder()
                .addOperator(
                        ExpressionOperator
                                .builder()
                                .op(Op.OR)
                                .addDocRefTerm(MetaFields.FEED, Condition.IN_FOLDER, folder)
                                .addDocRefTerm(MetaFields.PIPELINE, Condition.IN_FOLDER, folder)
                                .build()
                )
                .addDateTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
    }

    public static ExpressionOperator createFeedExpression(final DocRef feedRef) {
        return ExpressionOperator.builder()
                .addDocRefTerm(MetaFields.FEED, Condition.IS_DOC_REF, feedRef)
                .addDateTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
    }

    public static ExpressionOperator createFeedExpression(final String feedName) {
        return ExpressionOperator.builder()
                .addDateTerm(MetaFields.FEED, Condition.EQUALS, feedName)
                .addDateTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
    }

    public static ExpressionOperator createFeedsExpression(final String... feedNames) {
        return ExpressionOperator.builder()
                .addDateTerm(MetaFields.FEED, Condition.IN, String.join(",", feedNames))
                .addDateTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
    }

    public static ExpressionOperator createPipelineExpression(final DocRef pipelineRef) {
        return ExpressionOperator.builder()
                .addDocRefTerm(MetaFields.PIPELINE, Condition.IS_DOC_REF, pipelineRef)
                .addDateTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
    }
}
