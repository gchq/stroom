package stroom.state.impl.dao;

import stroom.datasource.api.v2.QueryField;
import stroom.entity.shared.ExpressionCriteria;
import stroom.expression.api.DateTimeSettings;
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
import com.datastax.oss.driver.api.querybuilder.relation.Relation;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;

public class SearchHelper {

    private final Provider<CqlSession> sessionProvider;
    private final CqlIdentifier table;
    private final Map<String, CqlIdentifier> columnMap;
    private final Map<String, QueryField> fieldMap;
    private final String valueTypeFieldName;
    private final String valueFieldName;

    private int valueTypePosition = -1;
    private int valuePosition = -1;

    public SearchHelper(final Provider<CqlSession> sessionProvider,
                        final CqlIdentifier table,
                        final Map<String, CqlIdentifier> columnMap,
                        final Map<String, QueryField> fieldMap,
                        final String valueTypeFieldName,
                        final String valueFieldName) {
        this.sessionProvider = sessionProvider;
        this.table = table;
        this.columnMap = columnMap;
        this.fieldMap = fieldMap;
        this.valueTypeFieldName = valueTypeFieldName;
        this.valueFieldName = valueFieldName;
    }

    void search(final ExpressionCriteria criteria,
                final FieldIndex fieldIndex,
                final DateTimeSettings dateTimeSettings,
                final ValuesConsumer consumer) {
        final List<Relation> relations = new ArrayList<>();
        ScyllaDbExpressionUtil.getRelations(fieldMap, columnMap, criteria.getExpression(), relations, dateTimeSettings);
        final String[] fieldNames = fieldIndex.getFields();
        final List<CqlIdentifier> columns = new ArrayList<>();

        int columnPos = 0;
        final ValFunction[] valFunctions = new ValFunction[fieldNames.length];
        for (int i = 0; i < fieldNames.length; i++) {
            final String fieldName = fieldNames[i];
            final QueryField queryField = fieldMap.get(fieldName);
            if (queryField != null) {
                final CqlIdentifier column = columnMap.get(fieldName);
                columns.add(column);
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
                    switch (queryField.getFldType()) {
                        case ID -> valFunctions[i] = row -> ValLong.create(row.getLong(pos));
                        case BOOLEAN -> valFunctions[i] = row -> ValBoolean.create(row.getBoolean(pos));
                        case INTEGER -> valFunctions[i] = row -> ValInteger.create(row.getInt(pos));
                        case LONG -> valFunctions[i] = row -> ValLong.create(row.getLong(pos));
                        case FLOAT -> valFunctions[i] = row -> ValFloat.create(row.getFloat(pos));
                        case DOUBLE -> valFunctions[i] = row -> ValDouble.create(row.getDouble(pos));
                        case DATE -> valFunctions[i] = row -> ValDate.create(row.getInstant(pos));
                        case TEXT -> valFunctions[i] = row -> ValString.create(row.getString(pos));
                        default -> throw new RuntimeException("Unexpected field type");
                    }
                }

                columnPos++;
            } else {
                valFunctions[i] = row -> ValNull.INSTANCE;
            }
        }

        // Add the value type and record the value type position if it is needed.
        if (valuePosition != -1 && valueTypePosition == -1) {
            columns.add(columnMap.get(valueTypeFieldName));
            valueTypePosition = columnPos;
        }

        if (columns.isEmpty()) {
            throw new RuntimeException("You must select one or more columns");
        }

        final SimpleStatement statement = selectFrom(table)
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

    private interface ValFunction extends Function<Row, Val> {

    }
}
