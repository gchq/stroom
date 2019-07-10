package stroom.meta.shared;

import stroom.datasource.api.v2.AbstractField;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class ExpressionUtil {
    private ExpressionUtil() {
        // Utility class.
    }

    public static int termCount(final ExpressionOperator expressionOperator) {
        return terms(expressionOperator, null).size();
    }

    public static int termCount(final ExpressionOperator expressionOperator, final AbstractField field) {
        return terms(expressionOperator, field).size();
    }

    public static List<String> fields(final ExpressionOperator expressionOperator) {
        return terms(expressionOperator, null).stream().map(ExpressionTerm::getField).collect(Collectors.toList());
    }

    public static List<String> fields(final ExpressionOperator expressionOperator, final AbstractField field) {
        return terms(expressionOperator, field).stream().map(ExpressionTerm::getField).collect(Collectors.toList());
    }

    public static List<String> values(final ExpressionOperator expressionOperator) {
        return terms(expressionOperator, null).stream().map(ExpressionTerm::getValue).collect(Collectors.toList());
    }

    public static List<String> values(final ExpressionOperator expressionOperator, final AbstractField field) {
        return terms(expressionOperator, field).stream().map(ExpressionTerm::getValue).collect(Collectors.toList());
    }

    public static List<ExpressionTerm> terms(final ExpressionOperator expressionOperator, final AbstractField field) {
        final List<ExpressionTerm> terms = new ArrayList<>();
        addTerms(expressionOperator, field, terms);
        return terms;
    }

    private static void addTerms(final ExpressionOperator expressionOperator, final AbstractField field, final List<ExpressionTerm> terms) {
        if (expressionOperator != null && expressionOperator.enabled() && !Op.NOT.equals(expressionOperator.getOp())) {
            for (final ExpressionItem item : expressionOperator.getChildren()) {
                if (item.enabled()) {
                    if (item instanceof ExpressionTerm) {
                        final ExpressionTerm expressionTerm = (ExpressionTerm) item;
                        if ((field == null || field.getName().equals(expressionTerm.getField())) && expressionTerm.getValue() != null && expressionTerm.getValue().length() > 0) {
                            terms.add(expressionTerm);
                        }
                    } else if (item instanceof ExpressionOperator) {
                        addTerms((ExpressionOperator) item, field, terms);
                    }
                }
            }
        }
    }

    public static ExpressionOperator copyOperator(final ExpressionOperator operator) {
        if (operator == null) {
            return null;
        }

        final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(operator.enabled(), operator.getOp());
        operator.getChildren().forEach(item -> {
            if (item instanceof ExpressionOperator) {
                builder.addOperator(copyOperator((ExpressionOperator) item));

            } else if (item instanceof ExpressionTerm) {
                builder.addTerm(copyTerm((ExpressionTerm) item));
            }
        });
        return builder.build();
    }

    public static ExpressionTerm copyTerm(final ExpressionTerm term) {
        if (term == null) {
            return null;
        }

        final ExpressionTerm.Builder builder = new ExpressionTerm.Builder();
        builder.field(term.getField());
        builder.condition(term.getCondition());
        builder.value(term.getValue());
        builder.docRef(term.getDocRef());
        return builder.build();
    }
}
