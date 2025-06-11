package stroom.state.impl;

import stroom.pipeline.refdata.store.StringValue;
import stroom.state.impl.dao.State;
import stroom.state.impl.dao.StateDao;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled
public class TestScyllaDbUtil {
//
//    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestScyllaDbUtil.class);
//
//    @Test
//    void testConnection() {
//        ScyllaDbUtil.test((sessionProvider, tableName) ->
//                ScyllaDbUtil.printMetadata(sessionProvider, "test"));
//    }
//
//    @Test
//    void testReplaceKeyspaceNameInCql() {
//        final String newKeyspaceName = "this_is_a_test";
//        final String cql = ScyllaDbUtil.getDefaultKeyspaceCql();
//        final String actual = ScyllaDbUtil.replaceKeyspaceNameInCql(cql, newKeyspaceName);
//        final String expected = """
//                CREATE KEYSPACE IF NOT EXISTS this_is_a_test
//                WITH replication = { 'class': 'NetworkTopologyStrategy', 'replication_factor': '1' }
//                AND durable_writes = TRUE;""";
//        assertThat(actual).isEqualTo(expected);
//
//        final Optional<String> extracted = ScyllaDbUtil.extractKeyspaceNameFromCql(actual);
//        assertThat(extracted).isNotEmpty();
//        assertThat(extracted.get()).isEqualTo(newKeyspaceName);
//    }
//
//    /**
//     * Disabled as we don't usually want to drop all keyspaces
//     **/
//    @Disabled
//    @Test
//    public void dropAllKeyspacesFromDefault() {
//        ScyllaDbUtil.dropAllKeyspacesFromDefault();
//    }
//
//    @Test
//    void testConnectionPerformance() {
//        ScyllaDbUtil.test((sessionProvider, tableName) -> {
//            new StateDao(sessionProvider, "test").insert(Collections.singletonList(
//                    new State(
//                            "test",
//                            StringValue.TYPE_ID,
//                            ByteBuffer.wrap("test".getBytes(StandardCharsets.UTF_8)))));
//        });
//    }
}
