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
import com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.querybuilder.relation.Relation;
import com.datastax.oss.driver.internal.querybuilder.schema.compaction.DefaultTimeWindowCompactionStrategy;
import jakarta.inject.Provider;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.literal;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.createTable;


public class TemporalStateDao extends AbstractStateDao<TemporalState> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TemporalStateDao.class);

    private static final CqlIdentifier COLUMN_KEY = CqlIdentifier.fromCql("key");
    private static final CqlIdentifier COLUMN_EFFECTIVE_TIME = CqlIdentifier.fromCql("effective_time");
    private static final CqlIdentifier COLUMN_VALUE_TYPE = CqlIdentifier.fromCql("value_type");
    private static final CqlIdentifier COLUMN_VALUE = CqlIdentifier.fromCql("value");

    private static final Map<String, ScyllaDbColumn> COLUMN_MAP = Map.of(
            TemporalStateFields.KEY,
            new ScyllaDbColumn(TemporalStateFields.KEY, DataTypes.TEXT, COLUMN_KEY),
            TemporalStateFields.EFFECTIVE_TIME,
            new ScyllaDbColumn(TemporalStateFields.EFFECTIVE_TIME, DataTypes.TIMESTAMP, COLUMN_EFFECTIVE_TIME),
            TemporalStateFields.VALUE_TYPE,
            new ScyllaDbColumn(TemporalStateFields.VALUE_TYPE, DataTypes.TINYINT, COLUMN_VALUE_TYPE),
            TemporalStateFields.VALUE,
            new ScyllaDbColumn(TemporalStateFields.VALUE, DataTypes.BLOB, COLUMN_VALUE));



    public TemporalStateDao(final Provider<CqlSession> sessionProvider, final String tableName) {
        super(sessionProvider, CqlIdentifier.fromCql(tableName));
    }

    @Override
    void createTables() {
        LOGGER.info("Creating table: " + table);
        LOGGER.logDurationIfInfoEnabled(() -> {
            final SimpleStatement statement = createTable(table)
                    .ifNotExists()
                    .withPartitionKey(COLUMN_KEY, DataTypes.TEXT)
                    .withClusteringColumn(COLUMN_EFFECTIVE_TIME, DataTypes.TIMESTAMP)
                    .withColumn(COLUMN_VALUE_TYPE, DataTypes.TINYINT)
                    .withColumn(COLUMN_VALUE, DataTypes.BLOB)
                    .withClusteringOrder(COLUMN_EFFECTIVE_TIME, ClusteringOrder.DESC)
                    .withCompaction(new DefaultTimeWindowCompactionStrategy())
                    .build();
            sessionProvider.get().execute(statement);
        }, "createTables()");
    }

    @Override
    public void insert(final List<TemporalState> states) {
        Objects.requireNonNull(states, "Null states list");
        final SimpleStatement statement = insertInto(table)
                .value(COLUMN_KEY, bindMarker())
                .value(COLUMN_EFFECTIVE_TIME, bindMarker())
                .value(COLUMN_VALUE_TYPE, bindMarker())
                .value(COLUMN_VALUE, bindMarker())
                .usingTimeout(TEN_SECONDS)
                .build();
        final PreparedStatement preparedStatement = prepare(statement);
        try (final BatchStatementExecutor executor = new BatchStatementExecutor(sessionProvider)) {
            for (final TemporalState state : states) {
                executor.addStatement(preparedStatement.bind(
                        state.key(),
                        state.effectiveTime(),
                        state.typeId(),
                        state.value()));
            }
        }
    }

    @Override
    public void delete(final List<TemporalState> states) {
        final SimpleStatement statement = deleteFrom(table)
                .whereColumn(COLUMN_KEY).isEqualTo(bindMarker())
                .whereColumn(COLUMN_EFFECTIVE_TIME).isEqualTo(bindMarker())
                .build();
        doDelete(states, statement, state -> new Object[]{
                state.key(),
                state.effectiveTime()});
    }

    public Optional<TemporalState> getState(final TemporalStateRequest request) {
        final SimpleStatement statement = selectFrom(table)
                .column(COLUMN_EFFECTIVE_TIME)
                .column(COLUMN_VALUE_TYPE)
                .column(COLUMN_VALUE)
                .whereColumn(COLUMN_KEY).isEqualTo(bindMarker())
                .whereColumn(COLUMN_EFFECTIVE_TIME).isLessThanOrEqualTo(bindMarker())
                .orderBy(COLUMN_EFFECTIVE_TIME, ClusteringOrder.DESC)
                .limit(1)
                .allowFiltering()
                .build();
        final PreparedStatement preparedStatement = sessionProvider.get().prepare(statement);
        final BoundStatement bound = preparedStatement.bind(request.key(), request.effectiveTime());
        return Optional
                .ofNullable(sessionProvider.get().execute(bound).one())
                .map(row -> new TemporalState(
                        request.key(),
                        row.getInstant(0),
                        row.getByte(1),
                        row.getByteBuffer(2)));
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
                TemporalStateFields.VALUE_TYPE,
                TemporalStateFields.VALUE);
        searchHelper.search(criteria, fieldIndex, dateTimeSettings, consumer);
    }

    private void findKeys(final List<Relation> relations,
                          final Consumer<String> consumer) {
        final SimpleStatement statement = selectFrom(table)
                .column(COLUMN_KEY)
                .where(relations)
                .groupByColumnIds(COLUMN_KEY)
                .build();
        for (final Row row : sessionProvider.get().execute(statement)) {
            final String key = row.getString(0);
            consumer.accept(key);
        }
    }

    @Override
    public void condense(final Instant oldest) {
        findKeys(Collections.emptyList(), key -> {
            final SimpleStatement select = selectFrom(table)
                    .column(COLUMN_EFFECTIVE_TIME)
                    .column(COLUMN_VALUE_TYPE)
                    .column(COLUMN_VALUE)
                    .whereColumn(COLUMN_KEY).isEqualTo(literal(key))
                    .whereColumn(COLUMN_EFFECTIVE_TIME).isLessThanOrEqualTo(literal(oldest))
                    .orderBy(COLUMN_EFFECTIVE_TIME, ClusteringOrder.ASC)
                    .allowFiltering()
                    .build();
            final SimpleStatement delete = deleteFrom(table)
                    .whereColumn(COLUMN_KEY).isEqualTo(bindMarker())
                    .whereColumn(COLUMN_EFFECTIVE_TIME).isEqualTo(bindMarker())
                    .build();
            final PreparedStatement preparedStatement = sessionProvider.get().prepare(delete);

            Byte lastTypeId = null;
            String lastValue = null;
            try (final BatchStatementExecutor executor = new BatchStatementExecutor(sessionProvider)) {
                for (final Row row : sessionProvider.get().execute(select)) {
                    final Instant effectiveTime = row.getInstant(0);
                    final byte typeId = row.getByte(1);
                    final ByteBuffer byteBuffer = row.getByteBuffer(2);
                    final String value = ValUtil.getString(typeId, byteBuffer);

                    if (lastTypeId != null) {
                        if (Objects.equals(typeId, lastTypeId) && Objects.equals(value, lastValue)) {
                            executor.addStatement(preparedStatement.bind(key, effectiveTime));
                        }
                    }

                    lastTypeId = typeId;
                    lastValue = value;
                }
            }
        });
    }

    @Override
    public void removeOldData(final Instant oldest) {
        // We have to select rows to delete data here as you can only execute delete statements against primary keys.
        final SimpleStatement select = selectFrom(table)
                .column(COLUMN_KEY)
                .column(COLUMN_EFFECTIVE_TIME)
                .whereColumn(COLUMN_EFFECTIVE_TIME).isLessThan(literal(oldest))
                .allowFiltering()
                .build();
        final SimpleStatement delete = deleteFrom(table)
                .whereColumn(COLUMN_KEY).isEqualTo(bindMarker())
                .whereColumn(COLUMN_EFFECTIVE_TIME).isEqualTo(bindMarker())
                .build();
        final PreparedStatement preparedStatement = sessionProvider.get().prepare(delete);
        try (final BatchStatementExecutor executor = new BatchStatementExecutor(sessionProvider)) {
            for (final Row row : sessionProvider.get().execute(select)) {
                executor.addStatement(preparedStatement.bind(row.getString(0), row.getInstant(1)));
            }
        }
    }
}
