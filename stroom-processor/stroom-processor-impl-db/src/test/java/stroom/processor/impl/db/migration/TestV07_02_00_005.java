package stroom.processor.impl.db.migration;

import stroom.util.logging.AsciiTable;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class TestV07_02_00_005 extends AbstractProcessorMigrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestV07_02_00_005.class);

    private static final String UUID1 = UUID.randomUUID().toString();
    private static final String UUID2 = UUID.randomUUID().toString();
    public static final String NAME1 = "bob";
    public static final String NAME2 = "fred";

    @Override
    protected String getTestDataScript() {

        // We have to create a skeleton stroom_user table to be able to join to in the mig
        return scriptBuilder()
                .append("""
                        CREATE TABLE stroom_user (
                          `id` int NOT NULL AUTO_INCREMENT,
                          `name` varchar(255) NOT NULL,
                          `uuid` varchar(255) NOT NULL,
                          PRIMARY KEY (`id`),
                          UNIQUE KEY `stroom_user_uuid_idx` (`uuid`)
                        ) ENGINE=InnoDB AUTO_INCREMENT=4
                          DEFAULT CHARSET=utf8mb4
                          COLLATE=utf8mb4_0900_ai_ci;
                        """)
                .setParam("name1", NAME1)
                .setParam("name2", NAME2)
                .setParam("uuid1", UUID1)
                .setParam("uuid2", UUID2)
                .append("""
                        insert into stroom_user (
                            name,
                            uuid)
                        values (
                            @name1,
                            @uuid1);
                        """)
                .append("""
                        insert into stroom_user (
                            name,
                            uuid)
                        values (
                            @name2,
                            @uuid2);
                        """)
                .append("""
                        insert into processor (
                            version,
                            create_time_ms,
                            create_user,
                            update_time_ms,
                            update_user,
                            uuid,
                            pipeline_uuid)
                        values (
                            1,
                            1,
                            @name1,
                            1,
                            @name1,
                            "myUUID",
                            "pipeUUID");
                        SET @procId = (SELECT LAST_INSERT_ID());

                        insert into processor_filter_tracker (
                            version,
                            min_meta_id,
                            min_event_id)
                        values (
                            1,
                            1,
                            1);
                        SET @procFilterTrackerId = (SELECT LAST_INSERT_ID());

                        insert into processor_filter (
                            version,
                            create_time_ms,
                            create_user,
                            update_time_ms,
                            update_user,
                            uuid,
                            fk_processor_id,
                            fk_processor_filter_tracker_id,
                            data,
                            priority)
                        values (
                            1,
                            1,
                            @name1,
                            1,
                            @name1,
                            "myUUID1",
                            @procId,
                            @procFilterTrackerId,
                            "my data",
                            10);

                        insert into processor_filter (
                            version,
                            create_time_ms,
                            create_user,
                            update_time_ms,
                            update_user,
                            uuid,
                            fk_processor_id,
                            fk_processor_filter_tracker_id,
                            data,
                            priority)
                        values (
                            1,
                            1,
                            @name2,
                            1,
                            @name2,
                            "myUUID2",
                            @procId,
                            @procFilterTrackerId,
                            "my data",
                            10);
                        """)
                .build();
    }

    @Test
    void test() {

        final List<Row> rows = getRows("""
                        select
                            create_user,
                            update_user,
                            owner_uuid
                        from processor_filter
                        """,
                rec -> new Row(
                        rec.get("create_user", String.class),
                        rec.get("update_user", String.class),
                        rec.get("owner_uuid", String.class)));

        assertThat(rows)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        new Row(NAME1, NAME1, UUID1),
                        new Row(NAME2, NAME2, UUID2)));

        LOGGER.info("rows:\n{}", AsciiTable.fromCollection(rows));
    }


    // --------------------------------------------------------------------------------


    public record Row(String createUser, String updateUser, String ownerUuid) {

    }
}
