package stroom.index.impl.db.migration;

import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TestV07_04_00_005__B extends AbstractIndexMigrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestV07_04_00_005__B.class);

    @Override
    protected String getTestDataScript() {
        return """
                DELETE FROM index_volume_group;

                INSERT INTO index_volume_group (
                    id,
                    version,
                    create_time_ms,
                    create_user,
                    update_time_ms,
                    update_user,
                    name)
                Values (
                    123,
                    1,
                    UNIX_TIMESTAMP() * 1000,
                    'admin',
                    UNIX_TIMESTAMP() * 1000,
                    'admin',
                    'Default Volume Group');

                INSERT INTO index_volume (
                    id,
                    version,
                    create_time_ms,
                    create_user,
                    update_time_ms,
                    update_user,
                    path,
                    fk_index_volume_group_id)
                Values (
                    1,
                    1,
                    UNIX_TIMESTAMP() * 1000,
                    'admin',
                    UNIX_TIMESTAMP() * 1000,
                    'admin',
                    '/my/path',
                    123);

                INSERT INTO index_volume_group (
                    id,
                    version,
                    create_time_ms,
                    create_user,
                    update_time_ms,
                    update_user,
                    name)
                Values (
                    789,
                    1,
                    UNIX_TIMESTAMP() * 1000,
                    'admin',
                    UNIX_TIMESTAMP() * 1000,
                    'admin',
                    'My Other Vol Group');
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
                        from index_volume_group;
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

        assertThat(getTableCount("index_volume"))
                .isEqualTo(1);
        assertThat(getTableCount("index_volume_group"))
                .isEqualTo(2);
        assertThat(rows)
                .hasSize(2);

        final Row row0 = rows.get(0);
        assertThat(row0.name)
                .isEqualTo("Default Volume Group");

        // From stroom.index.shared.IndexVolumeGroup#DEFAULT_VOLUME_UUID
        assertThat(row0.uuid)
                .isEqualTo("5de2d603-cfc7-45cf-a8b4-e06bdf454f5e");
        assertThat(row0.isDefault)
                .isEqualTo(true);

        final Row row1 = rows.get(1);
        assertThat(row1.name)
                .isEqualTo("My Other Vol Group");
        assertThat(row1.uuid)
                .isNotNull()
                .isNotEqualTo("5de2d603-cfc7-45cf-a8b4-e06bdf454f5e");
        assertThat(row1.isDefault)
                .isNull();
    }


    // --------------------------------------------------------------------------------


    public record Row(String name, String uuid, Boolean isDefault) {

    }
}
