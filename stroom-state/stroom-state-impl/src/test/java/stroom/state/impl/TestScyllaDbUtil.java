package stroom.state.impl;

import stroom.pipeline.refdata.store.StringValue;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

public class TestScyllaDbUtil {

    @Disabled
    @Test
    void reset() {
        try (final CqlSession session = ScyllaDbUtil.keyspace(
                ScyllaDbUtil.getDefaultConnection(),
                ScyllaDbUtil.getDefaultKeyspace())) {
            StateDao.dropTables(session);
            StateDao.createTables(session);
            RangedStateDao.dropTables(session);
            RangedStateDao.createTables(session);
        }
    }

    @Test
    void testConnection() {
        ScyllaDbUtil.test((session, keyspaceName) -> {
            ScyllaDbUtil.printMetadata(session, keyspaceName);
            StateDao.dropTables(session);
            StateDao.createTables(session);
            RangedStateDao.dropTables(session);
            RangedStateDao.createTables(session);
        });
    }

    @Disabled
    @Test
    void dumpState() {
        try (final CqlSession session = ScyllaDbUtil.keyspace(
                ScyllaDbUtil.getDefaultConnection(),
                ScyllaDbUtil.getDefaultKeyspace())) {
            ScyllaDbUtil.printMetadata(session, ScyllaDbUtil.getDefaultKeyspace());

            final String cql = """
                    SELECT map, key, effective_time, type_id, value
                    FROM state
                    """;

            //SORT BY effective_time DESC

            final PreparedStatement prepared = session.prepare(cql);
            final BoundStatement bound = prepared.bind();
            session.execute(bound).forEach(row -> {
                final State state = new State(
                        row.getString(0),
                        row.getString(1),
                        row.getInstant(2),
                        row.getByte(3),
                        row.getByteBuffer(4));

                System.out.println("map: " + state.map() +
                        ", key: " + state.key() +
                        ", effectiveTime: " + state.effectiveTime() +
                        ", typeId: " + state.typeId() +
                        ", value: " + state.getValueAsString());
            });


            StateRequest request = new StateRequest(
                    "FILENO_TO_LOCATION_MAP",
                    "2",
                    Instant.parse("2010-01-01T00:00:00Z"));
            final Optional<State> optional = StateDao.getState(session, request);
            System.out.println(optional);
        }
    }

    @Disabled
    @Test
    void dumpRangedState() {
        try (final CqlSession session = ScyllaDbUtil.keyspace(
                ScyllaDbUtil.getDefaultConnection(),
                ScyllaDbUtil.getDefaultKeyspace())) {
            ScyllaDbUtil.printMetadata(session, ScyllaDbUtil.getDefaultKeyspace());

            final String cql = """
                    SELECT map, key_start, key_end, effective_time, type_id, value
                    FROM range
                    """;

            //SORT BY effective_time DESC

            final PreparedStatement prepared = session.prepare(cql);
            final BoundStatement bound = prepared.bind();
            session.execute(bound).forEach(row -> {
                final RangedState state = new RangedState(
                        row.getString(0),
                        row.getLong(1),
                        row.getLong(2),
                        row.getInstant(3),
                        row.getByte(4),
                        row.getByteBuffer(5));

                String value = state.value().toString();
                if (state.typeId() == StringValue.TYPE_ID) {
                    value = new String(state.value().array(), StandardCharsets.UTF_8);
                }

                System.out.println("map: " + state.map() +
                        ", key_start: " + state.keyStart() +
                        ", key_end: " + state.keyEnd() +
                        ", effectiveTime: " + state.effectiveTime() +
                        ", typeId: " + state.typeId() +
                        ", value: " + value);
            });
        }
    }
}