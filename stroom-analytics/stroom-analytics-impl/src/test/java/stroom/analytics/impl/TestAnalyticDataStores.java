/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.analytics.impl;

import stroom.analytics.impl.AnalyticDataStores.AnalyticDataStore;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.docref.DocRef;
import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequest;
import stroom.query.common.v2.AnalyticResultStoreConfig;
import stroom.query.common.v2.LmdbDataStore;
import stroom.util.io.PathCreator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers what {@link AnalyticDataStores#deleteOldStores()} does, and deliberately does not do,
 * to a cached store. The cache is keyed on the whole {@link AnalyticRuleDoc}, which has value
 * based equality including a version that changes on every save, so an EDITED rule reaches the
 * same branch as a deleted one. Its store dir is keyed on the rule uuid and name though, so it
 * is still in use and must survive.
 */
class TestAnalyticDataStores {

    private static final String RULE_UUID = "rule-uuid";
    private static final String RULE_NAME = "rule-name";

    @Test
    void keepsTheStoreOfAnEditedRule(@TempDir final Path tempDir) throws IOException {
        final AnalyticRuleDoc original = rule("the original description");
        final AnalyticRuleDoc edited = rule("an edited description");
        assertThat(edited).isNotEqualTo(original);

        final AnalyticRuleStore ruleStore = ruleStoreReturning(edited);
        final AnalyticDataStores dataStores = create(tempDir, ruleStore);
        final Path storeDir = createStoreDir(tempDir, dataStores, original);
        final LmdbDataStore lmdbDataStore = Mockito.mock(LmdbDataStore.class);
        dataStores.putStoreForTesting(original, new AnalyticDataStore(searchRequest(), lmdbDataStore));

        dataStores.deleteOldStores();

        // The rule still exists and still uses this dir, so its data must survive...
        assertThat(storeDir).exists();
        // ...and its store must not be closed, or an in flight search on it would silently
        // return no rows.
        Mockito.verify(lmdbDataStore, Mockito.never()).close();
        Mockito.verify(lmdbDataStore, Mockito.never()).clear();
    }

    @Test
    void closesAndDeletesTheStoreOfADeletedRule(@TempDir final Path tempDir) throws IOException {
        final AnalyticRuleDoc deleted = rule("a rule that is about to be deleted");

        // No rules left at all, so this one has genuinely gone.
        final AnalyticRuleStore ruleStore = Mockito.mock(AnalyticRuleStore.class);
        Mockito.when(ruleStore.list()).thenReturn(Collections.emptyList());

        final AnalyticDataStores dataStores = create(tempDir, ruleStore);
        final Path storeDir = createStoreDir(tempDir, dataStores, deleted);
        final LmdbDataStore lmdbDataStore = Mockito.mock(LmdbDataStore.class);
        dataStores.putStoreForTesting(deleted, new AnalyticDataStore(searchRequest(), lmdbDataStore));

        dataStores.deleteOldStores();

        // The dir must go, and the store must be closed FIRST so the files are never unlinked
        // with an open env on them.
        assertThat(storeDir).doesNotExist();
        Mockito.verify(lmdbDataStore).close();
    }

    @Test
    void deletesNothingIfARuleCannotBeRead(@TempDir final Path tempDir) throws IOException {
        final AnalyticRuleDoc unreadable = rule("a rule we cannot read");

        final AnalyticRuleStore ruleStore = Mockito.mock(AnalyticRuleStore.class);
        Mockito.when(ruleStore.list()).thenReturn(List.of(docRef()));
        // Not a DocumentNotFoundException, so we must not conclude the rule has been deleted.
        Mockito.when(ruleStore.readDocument(Mockito.any()))
                .thenThrow(new RuntimeException("Some transient failure"));

        final AnalyticDataStores dataStores = create(tempDir, ruleStore);
        final Path storeDir = createStoreDir(tempDir, dataStores, unreadable);
        final LmdbDataStore lmdbDataStore = Mockito.mock(LmdbDataStore.class);
        dataStores.putStoreForTesting(unreadable, new AnalyticDataStore(searchRequest(), lmdbDataStore));

        dataStores.deleteOldStores();

        assertThat(storeDir).exists();
        Mockito.verify(lmdbDataStore, Mockito.never()).close();
    }

    private AnalyticRuleDoc rule(final String description) {
        return AnalyticRuleDoc
                .builder()
                .uuid(RULE_UUID)
                .name(RULE_NAME)
                .description(description)
                .build();
    }

    private DocRef docRef() {
        return DocRef.builder().type(AnalyticRuleDoc.TYPE).uuid(RULE_UUID).name(RULE_NAME).build();
    }

    private SearchRequest searchRequest() {
        final SearchRequest searchRequest = Mockito.mock(SearchRequest.class);
        Mockito.when(searchRequest.getKey()).thenReturn(new QueryKey(RULE_UUID + " - " + RULE_NAME));
        Mockito.when(searchRequest.getResultRequests()).thenReturn(Collections.emptyList());
        return searchRequest;
    }

    private AnalyticRuleStore ruleStoreReturning(final AnalyticRuleDoc doc) {
        final AnalyticRuleStore ruleStore = Mockito.mock(AnalyticRuleStore.class);
        Mockito.when(ruleStore.list()).thenReturn(List.of(docRef()));
        Mockito.when(ruleStore.readDocument(Mockito.any())).thenReturn(doc);
        return ruleStore;
    }

    /**
     * Creates the dir on disk that the store for the supplied rule would use, so the sweep has
     * something real to keep or delete.
     */
    private Path createStoreDir(final Path tempDir,
                                final AnalyticDataStores dataStores,
                                final AnalyticRuleDoc doc) throws IOException {
        final String dirName = (RULE_UUID + " - " + RULE_NAME + "_null").replaceAll("[^A-Za-z0-9]", "_");
        final Path dir = tempDir.resolve(dirName);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("data.mdb"), "analytic data for " + doc.getUuid());
        return dir;
    }

    private AnalyticDataStores create(final Path tempDir, final AnalyticRuleStore ruleStore) {
        final PathCreator pathCreator = Mockito.mock(PathCreator.class);
        Mockito.when(pathCreator.toAppPath(Mockito.anyString())).thenReturn(tempDir);

        final AnalyticRuleSearchRequestHelper helper = Mockito.mock(AnalyticRuleSearchRequestHelper.class);
        Mockito.when(helper.create(Mockito.any())).thenAnswer(invocation -> searchRequest());

        return new AnalyticDataStores(
                null,
                pathCreator,
                ruleStore,
                helper,
                AnalyticResultStoreConfig::new,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
