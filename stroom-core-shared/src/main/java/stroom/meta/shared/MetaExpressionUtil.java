package stroom.meta.shared;

import stroom.datasource.api.v2.AbstractField;
import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;

public final class MetaExpressionUtil {
    private MetaExpressionUtil() {
        // Utility class.
    }

    public static ExpressionOperator createSimpleExpression() {
        return createSimpleExpression(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue());
    }

    public static ExpressionOperator createSimpleExpression(final AbstractField field, final Condition condition, final String value) {
        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(field.getName(), condition, value)
                .build();
    }

//    public static ExpressionOperator createSimpleExpression(final TextField field, final Condition condition, final String value) {
//        return new ExpressionOperator.Builder(Op.AND)
//                .addTerm(field, condition, value)
//                .build();
//    }
//
//    public static ExpressionOperator createSimpleExpression(final DateField field, final Condition condition, final String value) {
//        return new ExpressionOperator.Builder(Op.AND)
//                .addTerm(field, condition, value)
//                .build();
//    }

    public static ExpressionOperator createStatusExpression(final Status status) {
        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaFields.STATUS, Condition.EQUALS, status.getDisplayValue())
                .build();
    }

    public static ExpressionOperator createDataIdExpression(final long id) {
        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaFields.ID, Condition.EQUALS, id)
                .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
        return expression;
    }

    public static ExpressionOperator createParentIdExpression(final long parentId) {
        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaFields.PARENT_ID, Condition.EQUALS, parentId)
                .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
        return expression;
    }

    public static ExpressionOperator createTypeExpression(final String typeName) {
        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaFields.TYPE_NAME, Condition.EQUALS, typeName)
                .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
    }

    public static ExpressionOperator createFolderExpression(final DocRef folder) {
        return createFoldersExpression(folder);
    }

    public static ExpressionOperator createFoldersExpression(final DocRef... folders) {
        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(Op.AND);

        if (folders != null) {
            if (folders.length == 1) {
                builder.addTerm(MetaFields.FEED, Condition.IN_FOLDER, folders[0]);
            } else {
                final ExpressionOperator.Builder or = new ExpressionOperator.Builder(Op.OR);
                for (final DocRef folder : folders) {
                    or.addTerm(MetaFields.FEED, Condition.IN_FOLDER, folder);
                }
                builder.addOperator(or.build());
            }
        }

        builder.addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue());
        return builder.build();
    }

    public static ExpressionOperator createFeedExpression(final String feedName) {
        return createFeedsExpression(feedName);
    }

    public static ExpressionOperator createFeedsExpression(final String... feedNames) {
        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(Op.AND);

        if (feedNames != null) {
            if (feedNames.length == 1) {
                builder.addTerm(MetaFields.FEED_NAME, Condition.EQUALS, feedNames[0]);
            } else {
                final ExpressionOperator.Builder or = new ExpressionOperator.Builder(Op.OR);
                for (final String feedName : feedNames) {
                    or.addTerm(MetaFields.FEED_NAME, Condition.EQUALS, feedName);
                }
                builder.addOperator(or.build());
            }
        }

        builder.addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue());
        return builder.build();
    }

    public static ExpressionOperator createPipelineExpression(final DocRef pipelineRef) {
        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaFields.PIPELINE, Condition.IS_DOC_REF, pipelineRef)
                .addTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
    }
}
