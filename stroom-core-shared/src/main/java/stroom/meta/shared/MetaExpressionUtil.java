package stroom.meta.shared;

import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.ExpressionUtil;

import java.util.Set;
import java.util.stream.Collectors;

public final class MetaExpressionUtil {
    private MetaExpressionUtil() {
        // Utility class.
    }

    public static ExpressionOperator createStatusExpression(final Status status) {
        return ExpressionUtil.equals(MetaFields.STATUS, status.getDisplayValue());
    }

    public static ExpressionOperator createDataIdSetExpression(final Set<Long> idSet) {
        final String delimitedList = idSet.stream().map(String::valueOf).collect(Collectors.joining(","));
        return new ExpressionOperator.Builder(Op.OR)
                .addTerm(MetaFields.ID.getName(), Condition.IN, delimitedList)
                .build();
    }

    public static ExpressionOperator createDataIdExpression(final long id) {
        return ExpressionUtil.equals(MetaFields.ID, id);
    }

    public static ExpressionOperator createDataIdExpression(final long id, final Status status) {
        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaFields.ID, Condition.EQUALS, id)
                .addTerm(MetaFields.STATUS, Condition.EQUALS, status.getDisplayValue())
                .build();
    }

    public static ExpressionOperator createParentIdExpression(final long parentId, final Status status) {
        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaFields.PARENT_ID, Condition.EQUALS, parentId)
                .addTerm(MetaFields.STATUS, Condition.EQUALS, status.getDisplayValue())
                .build();
    }

    public static ExpressionOperator createTypeExpression(final String typeName, final Status status) {
        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaFields.TYPE_NAME, Condition.EQUALS, typeName)
                .addTerm(MetaFields.STATUS, Condition.EQUALS, status.getDisplayValue())
                .build();
    }

    public static ExpressionOperator createFolderExpression(final DocRef folder) {
        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaFields.FEED, Condition.IN_FOLDER, folder)
                .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
    }

//    public static ExpressionOperator createFoldersExpression(final DocRef... folders) {
//        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(Op.AND);
//
//        if (folders != null) {
//            if (folders.length == 1) {
//                builder.addTerm(MetaFields.FEED, Condition.IN_FOLDER, folders[0]);
//            } else {
//                final ExpressionOperator.Builder or = new ExpressionOperator.Builder(Op.OR);
//                for (final DocRef folder : folders) {
//                    or.addTerm(MetaFields.FEED, Condition.IN_FOLDER, folder);
//                }
//                builder.addOperator(or.build());
//            }
//        }
//
//        builder.addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue());
//        return builder.build();
//    }

    public static ExpressionOperator createFeedExpression(final String feedName) {
        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaFields.FEED_NAME, Condition.EQUALS, feedName)
                .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
    }

    public static ExpressionOperator createFeedsExpression(final String... feedNames) {
        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaFields.FEED_NAME, Condition.IN, String.join(",", feedNames))
                .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
    }

    public static ExpressionOperator createPipelineExpression(final DocRef pipelineRef) {
        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaFields.PIPELINE, Condition.IS_DOC_REF, pipelineRef)
                .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
    }
}
