package stroom.query.api.v2;

import stroom.datasource.api.v2.AbstractField;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ExpressionValidator {

    private final Map<String, AbstractField> fieldMap;

    public ExpressionValidator(final List<AbstractField> fields) {
        fieldMap = fields.stream().collect(Collectors.toMap(AbstractField::getName, Function.identity()));
    }

    public void validate(final ExpressionOperator operator) throws ExpressionValidationException {
        if (operator.enabled() && operator.getChildren() != null) {
            for (final ExpressionItem item : operator.getChildren()) {
                if (item instanceof ExpressionOperator) {
                    validate((ExpressionOperator) item);
                } else if (item instanceof ExpressionTerm) {
                    validate((ExpressionTerm) item);
                }
            }
        }
    }

    public void validate(final ExpressionTerm term) throws ExpressionValidationException {
        if (term.enabled()) {
            if (term.getField() == null || term.getField().trim().length() == 0) {
                throw new ExpressionValidationException("" +
                        "Expression term has a missing field");
            }
            final AbstractField field = fieldMap.get(term.getField());
            if (field == null) {
                throw new ExpressionValidationException("" +
                        "Expression term has an unknown field '" +
                        term.getField() +
                        "'");
            }
            if (term.getCondition() == null) {
                throw new ExpressionValidationException("" +
                        "Expression term has no condition set for '" +
                        term.getField() +
                        "'");
            }
            if (field.isNumeric()) {
                switch (term.getCondition()) {
                    case CONTAINS:
                    case EQUALS:
                    case GREATER_THAN:
                    case GREATER_THAN_OR_EQUAL_TO:
                    case LESS_THAN:
                    case LESS_THAN_OR_EQUAL_TO:
                        isValidNumber(term);
                        break;
                    case BETWEEN:
                        isValidNumberArray(term, 2, 2);
                        break;
                    case IN:
                        isValidNumberArray(term, 1, Integer.MAX_VALUE);
                        break;
                    case IN_DICTIONARY:
                        break;
                    case IN_FOLDER:
                    case IS_DOC_REF:
                        invalidCondition(term);
                        break;
                    case IS_NULL:
                    case IS_NOT_NULL:
                }
            }
        }
    }

    private void isValidNumber(final ExpressionTerm term) {
        final String value = term.getValue();
        if (value == null || value.isEmpty()) {
            throw new ExpressionValidationException("" +
                    "Expression term has no value set for '" +
                    term.getField() +
                    "'");
        }
        try {
            Long.parseLong(term.getValue());
        } catch (final NumberFormatException e) {
            throw new ExpressionValidationException("" +
                    "Expression term has a non numeric value '" +
                    term.getValue() +
                    "' set for '" +
                    term.getField() +
                    "'");
        }
    }

    private void isValidNumberArray(final ExpressionTerm term, final int min, final int max) {
        final String value = term.getValue();
        if (value == null || value.isEmpty()) {
            throw new ExpressionValidationException("" +
                    "Expression term has no value set for '" +
                    term.getField() +
                    "'");
        }
        final String[] parts = value.split(",");
        if (parts.length > min && parts.length < max) {
            throw new ExpressionValidationException("" +
                    "Expression term has unexpected number of values '" +
                    term.getValue() +
                    "' for '" +
                    term.getField() +
                    "'");
        }
        try {
            for (final String part : parts) {
                Long.parseLong(part);
            }
        } catch (final NumberFormatException e) {
            throw new ExpressionValidationException("" +
                    "Expression term has a non numeric array value '" +
                    term.getValue() +
                    "' set for '" +
                    term.getField() +
                    "'");
        }
    }

    private void invalidCondition(final ExpressionTerm term) {
        throw new ExpressionValidationException("" +
                "Expression term has an invalid condition '" +
                term.getCondition().getDisplayValue() +
                "' set for '" +
                term.getField() +
                "'");
    }
}
