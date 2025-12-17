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

package stroom.processor.impl.db.migration;

import stroom.util.logging.AsciiTable;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class TestV07_06_00_305 extends AbstractProcessorMigrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestV07_06_00_305.class);

    private static final String USER1 = "user_1_uuid";
    private static final String USER2 = "user_2_uuid";
    private static final String USER3 = "user_3_uuid";

    private static final String DOC1 = "doc_1_uuid";
    private static final String DOC2 = "doc_2_uuid";
    private static final String DOC3 = "doc_3_uuid";

    @Override
    protected String getTestDataScript() {
        final StringBuilder sb = new StringBuilder("""
                -- Cross module so doesn't exist in the test
                CREATE TABLE doc_permission (
                    id bigint NOT NULL AUTO_INCREMENT,
                    user_uuid varchar(255) NOT NULL,
                    doc_uuid varchar(255) NOT NULL,
                    permission varchar(255) NOT NULL,
                    PRIMARY KEY (id),
                    UNIQUE KEY `doc_permission_fk_user_uuid_doc_uuid_permission_idx` (
                        user_uuid,`doc_uuid`,`permission`),
                    KEY doc_permission_fk_user_uuid (`user_uuid`),
                    KEY doc_permission_doc_uuid (`doc_uuid`)
                ) ENGINE=InnoDB DEFAULT
                    CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

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
                    "user1",
                    1,
                    "user1",
                    "processorUUID",
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
                """);

        appendPermissionSql(sb, USER1, DOC1, "Owner");
        appendPermissionSql(sb, USER2, DOC1, "Owner");
        appendPermissionSql(sb, USER3, DOC1, "Read");

        appendPermissionSql(sb, USER1, DOC2, "Owner");
        appendPermissionSql(sb, USER3, DOC2, "Read");

        appendPermissionSql(sb, USER3, "foo", "Owner");

        appendPipeFilterSql(sb, DOC1);
        appendPipeFilterSql(sb, DOC2);
        appendPipeFilterSql(sb, DOC3);

        return sb.toString();
    }

    private void appendPipeFilterSql(final StringBuilder sb,
                                     final String pipeFilterUuid) {
        sb.append(LogUtil.message("""
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
                    "user1",
                    1,
                    "user1",
                    "{}",
                    @procId,
                    @procFilterTrackerId,
                    "my data",
                    10);
                """, pipeFilterUuid));
    }

    private void appendPermissionSql(final StringBuilder sb,
                                     final String userUuid,
                                     final String docUuid,
                                     final String permission) {

        sb.append(LogUtil.message("""
                insert into doc_permission (
                    user_uuid,
                    doc_uuid,
                    permission)
                values (
                    '{}',
                    '{}',
                    '{}');
                """, userUuid, docUuid, permission));
    }

    @Test
    void test() {
        // By this point the database will have been migrated up to the target version
        // with the test data having been applied prior to the target migration running.
        // If it fails before you get here then there is an exception in the migration
        // or the test data.

        // If you get here then you probably want to assert something about the migrated
        // state.

        final List<PermRow> permRows = getRows("""
                        select
                            id,
                            user_uuid,
                            doc_uuid,
                            permission
                        from doc_permission""",
                rec -> new PermRow(
                        rec.get("id", long.class),
                        rec.get("user_uuid", String.class),
                        rec.get("doc_uuid", String.class),
                        rec.get("permission", String.class)));

        LOGGER.info("permRows:\n{}", AsciiTable.fromCollection(permRows));

        // Read the affected rows
        final List<PipeFilterRow> pipeFilterRows = getRows("""
                        select
                            uuid,
                            run_as_user_uuid
                        from processor_filter
                        """,
                rec -> new PipeFilterRow(
                        rec.get("uuid", String.class),
                        rec.get("run_as_user_uuid", String.class)));

        LOGGER.info("pipeFilterRows:\n{}", AsciiTable.fromCollection(pipeFilterRows));

        Assertions.assertThat(pipeFilterRows)
                .containsExactlyInAnyOrder(
                        new PipeFilterRow(DOC1, USER2),
                        new PipeFilterRow(DOC2, USER1),
                        new PipeFilterRow(DOC3, null));
    }


    // --------------------------------------------------------------------------------

    public record PermRow(
            long id,
            String docUuid,
            String userUuid,
            String permission) {

    }


    // --------------------------------------------------------------------------------


    public record PipeFilterRow(
            String docUuid,
            String runAsUserUuid) {

    }
}
