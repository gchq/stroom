/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.processor.shared;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;

public final class ProcessorExpressionUtil {

    private ProcessorExpressionUtil() {
        // Utility class.
    }

//    public static ExpressionOperator createBasicExpression() {
//        return ExpressionOperator.builder()
//                .addTerm(ProcessorFields.DELETED, Condition.EQUALS, false)
//                .build();
//    }
//
//    public static ExpressionOperator createFolderExpression(final DocRef folder) {
//        return createFoldersExpression(folder);
//    }
//
//    public static ExpressionOperator createFoldersExpression(final DocRef... folders) {
//        final ExpressionOperator.Builder builder = ExpressionOperator.builder();
//
//        if (folders != null) {
//            if (folders.length == 1) {
//                builder.addTerm(ProcessorFields.PIPELINE, Condition.IN_FOLDER, folders[0]);
//            } else if (folders.length > 0) {
//                final ExpressionOperator.Builder or = ExpressionOperator.builder().op(Op.OR);
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
        return ExpressionOperator.builder()
                .addDocRefTerm(ProcessorFields.PIPELINE, Condition.IS_DOC_REF, pipelineRef)
                .addBooleanTerm(ProcessorFields.DELETED, Condition.EQUALS, false)
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
