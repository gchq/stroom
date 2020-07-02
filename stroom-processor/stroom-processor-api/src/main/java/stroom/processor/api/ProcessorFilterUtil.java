package stroom.processor.api;

import stroom.meta.shared.MetaFields;
import stroom.processor.shared.ProcessorFilter;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;

/**
 * Collection of utility methods relating to processor filters
 */
public final class ProcessorFilterUtil {

    private ProcessorFilterUtil() {
    }

    public static boolean shouldImport(final ProcessorFilter processorFilter) {
        if (processorFilter == null || processorFilter.getQueryData() == null ||
                processorFilter.getQueryData().getExpression() == null)
            return false;

        ExpressionOperator expression = processorFilter.getQueryData().getExpression();

        return containsIdField(expression) == false;
    }

    public static boolean shouldExport(final ProcessorFilter processorFilter) {
        if (processorFilter == null || processorFilter.getQueryData() == null ||
                processorFilter.getQueryData().getExpression() == null)
            return false;

        ExpressionOperator expression = processorFilter.getQueryData().getExpression();

        return containsIdField(expression) == false;
    }

    private static boolean containsIdField(ExpressionOperator expression) {
        if (expression == null)
            return false;
        for (ExpressionItem item : expression.getChildren()) {
            if (item instanceof ExpressionTerm) {
                ExpressionTerm term = (ExpressionTerm) item;
                if (MetaFields.ID.getName().equals(term.getField()))
                    return true;
                if (MetaFields.PARENT_ID.getName().equals(term.getField()))
                    return true;
                if (MetaFields.META_INTERNAL_PROCESSOR_ID.equals(term.getField()))
                    return true;
            } else if (item instanceof ExpressionOperator) {
                if (containsIdField((ExpressionOperator) item))
                    return true;
            }
        }
        return false;
    }

}
