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
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;

public final class ProcessorFilterExpressionUtil {

    private ProcessorFilterExpressionUtil() {
        // Utility class.
    }

    public static ExpressionOperator createBasicExpression() {
        return ExpressionOperator.builder()
                .addBooleanTerm(ProcessorFields.DELETED, Condition.EQUALS, false)
                .addBooleanTerm(ProcessorFilterFields.DELETED, Condition.EQUALS, false)
                .build();
    }

    public static ExpressionOperator createFolderExpression(final DocRef folder) {
        return createFoldersExpression(folder);
    }

    public static ExpressionOperator createFoldersExpression(final DocRef... folders) {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder();

        if (folders != null) {
            final ExpressionOperator.Builder or = ExpressionOperator.builder().op(Op.OR);
            for (final DocRef folder : folders) {
                or.addDocRefTerm(ProcessorFields.PIPELINE, Condition.IN_FOLDER, folder);
                or.addDocRefTerm(ProcessorFields.ANALYTIC_RULE, Condition.IN_FOLDER, folder);
            }
            builder.addOperator(or.build());
        }

        return builder
                .addBooleanTerm(ProcessorFields.DELETED, Condition.EQUALS, false)
                .addBooleanTerm(ProcessorFilterFields.DELETED, Condition.EQUALS, false)
                .build();
    }

    public static ExpressionOperator createPipelineExpression(final DocRef pipelineRef) {
        return ExpressionOperator.builder()
//                .addTerm(
//                        ProcessorFields.PROCESSOR_TYPE,
//                        Condition.EQUALS,
//                        ProcessorType.PIPELINE.getDisplayValue())
                .addDocRefTerm(ProcessorFields.PIPELINE, Condition.IS_DOC_REF, pipelineRef)
                .addBooleanTerm(ProcessorFields.DELETED, Condition.EQUALS, false)
                .addBooleanTerm(ProcessorFilterFields.DELETED, Condition.EQUALS, false)
                .build();
    }

    public static ExpressionOperator createAnalyticRuleExpression(final DocRef analyticRuleRef) {
        return ExpressionOperator.builder()
//                .addTerm(
//                        ProcessorFields.PROCESSOR_TYPE,
//                        Condition.EQUALS,
//                        ProcessorType.STREAMING_ANALYTIC.getDisplayValue())
                .addDocRefTerm(ProcessorFields.ANALYTIC_RULE, Condition.IS_DOC_REF, analyticRuleRef)
                .addBooleanTerm(ProcessorFields.DELETED, Condition.EQUALS, false)
                .addBooleanTerm(ProcessorFilterFields.DELETED, Condition.EQUALS, false)
                .build();
    }
}
