package stroom.processor.shared;

import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;

public final class ProcessorExpressionUtil {
    private ProcessorExpressionUtil() {
        // Utility class.
    }

//    public static ExpressionOperator createBasicExpression() {
//        return new ExpressionOperator.Builder(Op.AND)
//                .addTerm(ProcessorFields.DELETED, Condition.EQUALS, false)
//                .build();
//    }
//
//    public static ExpressionOperator createFolderExpression(final DocRef folder) {
//        return createFoldersExpression(folder);
//    }
//
//    public static ExpressionOperator createFoldersExpression(final DocRef... folders) {
//        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(Op.AND);
//
//        if (folders != null) {
//            if (folders.length == 1) {
//                builder.addTerm(ProcessorFields.PIPELINE, Condition.IN_FOLDER, folders[0]);
//            } else if (folders.length > 0) {
//                final ExpressionOperator.Builder or = new ExpressionOperator.Builder(Op.OR);
//                for (final DocRef folder : folders) {
//                    or.addTerm(ProcessorFields.PIPELINE, Condition.IN_FOLDER, folder);
//                }
//                builder.addOperator(or.build());
//            }
//        }
//
//        return builder.addTerm(ProcessorFields.DELETED, Condition.EQUALS, false)
//                .build();
//    }

    public static ExpressionOperator createPipelineExpression(final DocRef pipelineRef) {
        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(ProcessorFields.PIPELINE, Condition.IS_DOC_REF, pipelineRef)
                .addTerm(ProcessorFields.DELETED, Condition.EQUALS, false)
                .build();
    }

    public interface ExpressionOperatorVisitor {
        void accept(final ExpressionOperator parent,
                    final int childOffset,
                    final ExpressionOperator operator);
    }

    public interface ExpressionTermVisitor {
        void accept(final ExpressionOperator parent,
                    final int childOffset,
                    final ExpressionTerm operator);
    }

    public static void walkExpressionTree(final ExpressionItem expressionItem,
                                          final ProcessorExpressionUtil.ExpressionOperatorVisitor operatorVisitor,
                                          final ProcessorExpressionUtil.ExpressionTermVisitor termVisitor) {
        walkExpressionTree(null, -1, expressionItem, operatorVisitor, termVisitor);
    }

    public static void walkExpressionTree(final ExpressionOperator parentOperator,
                                          final int childOffset,
                                          final ExpressionItem expressionItem,
                                          final ProcessorExpressionUtil.ExpressionOperatorVisitor operatorVisitor,
                                          final ProcessorExpressionUtil.ExpressionTermVisitor termVisitor) {
        if (expressionItem != null) {
            if (expressionItem instanceof ExpressionOperator) {
                final ExpressionOperator operator = (ExpressionOperator) expressionItem;
                if (operatorVisitor != null) {
                    operatorVisitor.accept(parentOperator, childOffset, operator);
                }
                if (operator.getChildren() != null) {
                    for (int i = 0; i < operator.getChildren().size(); i++) {
                        final ExpressionItem childItem = operator.getChildren().get(i);
                        walkExpressionTree(operator, i, childItem, operatorVisitor, termVisitor);
                    }
                }
            } else if (expressionItem instanceof ExpressionTerm) {
                final ExpressionTerm term = (ExpressionTerm) expressionItem;
                if (termVisitor != null) {
                    termVisitor.accept(parentOperator, childOffset, term);
                }
            }
        }
    }
}
