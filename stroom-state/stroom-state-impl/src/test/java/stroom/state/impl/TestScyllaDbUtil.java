package stroom.state.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TestScyllaDbUtil {

    @Test
    void testConnection() {
        ScyllaDbUtil.test(ScyllaDbUtil::printMetadata);
    }

//    @Disabled
//    @Test
//    void dumpState() {
//        try (final CqlSession session = ScyllaDbUtil.keyspace(
//                ScyllaDbUtil.getDefaultConnection(),
//                ScyllaDbUtil.getDefaultKeyspace())) {
//            ScyllaDbUtil.printMetadata(() -> session, ScyllaDbUtil.getDefaultKeyspace());
//
//            final String cql = """
//                    SELECT map, key, effective_time, type_id, value
//                    FROM state
//                    """;
//
//            //SORT BY effective_time DESC
//
//            final PreparedStatement prepared = session.prepare(cql);
//            final BoundStatement bound = prepared.bind();
//            session.execute(bound).forEach(row -> {
//                final TemporalState state = new TemporalState(
//                        row.getString(0),
//                        row.getString(1),
//                        row.getInstant(2),
//                        row.getByte(3),
//                        row.getByteBuffer(4));
//
//                System.out.println("map: " + state.map() +
//                        ", key: " + state.key() +
//                        ", effectiveTime: " + state.effectiveTime() +
//                        ", typeId: " + state.typeId() +
//                        ", value: " + state.getValueAsString());
//            });
//
//
//            TemporalStateRequest request = new TemporalStateRequest(
//                    "FILENO_TO_LOCATION_MAP",
//                    "2",
//                    Instant.parse("2010-01-01T00:00:00Z"));
//            final Optional<TemporalState> optional = new TemporalStateDao(() -> session).getState(request);
//            System.out.println(optional);
//        }
//    }
//
//    @Disabled
//    @Test
//    void dumpRangedState() {
//        try (final CqlSession session = ScyllaDbUtil.keyspace(
//                ScyllaDbUtil.getDefaultConnection(),
//                ScyllaDbUtil.getDefaultKeyspace())) {
//            ScyllaDbUtil.printMetadata(() -> session, ScyllaDbUtil.getDefaultKeyspace());
//
//            final String cql = """
//                    SELECT map, key_start, key_end, effective_time, type_id, value
//                    FROM range
//                    """;
//
//            //SORT BY effective_time DESC
//
//            final PreparedStatement prepared = session.prepare(cql);
//            final BoundStatement bound = prepared.bind();
//            session.execute(bound).forEach(row -> {
//                final RangedState state = new RangedState(
//                        row.getString(0),
//                        row.getLong(1),
//                        row.getLong(2),
//                        row.getInstant(3),
//                        row.getByte(4),
//                        row.getByteBuffer(5));
//
//                String value = state.value().toString();
//                if (state.typeId() == StringValue.TYPE_ID) {
//                    value = new String(state.value().array(), StandardCharsets.UTF_8);
//                }
//
//                System.out.println("map: " + state.map() +
//                        ", key_start: " + state.keyStart() +
//                        ", key_end: " + state.keyEnd() +
//                        ", effectiveTime: " + state.effectiveTime() +
//                        ", typeId: " + state.typeId() +
//                        ", value: " + value);
//            });
//        }
//    }

    @Test
    void testReplaceKeyspaceNameInCql() {
        final String cql = ScyllaDbUtil.getDefaultKeyspaceCql();
        final String actual = ScyllaDbUtil.replaceKeyspaceNameInCql(cql, "this_is_a_test");
        final String expected = """
                CREATE KEYSPACE IF NOT EXISTS this_is_a_test
                WITH replication = { 'class': 'NetworkTopologyStrategy', 'replication_factor': '1' }
                AND durable_writes = TRUE;""";
        assertThat(actual).isEqualTo(expected);
    }
}
