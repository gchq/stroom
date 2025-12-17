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

package stroom.query.common.v2;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.string.StringUtil;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ExpressionValidator {

    private final Map<String, QueryField> fieldMap;
    private final DateTimeSettings dateTimeSettings;

    public ExpressionValidator(final List<QueryField> fields) {
        this.fieldMap = fields.stream()
                .collect(Collectors.toMap(QueryField::getFldName, Function.identity()));
        this.dateTimeSettings = DateTimeSettings.builder().build();
    }

    public ExpressionValidator(final List<QueryField> fields,
                               final DateTimeSettings dateTimeSettings) {
        this.fieldMap = fields.stream()
                .collect(Collectors.toMap(QueryField::getFldName, Function.identity()));
        this.dateTimeSettings = dateTimeSettings;
    }

    public void validate(final ExpressionItem expressionItem) throws ExpressionValidationException {
        switch (expressionItem) {
            case final ExpressionOperator expressionOperator -> validate(expressionOperator);
            case final ExpressionTerm expressionTerm -> validate(expressionTerm);
            case null -> throw new IllegalArgumentException("Null expressionItem");
            default ->
                    throw new IllegalArgumentException("Unexpected type " + expressionItem.getClass().getSimpleName());
        }
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
            if (term.getField() == null || term.getField().trim().isEmpty()) {
                throw new ExpressionValidationException("" +
                                                        "Expression term has a missing field");
            }
            final QueryField field = fieldMap.get(term.getField());
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
            final boolean isNumeric = field.isNumeric();
            if (isNumeric) {
                validateNumericTerm(term);
            } else {
                final FieldType fieldType = field.getFldType();
                if (FieldType.DATE.equals(fieldType)) {
                    validateDateTerm(term);
                }
            }
        }
    }

    private void validateNumericTerm(final ExpressionTerm term) {
        switch (term.getCondition()) {
            case CONTAINS:
            case EQUALS:
            case NOT_EQUALS:
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

    private void validateDateTerm(final ExpressionTerm term) {
        switch (term.getCondition()) {
            case EQUALS:
            case NOT_EQUALS:
            case GREATER_THAN:
            case GREATER_THAN_OR_EQUAL_TO:
            case LESS_THAN:
            case LESS_THAN_OR_EQUAL_TO:
                isValidDate(term);
                break;
            case BETWEEN:
                isValidDateArray(term, 2, 2);
                break;
        }
    }

    private void isValidDate(final ExpressionTerm term) {
        // This will throw ExpressionValidationException
        isValidDateStr(term.getValue());
    }

    private void isValidDateArray(final ExpressionTerm term, final int min, final int max) {
        final String[] parts = getAndValidateParts(term, min, max);
        for (final String part : parts) {
            // This will throw ExpressionValidationException
            isValidDateStr(part);
        }
    }

    private static String[] getAndValidateParts(final ExpressionTerm term, final int min, final int max) {
        final String value = term.getValue();
        if (value == null || value.isEmpty()) {
            throw new ExpressionValidationException("" +
                                                    "Expression term has no value set for '" +
                                                    term.getField() +
                                                    "'");
        }
        final String[] parts = value.split(",");
        if (parts.length < min || parts.length > max) {
            throw new ExpressionValidationException("" +
                                                    "Expression term has unexpected number of values '" +
                                                    term.getValue() +
                                                    "' for '" +
                                                    term.getField() +
                                                    "'");
        }
        return parts;
    }

    private void isValidDateStr(final String dateStr) {
        try {
            final Optional<ZonedDateTime> optDateTime = DateExpressionParser.parse(dateStr, dateTimeSettings);

            if (optDateTime.isEmpty()) {
                throw new ExpressionValidationException(LogUtil.message(
                        "Date value '{}' is not a valid. The date value should be one of:" +
                        "\n\nAn absolute date time value, e.g. '2016-01-23T12:34:11.844Z'." +
                        "\n\nA relative date time expression, e.g. 'now() - 7d'.",
                        dateStr));
            }
        } catch (final Exception e) {
            String msg = NullSafe.getOrElse(
                    e.getMessage(),
                    String::trim,
                    "");
            msg = msg.isBlank()
                    ? " "
                    : " " + StringUtil.ensureFullStop(msg) + " ";

            throw new ExpressionValidationException(LogUtil.message(
                    "Date value '{}' is not a valid." +
                    "{}" +
                    "The date value should be one of:" +
                    "\n\nAn absolute date time value, e.g. '2016-01-23T12:34:11.844Z'." +
                    "\n\nA relative date time expression, e.g. 'now() - 7d'.",
                    dateStr, msg));
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
        final String[] parts = getAndValidateParts(term, min, max);
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
