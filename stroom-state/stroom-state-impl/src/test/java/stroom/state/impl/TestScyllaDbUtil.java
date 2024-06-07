package stroom.state.impl;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

public class TestScyllaDbUtil {

    @Test
    void testConnection() {
        try (final CqlSession session = ScyllaDbUtil.forTesting()) {
            ScyllaDbUtil.printMetadata(session, ScyllaDbUtil.TEST_KEYSPACE);
            StateDao.dropTable(session);
            StateDao.createTable(session);
        }
    }

    @Test
    void dump() {
        try (final CqlSession session = ScyllaDbUtil.keyspace(
                ScyllaDbUtil.DEFAULT_CONNECTION_YAML,
                ScyllaDbUtil.DEFAULT_KEYSPACE)) {
            ScyllaDbUtil.printMetadata(session, ScyllaDbUtil.DEFAULT_KEYSPACE);

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
                        ValueTypeId.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(row.getByte(3)),
                        row.getByteBuffer(4));

                String value = state.value().toString();
                if (state.typeId() == ValueTypeId.STRING) {
                    value = new String(state.value().array(), StandardCharsets.UTF_8);
                }

                System.out.println("map: " + state.map() +
                        ", key: " + state.key() +
                        ", effectiveTime: " + state.effectiveTime() +
                        ", typeId: " + state.typeId() +
                        ", value: " + value);
            });


            StateRequest request = new StateRequest(
                    "FILENO_TO_LOCATION_MAP",
                    "2",
                    Instant.parse("2010-01-01T00:00:00Z"));
            final Optional<State> optional = StateDao.getState(session, request);
            System.out.println(optional);
        }
    }
}
