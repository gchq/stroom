package stroom.state.impl.dao;

import stroom.entity.shared.ExpressionCriteria;
import stroom.expression.api.DateTimeSettings;
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


public class StateDao extends AbstractStateDao<State> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StateDao.class);

    private static final CqlIdentifier TABLE = CqlIdentifier.fromCql("state");
    private static final CqlIdentifier COLUMN_KEY = CqlIdentifier.fromCql("key");
    private static final CqlIdentifier COLUMN_VALUE_TYPE = CqlIdentifier.fromCql("value_type");
    private static final CqlIdentifier COLUMN_VALUE = CqlIdentifier.fromCql("value");
    private static final CqlIdentifier COLUMN_INSERT_TIME = CqlIdentifier.fromCql("insert_time");
    private static final SimpleStatement CREATE_TABLE = createTable(TABLE)
            .ifNotExists()
            .withPartitionKey(COLUMN_KEY, DataTypes.TEXT)
            .withColumn(COLUMN_VALUE_TYPE, DataTypes.TINYINT)
            .withColumn(COLUMN_VALUE, DataTypes.BLOB)
            .withColumn(COLUMN_INSERT_TIME, DataTypes.TIMESTAMP)
            .withCompaction(new DefaultTimeWindowCompactionStrategy())
            .build();
    private static final SimpleStatement DROP_TABLE = dropTable(TABLE)
            .ifExists()
            .build();

    private static final SimpleStatement INSERT = insertInto(TABLE)
            .value(COLUMN_KEY, bindMarker())
            .value(COLUMN_VALUE_TYPE, bindMarker())
            .value(COLUMN_VALUE, bindMarker())
            .value(COLUMN_INSERT_TIME, bindMarker())
            .build();

    private static final SimpleStatement DELETE = deleteFrom(TABLE)
            .where(
                    Relation.column(COLUMN_KEY).isEqualTo(bindMarker()))
            .build();

    private static final SimpleStatement SELECT = selectFrom(TABLE)
            .column(COLUMN_VALUE_TYPE)
            .column(COLUMN_VALUE)
            .whereColumn(COLUMN_KEY).isEqualTo(bindMarker())
            .limit(1)
            .allowFiltering()
            .build();
    private static final Map<String, ScyllaDbColumn> COLUMN_MAP = Map.of(
            StateFields.KEY,
            new ScyllaDbColumn(StateFields.KEY, DataTypes.TEXT, COLUMN_KEY),
            StateFields.VALUE_TYPE,
            new ScyllaDbColumn(StateFields.VALUE_TYPE, DataTypes.TINYINT, COLUMN_VALUE_TYPE),
            StateFields.VALUE,
            new ScyllaDbColumn(StateFields.VALUE, DataTypes.BLOB, COLUMN_VALUE),
            StateFields.INSERT_TIME,
            new ScyllaDbColumn(StateFields.INSERT_TIME, DataTypes.TIMESTAMP, COLUMN_INSERT_TIME));

    private final SearchHelper searchHelper;

    public StateDao(final Provider<CqlSession> sessionProvider) {
        super(sessionProvider, TABLE);
        searchHelper = new SearchHelper(
                sessionProvider,
                TABLE,
                COLUMN_MAP,
                StateFields.VALUE_TYPE,
                StateFields.VALUE);
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
    public void insert(final List<State> states) {
        Objects.requireNonNull(states, "Null states list");
        final Instant now = Instant.now();
        final PreparedStatement preparedStatement = sessionProvider.get().prepare(INSERT);
        try (final BatchStatementExecutor executor = new BatchStatementExecutor(sessionProvider)) {
            for (final State state : states) {
                executor.addStatement(preparedStatement.bind(
                        state.key(),
                        state.typeId(),
                        state.value(),
                        now));
            }
        }
    }

    @Override
    public void delete(final List<State> states) {
        doDelete(states, DELETE, state -> new Object[]{
                state.key()});
    }

    public Optional<State> getState(final StateRequest request) {
        final PreparedStatement preparedStatement = sessionProvider.get().prepare(SELECT);
        final BoundStatement bound = preparedStatement.bind(request.key());
        return Optional
                .ofNullable(sessionProvider.get().execute(bound).one())
                .map(row -> new State(
                        request.key(),
                        row.getByte(0),
                        row.getByteBuffer(1)));
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final DateTimeSettings dateTimeSettings,
                       final ValuesConsumer consumer) {
        searchHelper.search(criteria, fieldIndex, dateTimeSettings, consumer);
    }

    @Override
    public void removeOldData(final Instant oldest) {
        // We have to select rows to delete data here as you can only execute delete statements against primary keys.
        final SimpleStatement select = selectFrom(TABLE)
                .column(COLUMN_KEY)
                .whereColumn(COLUMN_INSERT_TIME).isLessThan(literal(oldest))
                .allowFiltering()
                .build();
        final SimpleStatement delete = deleteFrom(TABLE)
                .whereColumn(COLUMN_KEY).isEqualTo(bindMarker())
                .build();
        final PreparedStatement preparedStatement = sessionProvider.get().prepare(delete);
        try (final BatchStatementExecutor executor = new BatchStatementExecutor(sessionProvider)) {
            for (final Row row : sessionProvider.get().execute(select)) {
                executor.addStatement(preparedStatement.bind(row.getString(0)));
            }
        }
    }
}
