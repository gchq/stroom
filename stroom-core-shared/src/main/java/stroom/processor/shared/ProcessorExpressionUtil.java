package stroom.processor.shared;

import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;

public final class ProcessorExpressionUtil {
    private ProcessorExpressionUtil() {
        // Utility class.
    }

    public static ExpressionOperator createFolderExpression(final DocRef folder) {
        return createFoldersExpression(folder);
    }

    public static ExpressionOperator createFoldersExpression(final DocRef... folders) {
        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(Op.AND);

        if (folders != null) {
            final ExpressionOperator.Builder or = new ExpressionOperator.Builder(Op.OR);
            for (final DocRef folder : folders) {
                or.addTerm(ProcessorDataSource.PIPELINE, Condition.IN_FOLDER, folder);
//                or.addTerm(ProcessTaskDataSource.FEED_UUID, Condition.IN_FOLDER, folder);
            }
            builder.addOperator(or.build());
        }

        return builder.build();
    }

//    public static ExpressionOperator createFeedExpression(final DocRef feedRef) {
//        return new ExpressionOperator.Builder(Op.AND)
//                .addTerm(ProcessorTaskDataSource.FEED_UUID, Condition.IS_DOC_REF, feedRef)
//                .build();
//    }

    public static ExpressionOperator createPipelineExpression(final DocRef pipelineRef) {
        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(ProcessorDataSource.PIPELINE, Condition.IS_DOC_REF, pipelineRef)
                .build();
    }
}
