package stroom.state.impl;

import stroom.datasource.api.v2.QueryField;
import stroom.expression.api.DateTimeSettings;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.common.v2.DateExpressionParser;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.querybuilder.Literal;
import com.datastax.oss.driver.api.querybuilder.relation.ColumnRelationBuilder;
import com.datastax.oss.driver.api.querybuilder.relation.Relation;
import com.datastax.oss.driver.api.querybuilder.term.Term;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.literal;

public class ScyllaDbExpressionUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ScyllaDbExpressionUtil.class);

    public static void getRelations(final Map<String, QueryField> fieldMap,
                                    final Map<String, CqlIdentifier> columnMap,
                                    final ExpressionOperator expressionOperator,
                                    final List<Relation> relations,
                                    final DateTimeSettings dateTimeSettings) {
        if (expressionOperator != null &&
                expressionOperator.enabled() &&
                expressionOperator.getChildren() != null &&
                !expressionOperator.getChildren().isEmpty()) {
            switch (expressionOperator.op()) {
                case AND -> expressionOperator.getChildren().forEach(child -> {
                    if (child instanceof final ExpressionTerm expressionTerm) {
                        if (expressionTerm.enabled()) {
                            try {
                                final Relation relation =
                                        convertTerm(fieldMap, columnMap, expressionTerm, dateTimeSettings);
                                relations.add(relation);
                            } catch (final RuntimeException e) {
                                LOGGER.error(e::getMessage, e);
                            }
                        }
                    } else if (child instanceof final ExpressionOperator operator) {
                        getRelations(fieldMap, columnMap, operator, relations, dateTimeSettings);
                    }
                });
                case OR -> throw new RuntimeException("OR conditions are not supported");
                case NOT -> throw new RuntimeException("NOT conditions are not supported");
            }
        }
    }

    private static Relation convertTerm(final Map<String, QueryField> fieldMap,
                                        final Map<String, CqlIdentifier> columnMap,
                                        final ExpressionTerm term,
                                        final DateTimeSettings dateTimeSettings) {
        final CqlIdentifier column = columnMap.get(term.getField());
        if (column == null) {
            throw new RuntimeException("Unexpected column " + term.getField());
        }
        final QueryField queryField = fieldMap.get(term.getField());
        if (queryField == null) {
            throw new RuntimeException("Unexpected field " + term.getField());
        }

        final ColumnRelationBuilder<Relation> builder = Relation.column(column);
        Condition condition = term.getCondition();
        String value = term.getValue();
        return switch (condition) {
            case EQUALS, CONTAINS -> {
                if (value.contains("*") || value.contains("?")) {
                    value = value.replaceAll("\\*", "%");
                    value = value.replaceAll("\\?", "_");
                    yield builder.like(convertLiteral(queryField, value, dateTimeSettings));
                } else {
                    yield builder.isEqualTo(convertLiteral(queryField, value, dateTimeSettings));
                }
            }
            case NOT_EQUALS -> builder.isNotEqualTo(
                    convertLiteral(queryField, value, dateTimeSettings));
            case LESS_THAN -> builder.isLessThan(
                    convertLiteral(queryField, value, dateTimeSettings));
            case LESS_THAN_OR_EQUAL_TO -> builder.isLessThanOrEqualTo(
                    convertLiteral(queryField, value, dateTimeSettings));
            case GREATER_THAN -> builder.isGreaterThan(
                    convertLiteral(queryField, value, dateTimeSettings));
            case GREATER_THAN_OR_EQUAL_TO -> builder.isGreaterThanOrEqualTo(
                    convertLiteral(queryField, value, dateTimeSettings));
            case IN -> {
                Term[] terms = new Term[0];
                if (value != null) {
                    final String[] values = value.split(",");
                    terms = new Term[values.length];
                    for (int i = 0; i < values.length; i++) {
                        terms[i] = convertLiteral(queryField, values[i], dateTimeSettings);
                    }
                }
                yield builder.in(terms);
            }
            default -> throw new RuntimeException("Condition " + condition + " is not supported.");
        };
    }

    private static Literal convertLiteral(final QueryField queryField,
                                          final String value,
                                          final DateTimeSettings dateTimeSettings) {
        switch (queryField.getFldType()) {
            case ID -> {
                return literal(Long.parseLong(value));
            }
            case BOOLEAN -> {
                return literal(Boolean.parseBoolean(value));
            }
            case INTEGER -> {
                return literal(Integer.parseInt(value));
            }
            case LONG -> {
                return literal(Long.parseLong(value));
            }
            case FLOAT -> {
                return literal(Float.parseFloat(value));
            }
            case DOUBLE -> {
                return literal(Double.parseDouble(value));
            }
            case DATE -> {
                final long ms = DateExpressionParser.getMs(queryField.getFldName(), value, dateTimeSettings);
                return literal(Instant.ofEpochMilli(ms));
            }
            case TEXT -> {
                return literal(value);
            }
            case KEYWORD -> {
                return literal(value);
            }
            case IPV4_ADDRESS -> {
                return literal(Long.parseLong(value));
            }
            case DOC_REF -> {
//                return literal(Long.parseLong(value));
            }
        }
        throw new RuntimeException("Unable to convert literal: " + queryField.getFldType());
    }
}
