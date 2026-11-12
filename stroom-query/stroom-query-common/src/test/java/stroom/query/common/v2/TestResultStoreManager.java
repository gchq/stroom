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

import stroom.query.api.DestroyReason;
import stroom.query.api.QueryKey;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchResponse;
import stroom.query.common.v2.ResultStoreManager.RequestAndStore;
import stroom.security.mock.MockSecurityContext;
import stroom.security.user.api.UserRefLookup;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.SimpleTaskContextFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Searches with joins run through the [ResultStoreManager]: the placeholder polls see, the store
/// that takes its place, and terminating and destroying the search by its key.
class TestResultStoreManager extends AbstractJoinSearchTest {

    private FakeSources sources;
    private ResultStoreManager resultStoreManager;

    @BeforeEach
    void createResultStoreManager() {
        sources = new FakeSources();
        final JoinSearchRunner runner = createRunner(1_000_000);
        final SearchProvider searchProvider = Mockito.mock(SearchProvider.class);
        Mockito.when(searchProvider.getTimeField(Mockito.any())).thenReturn(Optional.empty());
        Mockito.when(searchProvider.createResultStore(Mockito.any())).thenAnswer(invocation -> {
            final SearchRequest request = invocation.getArgument(0);
            startedRequests.add(request);
            final ResultStore store = sources.start(request);
            createdStores.add(store);
            return store;
        });
        final SearchProviderRegistry searchProviderRegistry = dataSourceRef -> Optional.of(searchProvider);
        final ExecutorProvider executorProvider = Mockito.mock(ExecutorProvider.class);
        Mockito.when(executorProvider.get()).thenReturn(executor);
        resultStoreManager = new ResultStoreManager(
                new SimpleTaskContextFactory(),
                MockSecurityContext.getInstance(),
                executorProvider,
                searchProviderRegistry,
                Mockito.mock(UserRefLookup.class),
                () -> runner);
    }

    @Test
    void joinSearchShowsAPlaceholderUntilTheFirstSourceIsSearched() throws InterruptedException {
        sources.holdBack("hr");
        final RequestAndStore requestAndStore = resultStoreManager.getResultStore(
                parse("from events left join hr on UserId = hr.Username select UserId, hr.Department"));
        final QueryKey queryKey = requestAndStore.searchRequest().getKey();
        sources.awaitStarted("hr");

        assertThat(resultStoreManager.getIfPresent(queryKey)).containsSame(requestAndStore.resultStore());
        // A poll, as the query editor polls: incremental, so it returns what there is so far.
        final SearchResponse response = resultStoreManager.search(requestAndStore.searchRequest()
                .copy()
                .key(queryKey)
                .incremental(true)
                .build());
        assertThat(response.complete()).isFalse();
        assertThat(response.getErrorMessages()).isNullOrEmpty();

        sources.release("hr");
        final ResultStore searchStore = resultStoreManager.getSearchStore(requestAndStore);

        assertThat(searchStore).isNotSameAs(requestAndStore.resultStore());
        assertThat(resultStoreManager.getIfPresent(queryKey)).containsSame(searchStore);
        assertThat(rows(searchStore, "UserId", "hr.Department")).containsExactlyInAnyOrder(
                List.of("a", "Sales"),
                List.of("b", "IT"),
                List.of("c", ""),
                List.of("a", "Sales"));
    }

    @Test
    void terminatingTheSearchTerminatesItsJoinedSourcesSearch() throws InterruptedException {
        sources.holdBack("hr");
        final RequestAndStore requestAndStore = resultStoreManager.getResultStore(
                parse("from events join hr on UserId = hr.Username select UserId"));
        sources.awaitStarted("hr");

        resultStoreManager.terminate(requestAndStore.searchRequest().getKey());

        final ResultStore store = resultStoreManager.getSearchStore(requestAndStore);
        assertThat(store).isSameAs(requestAndStore.resultStore());
        assertThat(store.isComplete()).isTrue();
        assertThat(sources.wasTerminated("hr")).isTrue();
    }

    @Test
    void destroyingTheSearchBeforeTheFirstSourceIsSearchedLeavesNothingBehind() throws InterruptedException {
        sources.holdBack("hr");
        final RequestAndStore requestAndStore = resultStoreManager.getResultStore(
                parse("from events join hr on UserId = hr.Username select UserId"));
        final QueryKey queryKey = requestAndStore.searchRequest().getKey();
        sources.awaitStarted("hr");

        resultStoreManager.destroy(queryKey, DestroyReason.MANUAL);

        awaitComplete(requestAndStore.resultStore());
        assertThat(sources.wasTerminated("hr")).isTrue();
        assertThat(resultStoreManager.getIfPresent(queryKey)).isEmpty();
        assertThat(joinTables.get(queryKey)).isEmpty();
    }

    @Test
    void destroyingTheSearchDeletesItsStoreAndJoinTables() throws InterruptedException {
        final RequestAndStore requestAndStore = resultStoreManager.getResultStore(
                parse("from events join hr on UserId = hr.Username select UserId"));
        final QueryKey queryKey = requestAndStore.searchRequest().getKey();
        final ResultStore searchStore = resultStoreManager.getSearchStore(requestAndStore);
        awaitComplete(searchStore);
        assertThat(joinTables.get(queryKey)).isPresent();

        resultStoreManager.destroy(queryKey, DestroyReason.MANUAL);

        assertThat(resultStoreManager.getIfPresent(queryKey)).isEmpty();
        assertThat(joinTables.get(queryKey)).isEmpty();
    }

    @Test
    void failedJoinSearchIsAnErrorForCallersThatReadTheStoreThemselves() {
        sources.fail("hr");
        final RequestAndStore requestAndStore = resultStoreManager.getResultStore(
                parse("from events join hr on UserId = hr.Username select UserId"));

        assertThatThrownBy(() -> resultStoreManager.getSearchStore(requestAndStore))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("The search of 'hr' to join failed");
    }

    @Test
    void searchWithoutJoinsHasNoPlaceholder() throws InterruptedException {
        final RequestAndStore requestAndStore = resultStoreManager.getResultStore(
                parse("from events select UserId"));

        assertThat(resultStoreManager.getSearchStore(requestAndStore)).isSameAs(requestAndStore.resultStore());
        assertThat(rows(requestAndStore.resultStore(), "UserId")).hasSize(4);
    }
}
