package stroom.data.store.impl.fs.db.migration;

import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TestV07_04_00_005__B extends AbstractDataStoreMigrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestV07_04_00_005__B.class);

    @Override
    protected String getTestDataScript() {
        return """
                -- Remove the one added by V07_03_00_01
                DELETE FROM fs_volume_group;
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
                            name,
                            uuid,
                            is_default
                        from fs_volume_group;
                        """,
                rec -> new Row(
                        rec.get("name", String.class),
                        rec.get("uuid", String.class),
                        rec.get("is_default", Boolean.class)));

        LOGGER.info("explorer_node table:\n{}", AsciiTable.builder(rows)
                .withColumn(Column.of("name", Row::name))
                .withColumn(Column.of("uuid", Row::uuid))
                .withColumn(Column.of("is_default", row -> row.isDefault == null
                        ? "null"
                        : "'" + row.isDefault + "'"))
                .build());

        // no change to row count
        assertThat(getTableCount("fs_volume"))
                .isEqualTo(0);
        Assertions.assertThat(getTableCount("fs_volume_group"))
                .isEqualTo(0);
    }


    // --------------------------------------------------------------------------------


    public record Row(String name, String uuid, Boolean isDefault) {

    }
}
