/*
 * Copyright 2026 Crown Copyright
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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// Tests `V08_00_00_005__processor_task_lifecycle.sql`, which adds the columns that record the runs of a task and
/// changes tasks that had failed to aborted.
class TestV08_00_00_005 extends AbstractProcessorMigrationTest {

    // More than one batch of the migration.
    private static final int MANY_FAILED = 10_500;
    private static final long FIRST_MANY_FAILED_ID = 1_000;

    private final long testStartMs = System.currentTimeMillis();

    @Override
    protected String getTestDataScript() {
        return scriptBuilder()
                .append("""
                        insert into processor (
                            version, create_time_ms, create_user, update_time_ms, update_user, uuid, pipeline_uuid)
                        values (1, 1, "user1", 1, "user1", "myUUID", "pipeUUID");
                        SET @procId = (SELECT LAST_INSERT_ID());

                        insert into processor_filter_tracker (version, min_meta_id, min_event_id)
                        values (1, 1, 1);
                        SET @procFilterTrackerId = (SELECT LAST_INSERT_ID());

                        insert into processor_filter (
                            version, create_time_ms, create_user, update_time_ms, update_user, uuid,
                            fk_processor_id, fk_processor_filter_tracker_id, data, priority)
                        values (1, 1, "user1", 1, "user1", "myUUID", @procId, @procFilterTrackerId, "my data", 10);
                        SET @procFilterId = (SELECT LAST_INSERT_ID());
                        """)
                .append(insertTask(1, 0)) // created
                .append(insertTask(2, 1)) // queued
                .append(insertTask(3, 2)) // assigned
                .append(insertTask(4, 3)) // processing
                .append(insertTask(10, 10)) // complete
                .append(insertTask(22, 22)) // failed
                .append(insertTask(99, 99)) // deleted
                .append("""
                        insert into processor_task (
                            id, version, fk_processor_filter_id, status, status_time_ms, meta_id)
                        with digit as (
                            select 0 d union all select 1 union all select 2 union all select 3 union all select 4
                            union all select 5 union all select 6 union all select 7 union all select 8
                            union all select 9)
                        select id, 5, @procFilterId, 22, 7, 1
                        from (
                            select %s + a.d + (10 * b.d) + (100 * c.d) + (1000 * d.d) + (10000 * e.d) id
                            from digit a, digit b, digit c, digit d, digit e) ids
                        where id < %s;
                        """.formatted(FIRST_MANY_FAILED_ID, FIRST_MANY_FAILED_ID + MANY_FAILED))
                .build();
    }

    @Test
    void test() {
        assertThat(getTableCount("processor_task")).isEqualTo(7 + MANY_FAILED);

        assertThat(getTasks("id < " + FIRST_MANY_FAILED_ID + " and id <> 22"))
                .containsExactly(
                        // Unfinished, so may have been run.
                        new Task(1, 5, 0, 7, 1, 0, null),
                        new Task(2, 5, 1, 7, 1, 0, null),
                        new Task(3, 5, 2, 7, 1, 0, null),
                        new Task(4, 5, 3, 7, 1, 0, null),
                        // Finished, so left alone.
                        new Task(10, 5, 10, 7, 0, 0, null),
                        new Task(99, 5, 99, 7, 0, 0, null));

        // Failed tasks, in every batch, become aborted as of now.
        final List<Task> aborted = getTasks("status = 23");
        assertThat(aborted).hasSize(1 + MANY_FAILED);
        assertThat(aborted).allSatisfy(task -> {
            assertThat(task.version()).isEqualTo(6);
            assertThat(task.runCount()).isZero();
            assertThat(task.faultCount()).isZero();
            assertThat(task.reason()).isEqualTo("Failed before upgrade");
            // The database is on this host in tests, but allow for its clock being a little different.
            assertThat(task.statusTimeMs())
                    .isBetween(testStartMs - 60_000, System.currentTimeMillis() + 60_000);
        });
        assertThat(getTasks("status = 22")).isEmpty();
    }

    @Test
    void testProceduresAreRemoved() {
        assertThat(getSingleValue("""
                        select count(*)
                        from information_schema.routines
                        where routine_schema = database()
                        and routine_name like 'processor_task_lifecycle%'""",
                int.class)).isZero();
    }

    private List<Task> getTasks(final String condition) {
        return getRows("""
                        select id, version, status, status_time_ms, run_count, fault_count, reason
                        from processor_task
                        where\s""" + condition + " order by id",
                rec -> new Task(
                        rec.get("id", Long.class),
                        rec.get("version", Integer.class),
                        rec.get("status", Integer.class),
                        rec.get("status_time_ms", Long.class),
                        rec.get("run_count", Integer.class),
                        rec.get("fault_count", Integer.class),
                        rec.get("reason", String.class)));
    }

    private static String insertTask(final long id, final int status) {
        return """
                insert into processor_task (id, version, fk_processor_filter_id, status, status_time_ms, meta_id)
                values (%s, 5, @procFilterId, %s, 7, 1);
                """.formatted(id, status);
    }


    // --------------------------------------------------------------------------------


    private record Task(long id,
                        int version,
                        int status,
                        long statusTimeMs,
                        int runCount,
                        int faultCount,
                        String reason) {

    }
}
