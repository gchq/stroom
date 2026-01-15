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

package stroom.state.impl.dao;

import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.DateTimeSettings;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValFloat;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.state.impl.ScyllaDbExpressionUtil;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.relation.Relation;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class SearchHelper {

    private final Provider<CqlSession> sessionProvider;
    private final CqlIdentifier table;
    private final Map<String, ScyllaDbColumn> columnMap;
    private final String valueTypeFieldName;
    private final String valueFieldName;

    private int valueTypePosition = -1;
    private int valuePosition = -1;

    public SearchHelper(final Provider<CqlSession> sessionProvider,
                        final CqlIdentifier table,
                        final Map<String, ScyllaDbColumn> columnMap,
                        final String valueTypeFieldName,
                        final String valueFieldName) {
        this.sessionProvider = sessionProvider;
        this.table = table;
        this.columnMap = columnMap;
        this.valueTypeFieldName = valueTypeFieldName;
        this.valueFieldName = valueFieldName;
    }

    void search(final ExpressionCriteria criteria,
                final FieldIndex fieldIndex,
                final DateTimeSettings dateTimeSettings,
                final ValuesConsumer consumer) {
        final List<Relation> relations = new ArrayList<>();
        ScyllaDbExpressionUtil.getRelations(columnMap, criteria.getExpression(), relations, dateTimeSettings);
        final String[] fieldNames = fieldIndex.getFields();
        final List<CqlIdentifier> columns = new ArrayList<>();

        int columnPos = 0;
        final ValFunction[] valFunctions = new ValFunction[fieldNames.length];
        for (int i = 0; i < fieldNames.length; i++) {
            final String fieldName = fieldNames[i];
            final ScyllaDbColumn column = columnMap.get(fieldName);
            if (column != null) {
                columns.add(column.cqlIdentifier());
                final int pos = columnPos;

                if (valueTypeFieldName.equals(fieldName)) {
                    if (valueTypePosition == -1) {
                        valueTypePosition = columnPos;
                    }
                    valFunctions[i] = row -> ValUtil.getType(row.getByte(pos));
                } else if (valueFieldName.equals(fieldName)) {
                    if (valuePosition == -1) {
                        valuePosition = columnPos;
                    }
                    valFunctions[i] = row -> ValUtil.getValue(row.getByte(valueTypePosition), row.getByteBuffer(pos));
                } else {
                    valFunctions[i] = convertRow(column.dataType(), pos);
                }

                columnPos++;
            } else {
                valFunctions[i] = row -> ValNull.INSTANCE;
            }
        }

        // Add the value type and record the value type position if it is needed.
        if (valuePosition != -1 && valueTypePosition == -1) {
            columns.add(columnMap.get(valueTypeFieldName).cqlIdentifier());
            valueTypePosition = columnPos;
        }

        if (columns.isEmpty()) {
            throw new RuntimeException("You must select one or more columns");
        }

        final SimpleStatement statement = QueryBuilder.selectFrom(table)
                .columns(columns.toArray(new CqlIdentifier[0]))
                .where(relations)
                .allowFiltering()
                .build();
        sessionProvider.get().execute(statement).forEach(row -> {
            final Val[] values = new Val[fieldNames.length];
            for (int i = 0; i < values.length; i++) {
                values[i] = valFunctions[i].apply(row);
            }
            consumer.accept(Val.of(values));
        });
    }

    private ValFunction convertRow(final DataType dataType, final int pos) {
        if (DataTypes.TEXT.equals(dataType)) {
            return row -> ValString.create(row.getString(pos));
        } else if (DataTypes.BOOLEAN.equals(dataType)) {
            return row -> ValBoolean.create(row.getBoolean(pos));
        } else if (DataTypes.INT.equals(dataType)) {
            return row -> ValInteger.create(row.getInt(pos));
        } else if (DataTypes.BIGINT.equals(dataType)) {
            return row -> ValLong.create(row.getLong(pos));
        } else if (DataTypes.FLOAT.equals(dataType)) {
            return row -> ValFloat.create(row.getFloat(pos));
        } else if (DataTypes.DOUBLE.equals(dataType)) {
            return row -> ValDouble.create(row.getDouble(pos));
        } else if (DataTypes.TIMESTAMP.equals(dataType)) {
            return row -> ValDate.create(row.getInstant(pos));
        }

        throw new RuntimeException("Unexpected data type: " + dataType);
    }

    private interface ValFunction extends Function<Row, Val> {

    }
}
