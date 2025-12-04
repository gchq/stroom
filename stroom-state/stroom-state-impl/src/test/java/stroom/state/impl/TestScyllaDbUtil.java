/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.state.impl;

import org.junit.jupiter.api.Disabled;

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
