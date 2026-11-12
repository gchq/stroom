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

package stroom.query.common.v2;

import stroom.lmdb2.LmdbEnv;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestJoinTable {

    @TempDir
    private Path tempDir;

    @Test
    void rowsAreFoundByKeyInTheOrderAdded() {
        try (final JoinTable table = createTable(JoinLimits.DEFAULT)) {
            table.add("a", JoinTestHelper.row("a", "1"));
            table.add("b", JoinTestHelper.row("b", "2"));
            table.add("a", JoinTestHelper.row("a", "3"));
            table.complete();

            assertThat(matches(table, "a")).containsExactly(List.of("a", "1"), List.of("a", "3"));
            assertThat(matches(table, "b")).containsExactly(List.of("b", "2"));
            assertThat(matches(table, "c")).isEmpty();
            assertThat(table.hasMatch("a")).isTrue();
            assertThat(table.hasMatch("c")).isFalse();
            assertThat(table.getRowCount()).isEqualTo(3);
        }
    }

    @Test
    void rowWithNoKeyIsNotHeldAndNoKeyMatchesNothing() {
        try (final JoinTable table = createTable(JoinLimits.DEFAULT)) {
            table.add(null, JoinTestHelper.row("x"));
            table.complete();

            assertThat(table.getRowCount()).isZero();
            assertThat(matches(table, null)).isEmpty();
            assertThat(table.hasMatch(null)).isFalse();
        }
    }

    @Test
    void keysAreComparedExactly() {
        try (final JoinTable table = createTable(JoinLimits.DEFAULT)) {
            table.add("Bob", JoinTestHelper.row("Bob"));
            table.complete();

            assertThat(table.hasMatch("bob")).isFalse();
            assertThat(table.hasMatch("Bob ")).isFalse();
        }
    }

    @Test
    void longKeysAndValuesOfAnyTypeAreHeld() {
        final String longKey = "k".repeat(5_000);
        try (final JoinTable table = createTable(JoinLimits.DEFAULT)) {
            table.add(longKey, new Val[]{ValLong.create(42), null});
            table.complete();

            final List<Val[]> rows = new ArrayList<>();
            table.forEachMatch(longKey, rows::add);
            assertThat(rows).hasSize(1);
            assertThat(rows.getFirst()[0]).isEqualTo(ValLong.create(42));
        }
    }

    @Test
    void manyRowsAcrossCommits() {
        try (final JoinTable table = createTable(JoinLimits.DEFAULT)) {
            for (int i = 0; i < 25_000; i++) {
                table.add("k" + (i % 10), JoinTestHelper.row(String.valueOf(i)));
            }
            table.complete();

            assertThat(matches(table, "k3")).hasSize(2_500);
        }
    }

    @Test
    void rowLimitIsEnforced() {
        try (final JoinTable table = createTable(new JoinLimits(2, Long.MAX_VALUE))) {
            table.add("a", JoinTestHelper.row("1"));
            table.add("b", JoinTestHelper.row("2"));

            assertThatThrownBy(() -> table.add("c", JoinTestHelper.row("3")))
                    .isInstanceOf(JoinTableFullException.class)
                    .hasMessageContaining("'hr' has more than 2 rows");
            // The refused row is not counted.
            assertThat(table.getRowCount()).isEqualTo(2);
        }
    }

    @Test
    void failedLookupIsAnErrorNotNoMatch() {
        final JoinTable table = createTable(JoinLimits.DEFAULT);
        table.add("a", JoinTestHelper.row("1"));
        table.complete();
        table.close();

        assertThatThrownBy(() -> table.hasMatch("a")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> table.forEachMatch("a", row -> {
        })).isInstanceOf(RuntimeException.class);
    }

    @Test
    void lookupsFromManyThreadsAtOnceAllSucceed() throws Exception {
        try (final JoinTable table = createTable(JoinLimits.DEFAULT)) {
            for (int i = 0; i < 1_000; i++) {
                table.add("k" + i, JoinTestHelper.row(String.valueOf(i)));
            }
            table.complete();

            final List<CompletableFuture<Integer>> futures = new ArrayList<>();
            try (final ExecutorService executor = Executors.newFixedThreadPool(16)) {
                for (int thread = 0; thread < 16; thread++) {
                    futures.add(CompletableFuture.supplyAsync(() -> {
                        int found = 0;
                        for (int i = 0; i < 1_000; i++) {
                            found += table.forEachMatch("k" + i, row -> {
                            });
                        }
                        return found;
                    }, executor));
                }
                for (final CompletableFuture<Integer> future : futures) {
                    assertThat(future.get()).isEqualTo(1_000);
                }
            }
        }
    }

    @Test
    void byteLimitIsEnforced() {
        try (final JoinTable table = createTable(new JoinLimits(Long.MAX_VALUE, 100))) {
            assertThatThrownBy(() -> table.add("a", JoinTestHelper.row("x".repeat(200))))
                    .isInstanceOf(JoinTableFullException.class);
        }
    }

    @Test
    void tableMustBeCompleteToBeReadAndIsThenClosedToAdding() {
        try (final JoinTable table = createTable(JoinLimits.DEFAULT)) {
            assertThatThrownBy(() -> table.hasMatch("a")).isInstanceOf(IllegalStateException.class);
            table.complete();
            assertThatThrownBy(() -> table.add("a", JoinTestHelper.row("1")))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void closingDeletesTheTable() throws IOException {
        final JoinTable table = createTable(JoinLimits.DEFAULT);
        table.add("a", JoinTestHelper.row("1"));
        table.complete();
        assertThat(countFiles()).isPositive();

        table.close();

        assertThat(countFiles()).isZero();
    }

    private long countFiles() throws IOException {
        try (final Stream<Path> files = Files.walk(tempDir)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString().endsWith(".mdb"))
                    .count();
        }
    }

    private JoinTable createTable(final JoinLimits limits) {
        final LmdbEnv.Builder builder = JoinTestHelper.createLmdbEnvBuilder(tempDir);
        return new JoinTable("hr", List.of("Username", "Department"), builder, limits);
    }

    private static List<List<String>> matches(final JoinTable table, final String key) {
        final List<List<String>> rows = new ArrayList<>();
        table.forEachMatch(key, row -> rows.add(Stream.of(row).map(Val::toString).toList()));
        return rows;
    }
}
