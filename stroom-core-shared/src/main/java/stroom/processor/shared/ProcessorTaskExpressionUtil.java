package stroom.processor.shared;

import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.ExpressionUtil;

public final class ProcessorTaskExpressionUtil {
    private ProcessorTaskExpressionUtil() {
        // Utility class.
    }

    public static ExpressionOperator createWithStream(final Meta meta) {
        return ExpressionUtil.equals(ProcessorTaskFields.META_ID, meta.getId());
    }

    public static ExpressionOperator createFolderExpression(final DocRef folder) {
        return createFoldersExpression(folder);
    }

    public static ExpressionOperator createFoldersExpression(final DocRef... folders) {
        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(Op.OR);

        if (folders != null) {
            for (final DocRef folder : folders) {
                builder.addTerm(ProcessorTaskFields.PIPELINE, Condition.IN_FOLDER, folder);
                builder.addTerm(ProcessorTaskFields.FEED, Condition.IN_FOLDER, folder);
            }
        }

        return builder.build();
    }

    public static ExpressionOperator createPipelineExpression(final DocRef pipeline) {
        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(ProcessorTaskFields.PIPELINE, Condition.IS_DOC_REF, pipeline)
                .build();
    }

    public static ExpressionOperator createFeedExpression(final DocRef feed) {
        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(ProcessorTaskFields.FEED, Condition.IS_DOC_REF, feed)
                .build();
    }
}
