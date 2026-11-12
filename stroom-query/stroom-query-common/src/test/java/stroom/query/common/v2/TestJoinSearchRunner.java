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

import stroom.query.api.Column;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.SearchRequest;
import stroom.query.api.SearchTaskProgress;
import stroom.util.shared.ErrorMessage;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/// Searches with joins run end to end: parsed from StroomQL, the joined sources searched as children,
/// their join tables built, and the first source's rows joined into the store that replaces the
/// placeholder. The data sources are in-memory tables served by a fake search starter.
class TestJoinSearchRunner extends AbstractJoinSearchTest {

    @Test
    void leftJoinToAnotherSource() throws InterruptedException {
        final JoinSearchRunner runner = createRunner(1_000_000);
        final Run run = run(runner, """
                from events
                left join hr on UserId = hr.Username
                select UserId, Host, hr.Department""", new FakeSources());

        assertThat(run.awaitSearchStore()).isNotSameAs(run.placeholder());
        assertThat(rows(run.searchStore().get(), "UserId", "Host", "hr.Department")).containsExactlyInAnyOrder(
                List.of("a", "h1", "Sales"),
                List.of("b", "h2", "IT"),
                List.of("c", "h3", ""),
                List.of("a", "h4", "Sales"));
        assertThat(run.searchStore().get().getErrors()).isEmpty();
    }

    @Test
    void joinedSourceIsSearchedForOnlyTheFieldsTheQueryUses() throws InterruptedException {
        final JoinSearchRunner runner = createRunner(1_000_000);
        final Run run = run(runner, """
                from events
                join hr on UserId = hr.Username
                where hr.Department = 'Sales'
                select UserId""", new FakeSources());
        run.awaitSearchStore();

        final SearchRequest hrRequest = startedRequests.stream()
                .filter(request -> "hr".equals(request.getQuery().getDataSource().getName()))
                .findFirst()
                .orElseThrow();
        // The inner join's own condition went to its search.
        assertThat(ExpressionUtil.fields(hrRequest.getQuery().getExpression())).containsExactly("Department");
        assertThat(hrRequest.getResultRequests().getFirst().getMappings().getFirst().getColumns())
                .extracting(Column::getName)
                .containsExactly("Username");
        assertThat(rows(run.searchStore().get(), "UserId")).containsExactlyInAnyOrder(List.of("a"), List.of("a"));
    }

    @Test
    void bracketedFirstSourceIsJoined() throws InterruptedException {
        final JoinSearchRunner runner = createRunner(1_000_000);
        final Run run = run(runner, """
                from (from events group by UserId select UserId, count() as Logons) as e
                left join hr on e.UserId = hr.Username
                select UserId, Logons, hr.Department""", new FakeSources());

        run.awaitSearchStore();
        awaitComplete(run.searchStore().get());
        assertThat(rows(run.searchStore().get(), "UserId", "Logons", "hr.Department")).containsExactlyInAnyOrder(
                List.of("a", "2", "Sales"),
                List.of("b", "1", "IT"),
                List.of("c", "1", ""));
    }

    @Test
    void pollBeforeTheJoinedSourceFinishesSeesAnIncompletePlaceholder() throws InterruptedException {
        final JoinSearchRunner runner = createRunner(1_000_000);
        final FakeSources sources = new FakeSources();
        sources.holdBack("hr");
        final Run run = run(runner, "from events join hr on UserId = hr.Username select UserId", sources);

        sources.awaitStarted("hr");
        assertThat(run.searchStore().get()).isSameAs(run.placeholder());
        assertThat(run.placeholder().isComplete()).isFalse();
        final SearchTaskProgress progress = run.placeholder().getSearchTaskProgress();
        assertThat(progress.getTaskInfo()).isEqualTo("Searching 'hr' to join");

        sources.release("hr");
        assertThat(run.awaitSearchStore()).isNotSameAs(run.placeholder());
        assertThat(rows(run.searchStore().get(), "UserId")).hasSize(3);
    }

    @Test
    void terminatingWhileTheJoinedSourceIsSearchedStopsTheSearch() throws InterruptedException {
        final JoinSearchRunner runner = createRunner(1_000_000);
        final FakeSources sources = new FakeSources();
        sources.holdBack("hr");
        final Run run = run(runner, "from events join hr on UserId = hr.Username select UserId", sources);
        sources.awaitStarted("hr");

        run.placeholder().terminate();

        awaitComplete(run.placeholder());
        assertThat(sources.wasTerminated("hr")).isTrue();
        assertThat(run.searchStore().get()).isSameAs(run.placeholder());
        assertThat(startedRequests)
                .extracting(request -> request.getQuery().getDataSource().getName())
                .doesNotContain("events");
        assertThat(joinTables.get(run.request().getKey())).isEmpty();
    }

    @Test
    void failedJoinedSourceFailsTheSearch() {
        final JoinSearchRunner runner = createRunner(1_000_000);
        final FakeSources sources = new FakeSources();
        sources.fail("hr");
        final Run run = run(runner, "from events join hr on UserId = hr.Username select UserId", sources);

        awaitComplete(run.placeholder());
        assertThat(run.placeholder().getErrors())
                .extracting(ErrorMessage::getMessage)
                .anySatisfy(message -> assertThat(message).contains("'hr': hr is down"))
                .anySatisfy(message -> assertThat(message).contains("The search of 'hr' to join failed"));
        assertThat(joinTables.get(run.request().getKey())).isEmpty();
    }

    @Test
    void joinedSourceWithTooManyRowsFailsTheSearch() {
        final JoinSearchRunner runner = createRunner(2);
        final Run run = run(runner, "from events join hr on UserId = hr.Username select UserId", new FakeSources());

        awaitComplete(run.placeholder());
        assertThat(run.placeholder().getErrors())
                .extracting(ErrorMessage::getMessage)
                .anySatisfy(message -> assertThat(message).contains("'hr' has more than 2 rows"));
    }

    @Test
    void destroyingTheSearchDeletesItsJoinTables() throws InterruptedException {
        final JoinSearchRunner runner = createRunner(1_000_000);
        final Run run = run(runner, "from events join hr on UserId = hr.Username select UserId", new FakeSources());
        final ResultStore searchStore = run.awaitSearchStore();
        assertThat(joinTables.get(run.request().getKey())).isPresent();

        searchStore.destroy();

        assertThat(joinTables.get(run.request().getKey())).isEmpty();
    }

    @Test
    void bracketedJoinKeepsItsOwnLimit() throws InterruptedException {
        final JoinSearchRunner runner = createRunner(1_000_000);
        final Run run = run(runner, """
                from events
                left join (from hr limit 1 select Username, Department) as h on UserId = h.Username
                select UserId, h.Department""", new FakeSources());
        run.awaitSearchStore();

        final SearchRequest hrRequest = startedRequests.stream()
                .filter(request -> "hr".equals(request.getQuery().getDataSource().getName()))
                .findFirst()
                .orElseThrow();
        assertThat(hrRequest.getResultRequests().getFirst().getMappings().getFirst().getMaxResults())
                .containsExactly(1L);
    }

    @Test
    void terminateWhileTheFirstSourceIsStartingTerminatesItsSearch() throws InterruptedException {
        final JoinSearchRunner runner = createRunner(1_000_000);
        final FakeSources sources = new FakeSources();
        final AtomicReference<ResultStore> placeholder = new AtomicReference<>();
        sources.holdBack("events");
        sources.whileStarting("events", () -> placeholder.get().terminate());
        final Run run = run(runner, "from events join hr on UserId = hr.Username select UserId", sources, placeholder);

        run.awaitSearchStore();

        assertThat(sources.wasTerminated("events")).isTrue();
    }

    @Test
    void clearingTheSearchStoreTwiceReleasesItsTablesOnce() throws InterruptedException {
        final JoinSearchRunner runner = createRunner(1_000_000);
        final Run run = run(runner, "from events join hr on UserId = hr.Username select UserId", new FakeSources());
        final ResultStore searchStore = run.awaitSearchStore();
        awaitComplete(searchStore);
        // Another use of the same tables, as a fetch for another node holds them.
        final JoinTables.Registration other = joinTables.acquire(run.request().getKey()).orElseThrow();

        final CoprocessorsImpl coprocessors = (CoprocessorsImpl) searchStore.getCoprocessors();
        coprocessors.clear();
        coprocessors.clear();

        assertThat(joinTables.get(run.request().getKey())).as("the other use still holds them").isPresent();
        joinTables.release(run.request().getKey(), other);
        assertThat(joinTables.get(run.request().getKey())).isEmpty();
    }

    @Test
    void childSearchWarningsAreKept() throws InterruptedException {
        final JoinSearchRunner runner = createRunner(1_000_000);
        final FakeSources sources = new FakeSources();
        sources.warn("hr");
        final Run run = run(runner, "from events join hr on UserId = hr.Username select UserId", sources);

        assertThat(run.awaitSearchStore().getErrors())
                .extracting(ErrorMessage::getMessage)
                .contains("'hr': hr is slow");
    }
}
