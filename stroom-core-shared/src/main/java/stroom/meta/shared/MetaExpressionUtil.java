package stroom.meta.shared;

import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.ExpressionUtil;

import java.util.Set;

public final class MetaExpressionUtil {
    private MetaExpressionUtil() {
        // Utility class.
    }

    public static ExpressionOperator createStatusExpression(final Status status) {
        return ExpressionUtil.equals(MetaFields.STATUS, status.getDisplayValue());
    }

    public static ExpressionOperator createDataIdSetExpression(final Set<Long> idSet) {
        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(Op.OR);
        for (final Long id : idSet) {
            builder.addTerm(MetaFields.ID, Condition.EQUALS, id);
        }
        return builder.build();
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
