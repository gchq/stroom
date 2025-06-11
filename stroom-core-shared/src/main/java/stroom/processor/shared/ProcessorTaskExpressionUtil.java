package stroom.processor.shared;

import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.ExpressionUtil;

public final class ProcessorTaskExpressionUtil {

    private ProcessorTaskExpressionUtil() {
        // Utility class.
    }

    public static ExpressionOperator createWithStream(final Meta meta) {
        return ExpressionUtil.equalsId(ProcessorTaskFields.META_ID, meta.getId());
    }

    public static ExpressionOperator createFolderExpression(final DocRef folder) {
        return createFoldersExpression(folder);
    }

    public static ExpressionOperator createFoldersExpression(final DocRef... folders) {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.OR);

        if (folders != null) {
            for (final DocRef folder : folders) {
                builder.addDocRefTerm(ProcessorTaskFields.PIPELINE, Condition.IN_FOLDER, folder);
                builder.addDocRefTerm(ProcessorTaskFields.FEED, Condition.IN_FOLDER, folder);
            }
        }

        return builder.build();
    }

    public static ExpressionOperator createPipelineExpression(final DocRef pipeline) {
        return ExpressionOperator.builder()
                .addDocRefTerm(ProcessorTaskFields.PIPELINE, Condition.IS_DOC_REF, pipeline)
                .build();
    }

    public static ExpressionOperator createFeedExpression(final DocRef feed) {
        return ExpressionOperator.builder()
                .addDocRefTerm(ProcessorTaskFields.FEED, Condition.IS_DOC_REF, feed)
                .build();
    }
}
