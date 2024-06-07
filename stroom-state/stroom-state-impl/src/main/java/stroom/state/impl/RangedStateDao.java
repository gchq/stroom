package stroom.state.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatementBuilder;
import com.datastax.oss.driver.api.core.cql.BatchType;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;
import com.datastax.oss.driver.internal.querybuilder.schema.compaction.DefaultTimeWindowCompactionStrategy;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class RangedStateDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StateDao.class);

    private static final int MAX_BATCH_STATEMENTS = 65535;
    private static final String TABLE_NAME = "range";
    private static final CqlIdentifier TABLE = CqlIdentifier.fromCql(TABLE_NAME);

    public static void createTable(final CqlSession session) {
        LOGGER.info("Creating tables...");
        LOGGER.logDurationIfInfoEnabled(() -> {
            session.execute(SchemaBuilder.createTable(TABLE)
                    .ifNotExists()
                    .withPartitionKey("map", DataTypes.TEXT)
                    .withClusteringColumn("key_start", DataTypes.BIGINT)
                    .withClusteringColumn("key_end", DataTypes.BIGINT)
                    .withClusteringColumn("effective_time", DataTypes.TIMESTAMP)
                    .withColumn("type_Id", DataTypes.TINYINT)
                    .withColumn("value", DataTypes.BLOB)
                    .withClusteringOrder("key_start", ClusteringOrder.DESC)
                    .withClusteringOrder("key_end", ClusteringOrder.DESC)
                    .withClusteringOrder("effective_time", ClusteringOrder.DESC)
                    .withCompaction(new DefaultTimeWindowCompactionStrategy())
                    .build());
        }, "createTables()");
    }

    public static void dropTable(final CqlSession session) {
        session.execute(SchemaBuilder.dropTable(TABLE)
                .ifExists()
                .build());
    }

    public static void insert(final CqlSession session,
                              final List<RangedState> states) {
        Objects.requireNonNull(states, "Null states list");

        final String cql = """
                INSERT INTO range (map, key_start, key_end, effective_time, type_id, value)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        final PreparedStatement statement = session.prepare(cql);
        BatchStatementBuilder builder = new BatchStatementBuilder(BatchType.UNLOGGED);

        int statementCount = 0;
        for (final RangedState state : states) {
            builder = builder.addStatement(statement.bind(
                    state.map(),
                    state.from(),
                    state.to(),
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

    public static Optional<State> getState(final CqlSession session, final RangedStateRequest request) {
        final String cql = """
                SELECT effective_time, type_id, value
                FROM state
                WHERE map = ?
                AND key_start >= ?
                AND key_end <= ?
                AND effective_time <= ?
                LIMIT 1
                ALLOW FILTERING
                """;

        final PreparedStatement prepared = session.prepare(cql);
        final BoundStatement bound = prepared.bind(
                request.map(),
                request.key(),
                request.key(),
                request.effectiveTime());
        return Optional
                .ofNullable(session.execute(bound).one())
                .map(row -> new State(
                        request.map(),
                        Long.toString(request.key()),
                        row.getInstant(0),
                        ValueTypeId.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(row.getByte(1)),
                        row.getByteBuffer(2)));
    }
}
