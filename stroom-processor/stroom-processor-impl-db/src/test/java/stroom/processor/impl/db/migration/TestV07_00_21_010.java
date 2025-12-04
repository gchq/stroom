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

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestV07_00_21_010 extends AbstractProcessorMigrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestV07_00_21_010.class);

    @Override
    protected String getTestDataScript() {
        return Stream.of(
                        "Error",
                        "Error: some msg",
                        "Complete",
                        "Searching...",
                        "Creating...",
                        null)
                .map(status -> LogUtil.message("""
                        insert into processor_filter_tracker (
                            version,
                            min_meta_id,
                            min_event_id,
                            status)
                        values (
                            1,
                            1,
                            1,
                            {});
                        """, quote(status)))
                .collect(Collectors.joining("\n"));
    }

    @Test
    void test() {

        // By this point the database will have been migrated up to the target version
        // with the test data having been applied prior to the target migration running.
        // If it fails before you get here then there is an exception in the migration
        // or the test data.

        // If you get here then you probably want to assert something about the migrated
        // state.

        // no change to row count
        Assertions.assertThat(getTableCount("processor_filter_tracker"))
                .isEqualTo(6);

        // Read the affected rows
        final List<Row> rows = getRows("""
                        select
                            status,
                            message
                        from processor_filter_tracker
                        """,
                rec -> new Row(
                        rec.get("status", int.class),
                        rec.get("message", String.class)));

        Assertions.assertThat(rows)
                .containsExactlyInAnyOrder(
                        new Row(22, null),
                        new Row(22, "some msg"),
                        new Row(10, null),
                        new Row(0, "Searching..."),
                        new Row(0, "Creating..."),
                        new Row(0, null));
    }

    // TODO: 06/02/2023 convert to java record in j17
    private static class Row {

        private final int status;
        private final String msg;

        private Row(final int status, final String msg) {
            this.status = status;
            this.msg = msg;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Row row = (Row) o;
            return status == row.status && Objects.equals(msg, row.msg);
        }

        @Override
        public int hashCode() {
            return Objects.hash(status, msg);
        }

        @Override
        public String toString() {
            return status + "|" + msg;
        }
    }
}
