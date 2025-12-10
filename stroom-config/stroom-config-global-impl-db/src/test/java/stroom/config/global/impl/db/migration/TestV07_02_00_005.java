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

package stroom.config.global.impl.db.migration;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class TestV07_02_00_005 extends AbstractConfigMigrationTest {

    private static final String UUID1 = UUID.randomUUID().toString();
    private static final String UUID2 = UUID.randomUUID().toString();
    public static final String NAME1 = "bob";
    public static final String NAME2 = "fred";

    @Override
    protected String getTestDataScript() {

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
                        INSERT INTO preferences (
                            version,
                            create_time_ms,
                            create_user,
                            update_time_ms,
                            update_user,
                            user_id,
                            dat)
                        VALUES (
                            1,
                            0,
                            @name1,
                            0,
                            @name1,
                            @name1,
                            'the dat');
                        """)
                .append("""
                        INSERT INTO preferences (
                            version,
                            create_time_ms,
                            create_user,
                            update_time_ms,
                            update_user,
                            user_id,
                            dat)
                        VALUES (
                            1,
                            0,
                            @name2,
                            0,
                            @name2,
                            @name2,
                            'the dat');
                        """)
                .build();
    }

    @Test
    void test() {

        final List<Row> rows = getRows("""
                        select
                            create_user,
                            update_user,
                            user_uuid
                        from preferences
                        """,
                rec -> new Row(
                        rec.get("create_user", String.class),
                        rec.get("update_user", String.class),
                        rec.get("user_uuid", String.class)));

        assertThat(rows)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        new Row(NAME1, NAME1, UUID1),
                        new Row(NAME2, NAME2, UUID2)));
    }


    // --------------------------------------------------------------------------------


    private record Row(String createUser, String updateUser, String userUuid) {

    }
}
