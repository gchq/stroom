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

package stroom.annotation.impl.db.migration;

import stroom.util.logging.AsciiTable;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class TestV07_06_00_110 extends AbstractAnnotationMigrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestV07_06_00_110.class);

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
                        INSERT INTO annotation (
                        id,
                        version,
                        create_time_ms,
                        create_user,
                        update_time_ms,
                        update_user,
                        uuid,
                        title,
                        subject,
                        status,
                        assigned_to_uuid,
                        comment,
                        history)
                        VALUES (
                            123,
                            1,
                            0,
                            @name1,
                            0,
                            @name1,
                            @uuid1,
                            'my title',
                            'my subject',
                            'my status',
                            @name1,
                            'my comment',
                            'my history');""")
                .append("""
                        INSERT INTO annotation (
                        id,
                        version,
                        create_time_ms,
                        create_user,
                        update_time_ms,
                        update_user,
                        uuid,
                        title,
                        subject,
                        status,
                        assigned_to_uuid,
                        comment,
                        history)
                        VALUES (
                            124,
                            1,
                            0,
                            @name2,
                            0,
                            @name2,
                            @uuid2,
                            'my title',
                            'my subject',
                            'my status',
                            @name2,
                            'my comment',
                            'my history');""")
                .append("""
                        INSERT INTO annotation_entry (
                        version,
                        create_time_ms,
                        create_user,
                        update_time_ms,
                        update_user,
                        fk_annotation_id,
                        type,
                        data)
                        VALUES (
                        1,
                        0,
                        @name1,
                        0,
                        @name1,
                        123,
                        'Assigned',
                        @name1);""")
                .append("""
                        INSERT INTO annotation_entry (
                        version,
                        create_time_ms,
                        create_user,
                        update_time_ms,
                        update_user,
                        fk_annotation_id,
                        type,
                        data)
                        VALUES (
                        1,
                        0,
                        @name1,
                        0,
                        @name1,
                        123,
                        'Title',
                        'My Title');""")
                .append("""
                        INSERT INTO annotation_entry (
                        version,
                        create_time_ms,
                        create_user,
                        update_time_ms,
                        update_user,
                        fk_annotation_id,
                        type,
                        data)
                        VALUES (
                        1,
                        0,
                        @name2,
                        0,
                        @name2,
                        124,
                        'Assigned',
                        @name2);""")
                .build();
    }

    @Test
    void test() {

        final List<Row> rows = getRows("""
                        select
                            entry_user_uuid,
                            type,
                            data
                        from annotation_entry""",
                rec -> new Row(
                        rec.get("entry_user_uuid", String.class),
                        rec.get("type", String.class),
                        rec.get("data", String.class)));

        assertThat(rows)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        new Row(UUID1, "Assigned", UUID1),
                        new Row(UUID1, "Title", "My Title"),
                        new Row(UUID2, "Assigned", UUID2)));

        LOGGER.info("rows:\n{}", AsciiTable.fromCollection(rows));
    }


    // --------------------------------------------------------------------------------


    public record Row(String entryUserUuid, String type, String data) {

    }
}
