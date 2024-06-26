package stroom.state.impl.dao;

import stroom.entity.shared.ExpressionCriteria;
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
import java.util.function.BiConsumer;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.literal;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.createTable;
import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.dropTable;

public class TemporalRangedStateDao extends AbstractStateDao<TemporalRangedState> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TemporalRangedStateDao.class);

    private static final CqlIdentifier TABLE = CqlIdentifier.fromCql("temporal_range");
    private static final CqlIdentifier COLUMN_KEY_START = CqlIdentifier.fromCql("key_start");
    private static final CqlIdentifier COLUMN_KEY_END = CqlIdentifier.fromCql("key_end");
    private static final CqlIdentifier COLUMN_EFFECTIVE_TIME = CqlIdentifier.fromCql("effective_time");
    private static final CqlIdentifier COLUMN_TYPE_ID = CqlIdentifier.fromCql("type_Id");
    private static final CqlIdentifier COLUMN_VALUE = CqlIdentifier.fromCql("value");
    private static final SimpleStatement CREATE_TABLE = createTable(TABLE)
            .ifNotExists()
            .withPartitionKey(COLUMN_KEY_START, DataTypes.BIGINT)
            .withPartitionKey(COLUMN_KEY_END, DataTypes.BIGINT)
            .withClusteringColumn(COLUMN_EFFECTIVE_TIME, DataTypes.TIMESTAMP)
            .withColumn(COLUMN_TYPE_ID, DataTypes.TINYINT)
            .withColumn(COLUMN_VALUE, DataTypes.BLOB)
            .withClusteringOrder(COLUMN_EFFECTIVE_TIME, ClusteringOrder.DESC)
            .withCompaction(new DefaultTimeWindowCompactionStrategy())
            .build();
    private static final SimpleStatement DROP_TABLE = dropTable(TABLE)
            .ifExists()
            .build();

    private static final SimpleStatement INSERT = insertInto(TABLE)
            .value(COLUMN_KEY_START, bindMarker())
            .value(COLUMN_KEY_END, bindMarker())
            .value(COLUMN_EFFECTIVE_TIME, bindMarker())
            .value(COLUMN_TYPE_ID, bindMarker())
            .value(COLUMN_VALUE, bindMarker())
            .build();

    private static final SimpleStatement DELETE = deleteFrom(TABLE)
            .where(
                    Relation.column(COLUMN_KEY_START).isEqualTo(bindMarker()),
                    Relation.column(COLUMN_KEY_END).isEqualTo(bindMarker()),
                    Relation.column(COLUMN_EFFECTIVE_TIME).isEqualTo(bindMarker()))
            .build();

    private static final SimpleStatement SELECT = selectFrom(TABLE)
            .column(COLUMN_EFFECTIVE_TIME)
            .column(COLUMN_TYPE_ID)
            .column(COLUMN_VALUE)
            .whereColumn(COLUMN_KEY_START).isLessThanOrEqualTo(bindMarker())
            .whereColumn(COLUMN_KEY_END).isGreaterThanOrEqualTo(bindMarker())
            .whereColumn(COLUMN_EFFECTIVE_TIME).isLessThanOrEqualTo(bindMarker())
            .limit(1)
            .allowFiltering()
            .build();

    private static final Map<String, CqlIdentifier> COLUMN_MAP = Map.of(
            TemporalRangedStateFields.KEY_START, COLUMN_KEY_START,
            TemporalRangedStateFields.KEY_END, COLUMN_KEY_END,
            TemporalRangedStateFields.EFFECTIVE_TIME, COLUMN_EFFECTIVE_TIME,
            TemporalRangedStateFields.VALUE_TYPE, COLUMN_TYPE_ID,
            TemporalRangedStateFields.VALUE, COLUMN_VALUE);

    private final SearchHelper searchHelper;

    public TemporalRangedStateDao(final Provider<CqlSession> sessionProvider) {
        super(sessionProvider, TABLE);
        searchHelper = new SearchHelper(
                sessionProvider,
                TABLE,
                COLUMN_MAP,
                TemporalRangedStateFields.FIELD_MAP,
                TemporalRangedStateFields.VALUE_TYPE,
                TemporalRangedStateFields.VALUE);
    }

    @Override
    public void createTables() {
        LOGGER.info("Creating tables...");
        LOGGER.logDurationIfInfoEnabled(() -> {
            sessionProvider.get().execute(CREATE_TABLE);
        }, "createTables()");
    }

    @Override
    public void dropTables() {
        sessionProvider.get().execute(DROP_TABLE);
    }

    public void insert(final List<TemporalRangedState> states) {
        Objects.requireNonNull(states, "Null states list");
        final PreparedStatement statement = sessionProvider.get().prepare(INSERT);
        try (final BatchStatementExecutor executor = new BatchStatementExecutor(sessionProvider)) {
            for (final TemporalRangedState state : states) {
                executor.addStatement(statement.bind(
                        state.keyStart(),
                        state.keyEnd(),
                        state.effectiveTime(),
                        state.typeId(),
                        state.value()));
            }
        }
    }

    public Optional<TemporalState> getState(final TemporalRangedStateRequest request) {
        final PreparedStatement prepared = sessionProvider.get().prepare(SELECT);
        final BoundStatement bound = prepared.bind(
                request.key(),
                request.key(),
                request.effectiveTime());
        return Optional
                .ofNullable(sessionProvider.get().execute(bound).one())
                .map(row -> new TemporalState(
                        Long.toString(request.key()),
                        row.getInstant(0),
                        row.getByte(1),
                        row.getByteBuffer(2)));
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final ValuesConsumer consumer) {
        searchHelper.search(criteria, fieldIndex, consumer);
    }

    @Override
    public void delete(final List<TemporalRangedState> states) {
        doDelete(states, DELETE, state -> new Object[]{
                state.keyStart(),
                state.keyEnd(),
                state.effectiveTime()});
    }

    private void findKeys(final List<Relation> relations,
                          final BiConsumer<Long, Long> consumer) {
        final SimpleStatement statement = selectFrom(TABLE)
                .column(COLUMN_KEY_START)
                .column(COLUMN_KEY_END)
                .where(relations)
                .groupByColumnIds(COLUMN_KEY_START, COLUMN_KEY_END)
                .build();
        for (final Row row : sessionProvider.get().execute(statement)) {
            consumer.accept(row.getLong(0), row.getLong(1));
        }
    }

    public void condense(final Instant oldest) {
        findKeys(Collections.emptyList(), (keyStart, keyEnd) -> {
            final SimpleStatement select = selectFrom(TABLE)
                    .column(COLUMN_EFFECTIVE_TIME)
                    .column(COLUMN_TYPE_ID)
                    .column(COLUMN_VALUE)
                    .whereColumn(COLUMN_KEY_START).isEqualTo(literal(keyStart))
                    .whereColumn(COLUMN_KEY_END).isEqualTo(literal(keyEnd))
                    .whereColumn(COLUMN_EFFECTIVE_TIME).isLessThanOrEqualTo(literal(oldest))
                    .orderBy(COLUMN_EFFECTIVE_TIME, ClusteringOrder.ASC)
                    .allowFiltering()
                    .build();
            final SimpleStatement delete = deleteFrom(TABLE)
                    .whereColumn(COLUMN_KEY_START).isEqualTo(bindMarker())
                    .whereColumn(COLUMN_KEY_END).isEqualTo(bindMarker())
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
                            executor.addStatement(preparedStatement.bind(keyStart, keyEnd, effectiveTime));
                        }
                    }

                    lastTypeId = typeId;
                    lastValue = value;
                }
            }
        });
    }

    public void removeOldData(final Instant oldest) {
        // We have to select rows to delete data here as you can only execute delete statements against primary keys.
        final SimpleStatement select = selectFrom(TABLE)
                .column(COLUMN_KEY_START)
                .column(COLUMN_KEY_END)
                .column(COLUMN_EFFECTIVE_TIME)
                .whereColumn(COLUMN_EFFECTIVE_TIME).isLessThan(literal(oldest))
                .allowFiltering()
                .build();
        final SimpleStatement delete = deleteFrom(TABLE)
                .whereColumn(COLUMN_KEY_START).isEqualTo(bindMarker())
                .whereColumn(COLUMN_KEY_END).isEqualTo(bindMarker())
                .whereColumn(COLUMN_EFFECTIVE_TIME).isEqualTo(bindMarker())
                .build();
        final PreparedStatement preparedStatement = sessionProvider.get().prepare(delete);
        try (final BatchStatementExecutor executor = new BatchStatementExecutor(sessionProvider)) {
            for (final Row row : sessionProvider.get().execute(select)) {
                executor.addStatement(preparedStatement.bind(row.getLong(0), row.getLong(1), row.getInstant(2)));
            }
        }
    }
}
