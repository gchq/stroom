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
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.internal.querybuilder.schema.compaction.DefaultTimeWindowCompactionStrategy;
import jakarta.inject.Provider;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.literal;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.createTable;

public class RangedStateDao extends AbstractStateDao<RangedState> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RangedStateDao.class);

    private static final CqlIdentifier COLUMN_KEY_START = CqlIdentifier.fromCql("key_start");
    private static final CqlIdentifier COLUMN_KEY_END = CqlIdentifier.fromCql("key_end");
    private static final CqlIdentifier COLUMN_VALUE_TYPE = CqlIdentifier.fromCql("value_type");
    private static final CqlIdentifier COLUMN_VALUE = CqlIdentifier.fromCql("value");
    private static final CqlIdentifier COLUMN_INSERT_TIME = CqlIdentifier.fromCql("insert_time");

    private static final Map<String, ScyllaDbColumn> COLUMN_MAP = Map.of(
            RangedStateFields.KEY_START,
            new ScyllaDbColumn(RangedStateFields.KEY_START, DataTypes.BIGINT, COLUMN_KEY_START),
            RangedStateFields.KEY_END,
            new ScyllaDbColumn(RangedStateFields.KEY_END, DataTypes.BIGINT, COLUMN_KEY_END),
            RangedStateFields.VALUE_TYPE,
            new ScyllaDbColumn(RangedStateFields.VALUE_TYPE, DataTypes.TINYINT, COLUMN_VALUE_TYPE),
            RangedStateFields.VALUE,
            new ScyllaDbColumn(RangedStateFields.VALUE, DataTypes.BLOB, COLUMN_VALUE),
            RangedStateFields.INSERT_TIME,
            new ScyllaDbColumn(RangedStateFields.INSERT_TIME, DataTypes.TIMESTAMP, COLUMN_INSERT_TIME));

    public RangedStateDao(final Provider<CqlSession> sessionProvider, final String tableName) {
        super(sessionProvider, CqlIdentifier.fromCql(tableName));
    }

    @Override
    void createTables() {
        LOGGER.info("Creating table: " + table);
        LOGGER.logDurationIfInfoEnabled(() -> {
            final SimpleStatement statement = createTable(table)
                    .ifNotExists()
                    .withPartitionKey(COLUMN_KEY_START, DataTypes.BIGINT)
                    .withPartitionKey(COLUMN_KEY_END, DataTypes.BIGINT)
                    .withColumn(COLUMN_VALUE_TYPE, DataTypes.TINYINT)
                    .withColumn(COLUMN_VALUE, DataTypes.BLOB)
                    .withColumn(COLUMN_INSERT_TIME, DataTypes.TIMESTAMP)
                    .withCompaction(new DefaultTimeWindowCompactionStrategy())
                    .build();
            sessionProvider.get().execute(statement);
        }, "createTables()");
    }

    @Override
    public void insert(final List<RangedState> states) {
        Objects.requireNonNull(states, "Null states list");
        final Instant now = Instant.now();
        final SimpleStatement statement = insertInto(table)
                .value(COLUMN_KEY_START, bindMarker())
                .value(COLUMN_KEY_END, bindMarker())
                .value(COLUMN_VALUE_TYPE, bindMarker())
                .value(COLUMN_VALUE, bindMarker())
                .value(COLUMN_INSERT_TIME, bindMarker())
                .usingTimeout(TEN_SECONDS)
                .build();
        final PreparedStatement preparedStatement = prepare(statement);
        try (final BatchStatementExecutor executor = new BatchStatementExecutor(sessionProvider)) {
            for (final RangedState state : states) {
                executor.addStatement(preparedStatement.bind(
                        state.keyStart(),
                        state.keyEnd(),
                        state.typeId(),
                        state.value(),
                        now));
            }
        }
    }

    @Override
    public void delete(final List<RangedState> states) {
        final SimpleStatement statement = deleteFrom(table)
                .whereColumn(COLUMN_KEY_START).isEqualTo(bindMarker())
                .whereColumn(COLUMN_KEY_END).isEqualTo(bindMarker())
                .build();
        doDelete(states, statement, state -> new Object[]{
                state.keyStart(),
                state.keyEnd()});
    }

    public Optional<State> getState(final RangedStateRequest request) {
        final SimpleStatement statement = selectFrom(table)
                .column(COLUMN_VALUE_TYPE)
                .column(COLUMN_VALUE)
                .whereColumn(COLUMN_KEY_START).isLessThanOrEqualTo(bindMarker())
                .whereColumn(COLUMN_KEY_END).isGreaterThanOrEqualTo(bindMarker())
                .limit(1)
                .allowFiltering()
                .build();
        final PreparedStatement preparedStatement = sessionProvider.get().prepare(statement);
        final BoundStatement bound = preparedStatement.bind(
                request.key(),
                request.key());
        return Optional
                .ofNullable(sessionProvider.get().execute(bound).one())
                .map(row -> new State(
                        Long.toString(request.key()),
                        row.getByte(0),
                        row.getByteBuffer(1)));
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final DateTimeSettings dateTimeSettings,
                       final ValuesConsumer consumer) {
        final SearchHelper searchHelper = new SearchHelper(
                sessionProvider,
                table,
                COLUMN_MAP,
                RangedStateFields.VALUE_TYPE,
                RangedStateFields.VALUE);
        searchHelper.search(criteria, fieldIndex, dateTimeSettings, consumer);
    }

    @Override
    public void removeOldData(final Instant oldest) {
        // We have to select rows to delete data here as you can only execute delete statements against primary keys.
        final SimpleStatement select = selectFrom(table)
                .column(COLUMN_KEY_START)
                .column(COLUMN_KEY_END)
                .whereColumn(COLUMN_INSERT_TIME).isLessThan(literal(oldest))
                .allowFiltering()
                .build();
        final SimpleStatement delete = deleteFrom(table)
                .whereColumn(COLUMN_KEY_START).isEqualTo(bindMarker())
                .whereColumn(COLUMN_KEY_END).isEqualTo(bindMarker())
                .build();
        final PreparedStatement preparedStatement = sessionProvider.get().prepare(delete);
        try (final BatchStatementExecutor executor = new BatchStatementExecutor(sessionProvider)) {
            for (final Row row : sessionProvider.get().execute(select)) {
                executor.addStatement(preparedStatement.bind(row.getLong(0), row.getLong(1)));
            }
        }
    }
}
