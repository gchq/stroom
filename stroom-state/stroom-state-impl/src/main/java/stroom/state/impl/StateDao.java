package stroom.state.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatementBuilder;
import com.datastax.oss.driver.api.core.cql.BatchType;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.internal.querybuilder.schema.compaction.DefaultTimeWindowCompactionStrategy;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.createTable;
import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.dropTable;


public class StateDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StateDao.class);

    private static final int MAX_BATCH_STATEMENTS = 65535;

    private static final CqlIdentifier TABLE = CqlIdentifier.fromCql("state");
    private static final CqlIdentifier COLUMN_MAP = CqlIdentifier.fromCql("map");
    private static final CqlIdentifier COLUMN_KEY = CqlIdentifier.fromCql("key");
    private static final CqlIdentifier COLUMN_EFFECTIVE_TIME = CqlIdentifier.fromCql("effective_time");
    private static final CqlIdentifier COLUMN_TYPE_ID = CqlIdentifier.fromCql("type_Id");
    private static final CqlIdentifier COLUMN_VALUE = CqlIdentifier.fromCql("value");
    private static final SimpleStatement CREATE_TABLE = createTable(TABLE)
            .ifNotExists()
            .withPartitionKey(COLUMN_MAP, DataTypes.TEXT)
            .withPartitionKey(COLUMN_KEY, DataTypes.TEXT)
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
            .value(COLUMN_MAP, bindMarker())
            .value(COLUMN_KEY, bindMarker())
            .value(COLUMN_EFFECTIVE_TIME, bindMarker())
            .value(COLUMN_TYPE_ID, bindMarker())
            .value(COLUMN_VALUE, bindMarker())
            .build();

    private static final SimpleStatement SELECT = selectFrom(TABLE)
            .column(COLUMN_EFFECTIVE_TIME)
            .column(COLUMN_TYPE_ID)
            .column(COLUMN_VALUE)
            .whereColumn(COLUMN_MAP).isEqualTo(bindMarker())
            .whereColumn(COLUMN_KEY).isEqualTo(bindMarker())
            .whereColumn(COLUMN_EFFECTIVE_TIME).isLessThanOrEqualTo(bindMarker())
            .limit(1)
            .allowFiltering()
            .build();


    public static void createTables(final CqlSession session) {
        LOGGER.info("Creating tables...");
        LOGGER.logDurationIfInfoEnabled(() -> {
            session.execute(CREATE_TABLE);
        }, "createTables()");
    }

    public static void dropTables(final CqlSession session) {
        session.execute(DROP_TABLE);
    }

    public static void insert(final CqlSession session,
                              final List<State> states) {
        Objects.requireNonNull(states, "Null states list");

//        final SimpleStatement simpleStatement = insertInto("state")
//                .value("map", bindMarker())
//                .value("key", bindMarker())
//                .value("effective_time", bindMarker())
//                .value("type_id", bindMarker())
//                .value("value", bindMarker())
//                .build();

//        final String cql = """
//                INSERT INTO state (map, key, effective_time, type_id, value)
//                VALUES (?, ?, ?, ?, ?)
//                """;
//        final PreparedStatement statement = session.prepare(cql);

        final PreparedStatement preparedStatement = session.prepare(INSERT);
        BatchStatementBuilder builder = new BatchStatementBuilder(BatchType.UNLOGGED);

        int statementCount = 0;
        for (final State state : states) {
            builder = builder.addStatement(preparedStatement.bind(
                    state.map(),
                    state.key(),
                    state.effectiveTime(),
                    state.typeId().getPrimitiveValue(),
                    state.value()));
            statementCount++;

            if (statementCount >= MAX_BATCH_STATEMENTS) {
                session.execute(builder.build());
                builder.clearStatements();
                statementCount = 0;
            }
        }

        session.execute(builder.build());
        builder.clearStatements();
    }

    public static Optional<State> getState(final CqlSession session, final StateRequest request) {

//        final String cql1 = """
//                SELECT effective_time, value
//                FROM state
//                WHERE map = ?
//                AND key = ?
//                ALLOW FILTERING
//                """;
//
//        final PreparedStatement prepared1 = session.prepare(cql1);
//        final BoundStatement bound1 = prepared1.bind("TEST_MAP", "TEST_KEY");
//        session.execute(bound1).forEach(row -> {
//            final Instant effectiveTime = row.getInstant("effective_time");
//            final String value = row.getString("value");
//
//            LOGGER.info("Effective Time: {}, Value: {}", effectiveTime, value);
//        });
//
//
//        final String cql = """
//                SELECT effective_time, type_id, value
//                FROM state
//                WHERE map = ?
//                AND key = ?
//                AND effective_time <= ?
//                LIMIT 1
//                ALLOW FILTERING
//                """;


//        final SimpleStatement simpleStatement =
//                selectFrom("state")
//                .column("effective_time")
//                .column("type_id")
//                .column("value")
//                .whereColumn("map").isEqualTo(bindMarker())
//                .whereColumn("key").isEqualTo(bindMarker())
//                .whereColumn("effective_time").isLessThanOrEqualTo(bindMarker())
//                .limit(1)
//                .allowFiltering()
//                .build();


//                .value("key", bindMarker())
//                .value("effective_time", bindMarker())
//                .value("type_id", bindMarker())
//                .value("value", bindMarker())
//                .build();

        //SORT BY effective_time DESC

//        final PreparedStatement preparedStatement = session.prepare(cql);

        final PreparedStatement preparedStatement = session.prepare(SELECT);
        final BoundStatement bound = preparedStatement.bind(request.map(), request.key(), request.effectiveTime());
        return Optional
                .ofNullable(session.execute(bound).one())
                .map(row -> new State(
                        request.map(),
                        request.key(),
                        row.getInstant(0),
                        ValueTypeId.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(row.getByte(1)),
                        row.getByteBuffer(2)));
    }
}
