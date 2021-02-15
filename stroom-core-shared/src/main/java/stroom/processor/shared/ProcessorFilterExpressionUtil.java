package stroom.processor.shared;

import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;

public final class ProcessorFilterExpressionUtil {

    private ProcessorFilterExpressionUtil() {
        // Utility class.
    }

    public static ExpressionOperator createBasicExpression() {
        return ExpressionOperator.builder()
                .addTerm(ProcessorFields.DELETED, Condition.EQUALS, false)
                .addTerm(ProcessorFilterFields.DELETED, Condition.EQUALS, false)
                .build();
    }

    public static ExpressionOperator createFolderExpression(final DocRef folder) {
        return createFoldersExpression(folder);
    }

    public static ExpressionOperator createFoldersExpression(final DocRef... folders) {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder();

        if (folders != null) {
            if (folders.length == 1) {
                builder.addTerm(ProcessorFields.PIPELINE, Condition.IN_FOLDER, folders[0]);
            } else if (folders.length > 0) {
                final ExpressionOperator.Builder or = ExpressionOperator.builder().op(Op.OR);
                for (final DocRef folder : folders) {
                    or.addTerm(ProcessorFields.PIPELINE, Condition.IN_FOLDER, folder);
                }
                builder.addOperator(or.build());
            }
        }

        return builder.addTerm(ProcessorFields.DELETED, Condition.EQUALS, false)
                .addTerm(ProcessorFilterFields.DELETED, Condition.EQUALS, false)
                .build();
    }

    public static ExpressionOperator createPipelineExpression(final DocRef pipelineRef) {
        return ExpressionOperator.builder()
                .addTerm(ProcessorFields.PIPELINE, Condition.IS_DOC_REF, pipelineRef)
                .addTerm(ProcessorFields.DELETED, Condition.EQUALS, false)
                .addTerm(ProcessorFilterFields.DELETED, Condition.EQUALS, false)
                .build();
    }
}
