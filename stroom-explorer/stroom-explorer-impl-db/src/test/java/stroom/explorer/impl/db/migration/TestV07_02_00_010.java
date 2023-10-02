package stroom.explorer.impl.db.migration;

import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class TestV07_02_00_010 extends AbstractExplorerMigrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestV07_02_00_010.class);

    @Override
    protected String getTestDataScript() {
        return """
                insert into explorer_node (type, uuid, name, tags)
                values ('System', '0', 'System', null);

                insert into explorer_node (type, uuid, name, tags)
                values ('Folder', 'uuid-1', 'My Folder', 'tag-one');
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
                            tags,
                            version
                        from explorer_node;
                        """,
                rec -> new Row(
                        rec.get("type", String.class),
                        rec.get("uuid", String.class),
                        rec.get("name", String.class),
                        rec.get("tags", String.class),
                        rec.get("version", int.class)));

        LOGGER.info("explorer_node table:\n{}", AsciiTable.builder(rows)
                .withColumn(Column.of("type", Row::type))
                .withColumn(Column.of("uuid", Row::uuid))
                .withColumn(Column.of("name", Row::name))
                .withColumn(Column.of("tags", row -> row.tags() == null
                        ? "null"
                        : "'" + row.tags() + "'"))
                .withColumn(Column.integer("version", Row::version))
                .build());

        // no change to row count
        Assertions.assertThat(getTableCount("explorer_node"))
                .isEqualTo(2);

        Assertions.assertThat(rows)
                .containsExactlyInAnyOrder(
                        new Row("System", "0", "System", null, 1),
                        new Row("Folder", "uuid-1", "My Folder", "tag-one", 1));
    }


    // --------------------------------------------------------------------------------


    public record Row(String type, String uuid, String name, String tags, int version) {

    }
}
