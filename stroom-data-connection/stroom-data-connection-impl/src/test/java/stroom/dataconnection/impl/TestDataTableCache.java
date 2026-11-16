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

package stroom.dataconnection.impl;

import stroom.cache.impl.CacheManagerImpl;
import stroom.dataconnection.shared.CsvSettings;
import stroom.dataconnection.shared.DataConnectionDoc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestDataTableCache {

    private final CacheManagerImpl cacheManager = new CacheManagerImpl();
    private final DataTableCache cache = new DataTableCache(cacheManager, DataConnectionConfig::new);

    @AfterEach
    void tearDown() {
        cacheManager.close();
    }

    private static DataConnectionDoc doc(final String version, final String data) {
        return DataConnectionDoc.builder()
                .uuid("uuid-1")
                .version(version)
                .csv(CsvSettings.builder()
                        .data(data)
                        .build())
                .build();
    }

    @Test
    void sameVersionAndDataIsParsedOnce() {
        final DataTable first = get(doc("v1", "n\nRed\n"), 10);

        assertThat(get(doc("v1", "n\nRed\n"), 10)).isSameAs(first);
    }

    @Test
    void changedDataWithTheSameVersionIsParsedAgain() {
        // An import keeps the version in the pack, so a re-imported pack with new CSV has the same one.
        get(doc("v1", "n\nRed\n"), 10);

        assertThat(get(doc("v1", "n\nBlue\n"), 10).rows()).containsExactly(List.of("Blue"));
    }

    @Test
    void tablesAreKeptPerRowLimit() {
        get(doc("v1", "n\n1\n2\n3\n"), 1);

        assertThat(get(doc("v1", "n\n1\n2\n3\n"), 10).rows()).hasSize(3);
    }

    @Test
    void unsavedDocumentIsParsedEachTime() {
        final DataTable first = get(doc(null, "n\nRed\n"), 10);

        assertThat(get(doc(null, "n\nRed\n"), 10)).isNotSameAs(first).isEqualTo(first);
    }

    // The CSV connector's key: the data's length and hash, as the cache needs.
    private DataTable get(final DataConnectionDoc doc, final int maxRows) {
        final String data = doc.getCsv().getData();
        return cache.get(doc, List.of(data.length(), data.hashCode()), maxRows,
                () -> CsvReader.parse(doc.getCsv(), maxRows));
    }
}
