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
        if (processorFilter == null || processorFilter.getQueryData() == null ||
                processorFilter.getQueryData().getExpression() == null) {
            return false;
        }

        final ExpressionOperator expression = processorFilter.getQueryData().getExpression();

        return containsIdField(expression) == false;
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

        return containsIdField(expression) == false;
    }

    private static boolean containsIdField(final ExpressionOperator expression) {
        if (expression == null) {
            return false;
        }
        for (final ExpressionItem item : expression.getChildren()) {
            if (item instanceof ExpressionTerm) {
                final ExpressionTerm term = (ExpressionTerm) item;
                if (MetaFields.ID.getFldName().equals(term.getField())) {
                    return true;
                }
                if (MetaFields.PARENT_ID.getFldName().equals(term.getField())) {
                    return true;
                }
                if (MetaFields.META_INTERNAL_PROCESSOR_ID.equals(term.getField())) {
                    return true;
                }
            } else if (item instanceof ExpressionOperator) {
                if (containsIdField((ExpressionOperator) item)) {
                    return true;
                }
            }
        }
        return false;
    }

}
