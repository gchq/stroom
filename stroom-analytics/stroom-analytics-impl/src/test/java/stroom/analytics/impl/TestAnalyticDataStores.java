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
 * to a cached store. The store dir is keyed on the rule uuid and name, so a rule that has merely
 * been EDITED still uses the same dir and its data must survive; only a rule that has genuinely
 * gone, or been renamed away from its dir, may have its store closed and its dir deleted.
 */
class TestAnalyticDataStores {

    private static final String RULE_UUID = "rule-uuid";
    private static final String RULE_NAME = "rule-name";

    /**
     * The store cache is keyed on the store dir, not on the doc. If it were keyed on the doc then
     * every save would miss, because doc equality includes the version, which is regenerated on
     * every write, and the miss would open a SECOND env on the dir the first one still has open.
     */
    @Test
    void reusesTheStoreOfAnEditedRule(@TempDir final Path tempDir) {
        final AnalyticRuleDoc original = rule("the original description");
        final AnalyticRuleDoc edited = rule("an edited description");
        assertThat(edited).isNotEqualTo(original);

        final AnalyticDataStores dataStores = create(tempDir, ruleStoreReturning(edited));
        final AnalyticDataStore store = new AnalyticDataStore(
                searchRequest(), Mockito.mock(LmdbDataStore.class));
        dataStores.putStoreForTesting(store);

        // A cache miss here would try to open an env, which this instance has no factories for.
        assertThat(dataStores.get(edited)).isSameAs(store);
        assertThat(dataStores.get(original)).isSameAs(store);
    }

    @Test
    void keepsTheStoreOfAnEditedRule(@TempDir final Path tempDir) throws IOException {
        final AnalyticRuleDoc original = rule("the original description");
        final AnalyticRuleDoc edited = rule("an edited description");
        assertThat(edited).isNotEqualTo(original);

        final AnalyticRuleStore ruleStore = ruleStoreReturning(edited);
        final AnalyticDataStores dataStores = create(tempDir, ruleStore);
        final Path storeDir = createStoreDir(tempDir, dataStores, original);
        final LmdbDataStore lmdbDataStore = Mockito.mock(LmdbDataStore.class);
        dataStores.putStoreForTesting(new AnalyticDataStore(searchRequest(), lmdbDataStore));

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
        dataStores.putStoreForTesting(new AnalyticDataStore(searchRequest(), lmdbDataStore));

        dataStores.deleteOldStores();

        // The dir must go, and the store must be closed FIRST so the files are never unlinked
        // with an open env on them.
        assertThat(storeDir).doesNotExist();
        Mockito.verify(lmdbDataStore).close();
    }

    /**
     * A rule we cannot read might still exist, so its store must survive. Its dirs are protected
     * by uuid rather than the whole sweep aborting, so one rule the docstore cannot serve does not
     * stop every deleted rule's store ever being reclaimed.
     */
    @Test
    void protectsARulesStoreIfItCannotBeReadButStillSweeps(@TempDir final Path tempDir) throws IOException {
        final AnalyticRuleDoc unreadable = rule("a rule we cannot read");

        final AnalyticRuleStore ruleStore = Mockito.mock(AnalyticRuleStore.class);
        Mockito.when(ruleStore.list()).thenReturn(List.of(docRef()));
        // Not a DocumentNotFoundException, so we must not conclude the rule has been deleted.
        Mockito.when(ruleStore.readDocument(Mockito.any()))
                .thenThrow(new RuntimeException("Some transient failure"));

        final AnalyticDataStores dataStores = create(tempDir, ruleStore);
        final Path storeDir = createStoreDir(tempDir, dataStores, unreadable);
        final LmdbDataStore lmdbDataStore = Mockito.mock(LmdbDataStore.class);
        dataStores.putStoreForTesting(new AnalyticDataStore(searchRequest(), lmdbDataStore));
        // A dir belonging to some other rule that really has gone.
        final Path otherRulesDir = tempDir.resolve("some_other_rule_uuid___a_name_table");
        Files.createDirectories(otherRulesDir);

        dataStores.deleteOldStores();

        assertThat(storeDir).exists();
        Mockito.verify(lmdbDataStore, Mockito.never()).close();
        assertThat(otherRulesDir).doesNotExist();
    }

    /**
     * Without the rule list we cannot tell a deleted rule from a live one, so nothing may go.
     */
    @Test
    void deletesNothingIfTheRulesCannotBeListed(@TempDir final Path tempDir) throws IOException {
        final AnalyticRuleDoc rule = rule("a rule whose store must survive");

        final AnalyticRuleStore ruleStore = Mockito.mock(AnalyticRuleStore.class);
        Mockito.when(ruleStore.list()).thenThrow(new RuntimeException("Cannot list rules"));

        final AnalyticDataStores dataStores = create(tempDir, ruleStore);
        final Path storeDir = createStoreDir(tempDir, dataStores, rule);
        final LmdbDataStore lmdbDataStore = Mockito.mock(LmdbDataStore.class);
        dataStores.putStoreForTesting(new AnalyticDataStore(searchRequest(), lmdbDataStore));
        final Path otherRulesDir = tempDir.resolve("some_other_rule_uuid___a_name_table");
        Files.createDirectories(otherRulesDir);

        dataStores.deleteOldStores();

        assertThat(storeDir).exists();
        assertThat(otherRulesDir).exists();
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

    /**
     * A rule that exists but whose store dir cannot be worked out must not be treated as deleted.
     * Its dirs are protected by uuid, rather than the whole sweep aborting, so one rule with a
     * broken query cannot stop every other rule's store ever being reclaimed.
     */
    @Test
    void protectsARulesStoreDirIfItCannotBeResolvedButStillSweeps(@TempDir final Path tempDir)
            throws IOException {
        final AnalyticRuleDoc unresolvable = rule("a rule whose search request will not build");

        final AnalyticRuleStore ruleStore = ruleStoreReturning(unresolvable);
        final PathCreator pathCreator = Mockito.mock(PathCreator.class);
        Mockito.when(pathCreator.toAppPath(Mockito.anyString())).thenReturn(tempDir);
        final AnalyticRuleSearchRequestHelper helper = Mockito.mock(AnalyticRuleSearchRequestHelper.class);
        Mockito.when(helper.create(Mockito.any()))
                .thenThrow(new RuntimeException("Cannot build a search request for this rule"));
        final AnalyticDataStores dataStores = new AnalyticDataStores(
                null, pathCreator, ruleStore, helper, AnalyticResultStoreConfig::new,
                null, null, null, null, null, null, null, null);

        final Path storeDir = createStoreDir(tempDir, dataStores, unresolvable);
        // A dir belonging to some other rule that really has gone.
        final Path otherRulesDir = tempDir.resolve("some_other_rule_uuid___a_name_table");
        Files.createDirectories(otherRulesDir);

        dataStores.deleteOldStores();

        assertThat(storeDir).exists();
        assertThat(otherRulesDir).doesNotExist();
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
