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

package stroom.processor.api;

import stroom.meta.shared.MetaFields;
import stroom.processor.shared.ProcessorFilter;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;

/**
 * Collection of utility methods relating to processor filters
 */
public final class ProcessorFilterUtil {

    private ProcessorFilterUtil() {
    }

    public static boolean shouldImport(final ProcessorFilter processorFilter) {
        if (processorFilter == null
            || processorFilter.isDeleted()
            || processorFilter.getQueryData() == null
            || processorFilter.getQueryData().getExpression() == null) {
            return false;
        }

        final ExpressionOperator expression = processorFilter.getQueryData().getExpression();

        return !containsIdField(expression);
    }

    public static boolean shouldExport(final ProcessorFilter processorFilter) {
        if (processorFilter == null || processorFilter.getQueryData() == null ||
            processorFilter.getQueryData().getExpression() == null) {
            return false;
        }

        if (processorFilter.isReprocess() || processorFilter.isDeleted()) {
            return false;
        }

        final ExpressionOperator expression = processorFilter.getQueryData().getExpression();

        return !containsIdField(expression);
    }

    private static boolean containsIdField(final ExpressionOperator expression) {
        if (expression == null) {
            return false;
        }
        for (final ExpressionItem item : expression.getChildren()) {
            if (item instanceof final ExpressionTerm term) {
                if (MetaFields.ID.getFldName().equals(term.getField())) {
                    return true;
                }
                if (MetaFields.PARENT_ID.getFldName().equals(term.getField())) {
                    return true;
                }
                if (MetaFields.META_INTERNAL_PROCESSOR_ID.getFldName().equals(term.getField())) {
                    return true;
                }
            } else if (item instanceof final ExpressionOperator expressionOperator) {
                if (containsIdField(expressionOperator)) {
                    return true;
                }
            }
        }
        return false;
    }
}
