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

package stroom.state.impl;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.common.v2.DateExpressionParser;
import stroom.state.impl.dao.ScyllaDbColumn;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.querybuilder.Literal;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.relation.ColumnRelationBuilder;
import com.datastax.oss.driver.api.querybuilder.relation.Relation;
import com.datastax.oss.driver.api.querybuilder.term.Term;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ScyllaDbExpressionUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ScyllaDbExpressionUtil.class);

    public static void getRelations(final Map<String, ScyllaDbColumn> columnMap,
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
                                        convertTerm(columnMap, expressionTerm, dateTimeSettings);
                                relations.add(relation);
                            } catch (final RuntimeException e) {
                                LOGGER.error(e::getMessage, e);
                            }
                        }
                    } else if (child instanceof final ExpressionOperator operator) {
                        getRelations(columnMap, operator, relations, dateTimeSettings);
                    }
                });
                case OR -> throw new RuntimeException("OR conditions are not supported");
                case NOT -> throw new RuntimeException("NOT conditions are not supported");
            }
        }
    }

    private static Relation convertTerm(final Map<String, ScyllaDbColumn> columnMap,
                                        final ExpressionTerm term,
                                        final DateTimeSettings dateTimeSettings) {
        final String field = term.getField();
        final Condition condition = term.getCondition();
        String value = term.getValue();

        final ScyllaDbColumn column = columnMap.get(field);
        if (column == null) {
            throw new RuntimeException("Unexpected column " + field);
        }

        final ColumnRelationBuilder<Relation> builder = Relation.column(column.cqlIdentifier());
        return switch (condition) {
            case EQUALS, CONTAINS -> {
                if (DataTypes.TEXT.equals(column.dataType()) && (value.contains("*") || value.contains("?"))) {
                    value = value.replaceAll("\\*", "%");
                    value = value.replaceAll("\\?", "_");
                    yield builder.like(convertLiteral(column, value, dateTimeSettings));
                } else {
                    yield builder.isEqualTo(convertLiteral(column, value, dateTimeSettings));
                }
            }
            case NOT_EQUALS -> builder.isNotEqualTo(
                    convertLiteral(column, value, dateTimeSettings));
            case LESS_THAN -> builder.isLessThan(
                    convertLiteral(column, value, dateTimeSettings));
            case LESS_THAN_OR_EQUAL_TO -> builder.isLessThanOrEqualTo(
                    convertLiteral(column, value, dateTimeSettings));
            case GREATER_THAN -> builder.isGreaterThan(
                    convertLiteral(column, value, dateTimeSettings));
            case GREATER_THAN_OR_EQUAL_TO -> builder.isGreaterThanOrEqualTo(
                    convertLiteral(column, value, dateTimeSettings));
            case IN -> {
                Term[] terms = new Term[0];
                if (value != null) {
                    final String[] values = value.split(",");
                    terms = new Term[values.length];
                    for (int i = 0; i < values.length; i++) {
                        terms[i] = convertLiteral(column, values[i], dateTimeSettings);
                    }
                }
                yield builder.in(terms);
            }
            default -> throw new RuntimeException("Condition " + condition + " is not supported.");
        };
    }

    private static Literal convertLiteral(final ScyllaDbColumn column,
                                          final String value,
                                          final DateTimeSettings dateTimeSettings) {
        if (DataTypes.TEXT.equals(column.dataType())) {
            return QueryBuilder.literal(value);
        } else if (DataTypes.BOOLEAN.equals(column.dataType())) {
            return QueryBuilder.literal(Boolean.parseBoolean(value));
        } else if (DataTypes.INT.equals(column.dataType())) {
            return QueryBuilder.literal(Integer.parseInt(value));
        } else if (DataTypes.BIGINT.equals(column.dataType())) {
            return QueryBuilder.literal(Long.parseLong(value));
        } else if (DataTypes.FLOAT.equals(column.dataType())) {
            return QueryBuilder.literal(Float.parseFloat(value));
        } else if (DataTypes.DOUBLE.equals(column.dataType())) {
            return QueryBuilder.literal(Double.parseDouble(value));
        } else if (DataTypes.TIMESTAMP.equals(column.dataType())) {
            final long ms = DateExpressionParser.getMs(column.name(), value, dateTimeSettings);
            return QueryBuilder.literal(Instant.ofEpochMilli(ms));
        }

        throw new RuntimeException("Unable to convert literal: " + column.dataType());
    }
}
