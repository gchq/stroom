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

import stroom.query.api.QueryKey;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.util.io.ByteSize;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestJoinTableTransfer {

    private static final QueryKey QUERY_KEY = new QueryKey("search");

    @TempDir
    private Path tempDir;
    private JoinTables joinTables;
    private final List<JoinTable> created = new ArrayList<>();

    @BeforeEach
    void setUp() {
        final SearchResultStoreConfig config = new SearchResultStoreConfig(
                null, null, null, null, null, null, null,
                ResultStoreLmdbConfig.builder()
                        .localDir("search_results")
                        .maxStoreSize(ByteSize.ofMebibytes(100))
                        .build(),
                null, null, null);
        joinTables = new JoinTables(JoinTestHelper.createLmdbEnvDirFactory(tempDir), () -> config);
    }

    @AfterEach
    void tearDown() {
        created.forEach(JoinTable::close);
    }

    @Test
    void tablesAreCopiedWithTheirFieldsAndRows() {
        final JoinTable hr = table("hr", List.of("Username", "Department"));
        hr.add("a", JoinTestHelper.row("a", "Sales"));
        hr.add("a", JoinTestHelper.row("a", "Support"));
        hr.add("b", new Val[]{ValLong.create(7), ValNull.INSTANCE});
        hr.complete();
        final JoinTable empty = table("depts", List.of("Name"));
        empty.complete();

        final List<JoinTable> copies = copy(List.of(hr, empty));

        assertThat(copies).extracting(JoinTable::getName).containsExactly("hr", "depts");
        assertThat(copies.getFirst().getFields()).containsExactly("Username", "Department");
        assertThat(rows(copies.getFirst(), "a")).containsExactly(List.of("a", "Sales"), List.of("a", "Support"));
        assertThat(rows(copies.getFirst(), "b")).containsExactly(Arrays.asList("7", null));
        assertThat(copies.get(1).getRowCount()).isZero();
    }

    @Test
    void noTablesCopyToNoTables() {
        assertThat(copy(List.of())).isEmpty();
    }

    @Test
    void cutShortStreamFailsAndLeavesNoTables() throws IOException {
        final JoinTable hr = table("hr", List.of("Username"));
        for (int i = 0; i < 100; i++) {
            hr.add("k" + i, JoinTestHelper.row("k" + i));
        }
        hr.complete();
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JoinTableTransfer.write(List.of(hr), outputStream);
        final byte[] bytes = outputStream.toByteArray();
        final long filesBefore = countLmdbFiles();

        assertThatThrownBy(() -> JoinTableTransfer.read(
                new ByteArrayInputStream(Arrays.copyOf(bytes, bytes.length / 2)), QUERY_KEY, joinTables))
                .isInstanceOf(RuntimeException.class);
        assertThat(countLmdbFiles()).isEqualTo(filesBefore);
    }

    private JoinTable table(final String name, final List<String> fields) {
        final JoinTable table = joinTables.create(QUERY_KEY, name, fields);
        created.add(table);
        return table;
    }

    private List<JoinTable> copy(final List<JoinTable> tables) {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JoinTableTransfer.write(tables, outputStream);
        final List<JoinTable> copies = JoinTableTransfer.read(
                new ByteArrayInputStream(outputStream.toByteArray()), QUERY_KEY, joinTables);
        created.addAll(copies);
        return copies;
    }

    private static List<List<String>> rows(final JoinTable table, final String key) {
        final List<List<String>> rows = new ArrayList<>();
        table.forEachMatch(key, row -> rows.add(Stream.of(row).map(Val::toString).toList()));
        return rows;
    }

    private long countLmdbFiles() throws IOException {
        try (final Stream<Path> files = Files.walk(tempDir)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".mdb")).count();
        }
    }
}
