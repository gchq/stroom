package stroom.state.impl;

import stroom.docstore.shared.DocRefUtil;
import stroom.state.shared.ScyllaDbDoc;
import stroom.state.shared.StateDoc;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatementBuilder;
import com.datastax.oss.driver.api.core.cql.BatchType;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class StateDao {

    private static final int MAX_BATCH_STATEMENTS = 65535;

    private final CqlSessionCache cqlSessionCache;
    private final ScyllaDbDocCache scyllaDbDocCache;

    @Inject
    public StateDao(final CqlSessionCache cqlSessionCache,
                    final ScyllaDbDocCache scyllaDbDocCache) {
        this.cqlSessionCache = cqlSessionCache;
        this.scyllaDbDocCache = scyllaDbDocCache;
    }

    private CqlSession getSession(final StateDoc stateDoc) {
        Objects.requireNonNull(stateDoc, "Null state doc");
        Objects.requireNonNull(stateDoc.getScyllaDbRef(), "Null ScyllaDB ref");

        final ScyllaDbDoc scyllaDbDoc = scyllaDbDocCache.get(stateDoc.getScyllaDbRef());
        Objects.requireNonNull(scyllaDbDoc, "Unable to find ScyllaDB doc referenced by " +
                DocRefUtil.createSimpleDocRefString(stateDoc.getScyllaDbRef()));

        return cqlSessionCache.get(scyllaDbDoc);
    }

    public void insert(final StateDoc stateDoc, final List<State> states) {
        Objects.requireNonNull(states, "Null states list");
        final CqlSession session = getSession(stateDoc);
        insert(session, states);
    }

    public static void insert(final CqlSession session,
                        final List<State> states) {
        final String cql = """
                INSERT INTO state (map, key, effective_time, type_id, value)
                VALUES (?, ?, ?, ?, ?)
                """;
        final PreparedStatement statement = session.prepare(cql);
        BatchStatementBuilder builder = new BatchStatementBuilder(BatchType.UNLOGGED);

        int statementCount = 0;
        for (final State state : states) {
            builder = builder.addStatement(statement.bind(
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

    public Optional<State> getState(final StateDoc stateDoc, final StateRequest request) {
        final CqlSession session = getSession(stateDoc);
        return getState(session, request);
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


        final String cql = """
                SELECT effective_time, type_id, value
                FROM state
                WHERE map = ?
                AND key = ?
                AND effective_time <= ?
                LIMIT 1
                ALLOW FILTERING
                """;

        //SORT BY effective_time DESC

        final PreparedStatement prepared = session.prepare(cql);
        final BoundStatement bound = prepared.bind(request.map(), request.key(), request.effectiveTime());
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
