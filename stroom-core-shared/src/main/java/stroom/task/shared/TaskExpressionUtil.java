package stroom.task.shared;

import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.ProcessorTaskDataSource;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.ExpressionUtil;

public final class TaskExpressionUtil {
    private TaskExpressionUtil() {
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
                or.addTerm(ProcessorTaskDataSource.PIPELINE_UUID, Condition.IN_FOLDER, folder);
//                or.addDocRefTerm(ProcessTaskDataSource.FEED_UUID, Condition.IN_FOLDER, folder);
            }
            builder.addOperator(or.build());
        }

        return builder.build();
    }

    public static ExpressionOperator createFeedExpression(final String feedName) {
        return ExpressionUtil.equals(ProcessorTaskDataSource.FEED_NAME, feedName);
    }

    public static ExpressionOperator createPipelineExpression(final PipelineDoc pipelineEntity) {
        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(ProcessorTaskDataSource.PIPELINE_UUID, Condition.IS_DOC_REF, DocRefUtil.create(pipelineEntity))
                .build();
    }
}
