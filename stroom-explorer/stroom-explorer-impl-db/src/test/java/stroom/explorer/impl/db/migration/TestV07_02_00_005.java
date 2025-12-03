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

package stroom.explorer.impl.db.migration;

import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class TestV07_02_00_005 extends AbstractExplorerMigrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestV07_02_00_005.class);

    @Override
    protected String getTestDataScript() {
        return """
                insert into explorer_node (type, uuid, name, tags)
                values ('System', '0', 'System', null);

                insert into explorer_node (type, uuid, name, tags)
                values ('Folder', 'uuid-1', 'My Folder', 'tag-one');

                insert into explorer_node (type, uuid, name, tags)
                values ('StatisticStore', 'uuid-2', 'Imp_exp_test_statistics', 'DataSource');

                insert into explorer_node (type, uuid, name, tags)
                values ('SolrIndex', 'uuid-3', 'mycore', 'foo DataSource');

                insert into explorer_node (type, uuid, name, tags)
                values ('Index', 'uuid-4', 'LAX_CARGO_VOLUME-INDEX', 'DataSource foo');

                insert into explorer_node (type, uuid, name, tags)
                values ('Index', 'uuid-5', 'BROADBAND_SPEED_TESTS-INDEX', '  DataSource ');

                insert into explorer_node (type, uuid, name, tags)
                values ('Folder', 'uuid-6', 'Other Folder', '');
                """;
    }

    @Test
    void test() {

        // By this point the database will have been migrated up to the target version
        // with the test data having been applied prior to the target migration running.
        // If it fails before you get here then there is an exception in the migration
        // or the test data.

        // If you get here then you probably want to assert something about the migrated
        // state.

        // Read the affected rows
        final List<Row> rows = getRows("""
                        select
                            type,
                            uuid,
                            name,
                            tags
                        from explorer_node;
                        """,
                rec -> new Row(
                        rec.get("type", String.class),
                        rec.get("uuid", String.class),
                        rec.get("name", String.class),
                        rec.get("tags", String.class)));

        LOGGER.info("explorer_node table:\n{}", AsciiTable.builder(rows)
                .withColumn(Column.of("type", Row::type))
                .withColumn(Column.of("uuid", Row::uuid))
                .withColumn(Column.of("name", Row::name))
                .withColumn(Column.of("tags", row -> row.tags() == null
                        ? "null"
                        : "'" + row.tags() + "'"))
                .build());

        // no change to row count
        Assertions.assertThat(getTableCount("explorer_node"))
                .isEqualTo(7);

        Assertions.assertThat(rows)
                .containsExactlyInAnyOrder(
                        new Row("System", "0", "System", null),
                        new Row("Folder", "uuid-1", "My Folder", "tag-one"),
                        new Row("StatisticStore", "uuid-2", "Imp_exp_test_statistics", null),
                        new Row("SolrIndex", "uuid-3", "mycore", "foo"),
                        new Row("Index", "uuid-4", "LAX_CARGO_VOLUME-INDEX", "foo"),
                        new Row("Index", "uuid-5", "BROADBAND_SPEED_TESTS-INDEX", null),
                        new Row("Folder", "uuid-6", "Other Folder", null));
    }


    // --------------------------------------------------------------------------------


    public record Row(String type, String uuid, String name, String tags) {

    }
}
