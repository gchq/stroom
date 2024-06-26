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
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.querybuilder.relation.Relation;
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
import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.dropTable;

public class RangedStateDao extends AbstractStateDao<RangedState> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RangedStateDao.class);

    private static final CqlIdentifier TABLE = CqlIdentifier.fromCql("range");
    private static final CqlIdentifier COLUMN_KEY_START = CqlIdentifier.fromCql("key_start");
    private static final CqlIdentifier COLUMN_KEY_END = CqlIdentifier.fromCql("key_end");
    private static final CqlIdentifier COLUMN_TYPE_ID = CqlIdentifier.fromCql("type_Id");
    private static final CqlIdentifier COLUMN_VALUE = CqlIdentifier.fromCql("value");
    private static final CqlIdentifier COLUMN_INSERT_TIME = CqlIdentifier.fromCql("insert_time");
    private static final SimpleStatement CREATE_TABLE = createTable(TABLE)
            .ifNotExists()
            .withPartitionKey(COLUMN_KEY_START, DataTypes.BIGINT)
            .withPartitionKey(COLUMN_KEY_END, DataTypes.BIGINT)
            .withColumn(COLUMN_TYPE_ID, DataTypes.TINYINT)
            .withColumn(COLUMN_VALUE, DataTypes.BLOB)
            .withColumn(COLUMN_INSERT_TIME, DataTypes.TIMESTAMP)
            .withCompaction(new DefaultTimeWindowCompactionStrategy())
            .build();
    private static final SimpleStatement DROP_TABLE = dropTable(TABLE)
            .ifExists()
            .build();

    private static final SimpleStatement INSERT = insertInto(TABLE)
            .value(COLUMN_KEY_START, bindMarker())
            .value(COLUMN_KEY_END, bindMarker())
            .value(COLUMN_TYPE_ID, bindMarker())
            .value(COLUMN_VALUE, bindMarker())
            .value(COLUMN_INSERT_TIME, bindMarker())
            .build();

    private static final SimpleStatement DELETE = deleteFrom(TABLE)
            .where(
                    Relation.column(COLUMN_KEY_START).isEqualTo(bindMarker()),
                    Relation.column(COLUMN_KEY_END).isEqualTo(bindMarker()))
            .build();

    private static final SimpleStatement SELECT = selectFrom(TABLE)
            .column(COLUMN_TYPE_ID)
            .column(COLUMN_VALUE)
            .whereColumn(COLUMN_KEY_START).isLessThanOrEqualTo(bindMarker())
            .whereColumn(COLUMN_KEY_END).isGreaterThanOrEqualTo(bindMarker())
            .limit(1)
            .allowFiltering()
            .build();

    private static final Map<String, CqlIdentifier> COLUMN_MAP = Map.of(
            RangedStateFields.KEY_START, COLUMN_KEY_START,
            RangedStateFields.KEY_END, COLUMN_KEY_END,
            RangedStateFields.VALUE_TYPE, COLUMN_TYPE_ID,
            RangedStateFields.VALUE, COLUMN_VALUE);

    private final SearchHelper searchHelper;

    public RangedStateDao(final Provider<CqlSession> sessionProvider) {
        super(sessionProvider, TABLE);
        searchHelper = new SearchHelper(
                sessionProvider,
                TABLE,
                COLUMN_MAP,
                RangedStateFields.FIELD_MAP,
                RangedStateFields.VALUE_TYPE,
                RangedStateFields.VALUE);
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

    @Override
    public void insert(final List<RangedState> states) {
        Objects.requireNonNull(states, "Null states list");
        final Instant now = Instant.now();
        final PreparedStatement preparedStatement = sessionProvider.get().prepare(INSERT);
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
        doDelete(states, DELETE, state -> new Object[]{
                state.keyStart(),
                state.keyEnd()});
    }

    public Optional<State> getState(final RangedStateRequest request) {
        final PreparedStatement preparedStatement = sessionProvider.get().prepare(SELECT);
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
                       final ValuesConsumer consumer) {
        searchHelper.search(criteria, fieldIndex, consumer);
    }

    public void removeOldData(final Instant oldest) {
        // We have to select rows to delete data here as you can only execute delete statements against primary keys.
        final SimpleStatement select = selectFrom(TABLE)
                .column(COLUMN_KEY_START)
                .column(COLUMN_KEY_END)
                .whereColumn(COLUMN_INSERT_TIME).isLessThan(literal(oldest))
                .allowFiltering()
                .build();
        final SimpleStatement delete = deleteFrom(TABLE)
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
