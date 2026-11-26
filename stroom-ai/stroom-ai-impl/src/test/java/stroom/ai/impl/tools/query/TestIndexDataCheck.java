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

package stroom.ai.impl.tools.query;

import stroom.ai.api.ElasticEsql;
import stroom.ai.impl.tools.query.IndexDataCheck.Checked;
import stroom.ai.impl.tools.query.IndexDataCheck.Holding;
import stroom.ai.impl.tools.query.IndexDataCheck.State;
import stroom.docref.DocRef;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexResource;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.LuceneIndexDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskTerminatedException;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Whether an index holds data: an Elastic Index by a count in the period, a Lucene index by its shards.
class TestIndexDataCheck {

    private static final DocRef ELASTIC = new DocRef(ElasticIndexDoc.TYPE, "elastic-uuid", "Proxy");
    private static final DocRef LUCENE = new DocRef(LuceneIndexDoc.TYPE, "lucene-uuid", "Old events");
    private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-08T00:00:00Z");

    private final ElasticEsql esql = Mockito.mock(ElasticEsql.class);
    private final IndexResource indexResource = Mockito.mock(IndexResource.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final IndexDataCheck check = new IndexDataCheck(() -> esql, () -> indexResource, securityContext);

    @BeforeEach
    void setUp() {
        Mockito.when(securityContext.asProcessingUserResult(Mockito.any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(0)).get());
    }

    @Test
    void anElasticIndexIsCountedInThePeriod() {
        count(3L);

        final Holding holding = check.check(ELASTIC, FROM, TO, null);

        assertThat(holding.state()).isEqualTo(State.DATA);
        final ArgumentCaptor<ElasticEsql.Request> sent = ArgumentCaptor.forClass(ElasticEsql.Request.class);
        Mockito.verify(esql).query(sent.capture());
        assertThat(sent.getValue().pipeline()).isEqualTo("| STATS n = COUNT(*)");
        assertThat(sent.getValue().timeRange().getFrom()).isEqualTo(FROM.toString());
        assertThat(sent.getValue().timeRange().getTo()).isEqualTo(TO.toString());
    }

    @Test
    void anElasticIndexWithNoDocumentsInThePeriodHoldsNone() {
        count(0L);

        assertThat(check.check(ELASTIC, FROM, TO, null))
                .isEqualTo(new Holding(ELASTIC, State.EMPTY, IndexDataCheck.NO_DOCUMENTS_IN_PERIOD));
        assertThat(check.check(ELASTIC, null, null, null))
                .isEqualTo(new Holding(ELASTIC, State.EMPTY, IndexDataCheck.NO_DOCUMENTS));
    }

    @Test
    void anElasticIndexWhoseNameStandsForNothingHoldsNone() {
        Mockito.when(esql.query(Mockito.any())).thenThrow(new ElasticEsql.Refusal(ElasticEsql.Reason.REFUSED,
                "Elasticsearch refused the query: Found 1 problem\nline 1:1: Unknown index [empty-events]"));

        assertThat(check.check(ELASTIC, FROM, TO, null).state()).isEqualTo(State.EMPTY);
        assertThat(check.check(ELASTIC, FROM, TO, null).why()).isEqualTo(IndexDataCheck.NO_INDEX);
    }

    @Test
    void anElasticIndexWithNoTimeFieldIsCountedWhole() {
        Mockito.when(esql.query(Mockito.argThat(request -> request != null && request.timeRange() != null)))
                .thenThrow(new ElasticEsql.Refusal(ElasticEsql.Reason.INVALID, "no time field"));
        Mockito.when(esql.query(Mockito.argThat(request -> request != null && request.timeRange() == null)))
                .thenReturn(result(0L));

        assertThat(check.check(ELASTIC, FROM, TO, null))
                .isEqualTo(new Holding(ELASTIC, State.EMPTY, IndexDataCheck.NO_DOCUMENTS));
    }

    @Test
    void anElasticCountThatFailsKeepsTheIndexSayingWhy() {
        Mockito.when(esql.query(Mockito.any())).thenThrow(new ElasticEsql.Refusal(ElasticEsql.Reason.CLUSTER_ERROR,
                "The cluster couldn't be reached"));

        final Checked checked = check.checkAll(List.of(ELASTIC), FROM, TO, null);

        assertThat(checked.kept()).containsExactly(ELASTIC);
        assertThat(checked.empty()).isEmpty();
        assertThat(checked.notes()).isEqualTo("; couldn't check for data: Proxy (The cluster couldn't be reached)");
    }

    @Test
    void anElasticCountWithNoAnswerKeepsTheIndex() {
        assertThat(check.check(ELASTIC, FROM, TO, null).state()).isEqualTo(State.UNKNOWN);
    }

    @Test
    void stoppingStopsTheChecks() {
        Mockito.when(esql.query(Mockito.any())).thenThrow(new ElasticEsql.Refusal(ElasticEsql.Reason.STOPPED,
                "stopped"));

        assertThatThrownBy(() -> check.check(ELASTIC, FROM, TO, null)).isInstanceOf(TaskTerminatedException.class);
        assertThatThrownBy(() -> check.check(ELASTIC, FROM, TO, () -> true))
                .isInstanceOf(TaskTerminatedException.class);
    }

    @Test
    void aLuceneIndexWithNoShardsHoldsNone() {
        shards();

        assertThat(check.check(LUCENE, FROM, TO, null))
                .isEqualTo(new Holding(LUCENE, State.EMPTY, IndexDataCheck.NO_SHARDS));
        final ArgumentCaptor<FindIndexShardCriteria> criteria = ArgumentCaptor.forClass(FindIndexShardCriteria.class);
        Mockito.verify(indexResource).find(criteria.capture());
        assertThat(criteria.getValue().getIndexUuidSet().getSet()).containsExactly(LUCENE.getUuid());
    }

    @Test
    void aLuceneIndexWhoseShardsAreEmptyOrDeletedHoldsNone() {
        shards(shard(0, null, null, IndexShardStatus.CLOSED), shard(10, null, null, IndexShardStatus.DELETED));

        assertThat(check.check(LUCENE, FROM, TO, null))
                .isEqualTo(new Holding(LUCENE, State.EMPTY, IndexDataCheck.NO_DOCUMENTS_IN_PERIOD));
        assertThat(check.check(LUCENE, null, null, null))
                .isEqualTo(new Holding(LUCENE, State.EMPTY, IndexDataCheck.NO_DOCUMENTS));
    }

    @Test
    void aLuceneIndexWhoseShardsLieOutsideThePeriodHoldsNoneInIt() {
        final long day = 86_400_000L;
        // A partition the day before the period, and one starting as it ends.
        shards(shard(5, FROM.toEpochMilli() - day, FROM.toEpochMilli(), IndexShardStatus.CLOSED),
                shard(5, TO.toEpochMilli(), TO.toEpochMilli() + day, IndexShardStatus.CLOSED));

        assertThat(check.check(LUCENE, FROM, TO, null))
                .isEqualTo(new Holding(LUCENE, State.EMPTY, IndexDataCheck.NO_SHARDS_IN_PERIOD));
        assertThat(check.check(LUCENE, null, null, null).state()).isEqualTo(State.DATA);
    }

    @Test
    void aLuceneIndexWithDocumentsInAShardOfThePeriodHoldsData() {
        shards(shard(0, null, null, IndexShardStatus.CLOSED), shard(7, FROM.toEpochMilli(),
                FROM.toEpochMilli() + 1, IndexShardStatus.OPEN));

        assertThat(check.check(LUCENE, FROM, TO, null).state()).isEqualTo(State.DATA);
    }

    @Test
    void shardsThatCantBeListedKeepTheIndex() {
        Mockito.when(indexResource.find(Mockito.any())).thenThrow(new RuntimeException("database down"));

        assertThat(check.check(LUCENE, FROM, TO, null))
                .isEqualTo(new Holding(LUCENE, State.UNKNOWN, "database down"));
    }

    @Test
    void anotherKindOfDataSourceIsNotChecked() {
        final DocRef view = new DocRef("View", "view-uuid", "A view");

        final Checked checked = check.checkAll(List.of(view), FROM, TO, null);

        assertThat(checked.kept()).containsExactly(view);
        assertThat(checked.unchecked()).hasSize(1);
        Mockito.verifyNoInteractions(esql, indexResource);
    }

    @Test
    void checkingAllKeepsTheOrderAndSaysWhatWasLeftOut() {
        final DocRef empty = new DocRef(ElasticIndexDoc.TYPE, "empty-uuid", "Empty");
        Mockito.when(esql.query(Mockito.argThat(request -> request != null && ELASTIC.equals(request.index()))))
                .thenReturn(result(9L));
        Mockito.when(esql.query(Mockito.argThat(request -> request != null && empty.equals(request.index()))))
                .thenReturn(result(0L));
        shards();

        final Checked checked = check.checkAll(List.of(empty, ELASTIC, LUCENE), FROM, TO, null);

        assertThat(checked.kept()).containsExactly(ELASTIC);
        assertThat(checked.notes()).isEqualTo("; left out, holding no data: Empty (no documents in the period); "
                                              + "Old events (no shards)");
    }

    private void count(final long n) {
        Mockito.when(esql.query(Mockito.any())).thenReturn(result(n));
    }

    private static ElasticEsql.Result result(final long n) {
        return new ElasticEsql.Result("FROM x | STATS n = COUNT(*)", "x", List.of(new ElasticEsql.Column("n",
                "long")), List.of(List.of(n)), 1, 1, null, false, false, null, null, "9.5.0", List.of());
    }

    private void shards(final IndexShard... shards) {
        Mockito.when(indexResource.find(Mockito.any())).thenReturn(new ResultPage<>(new ArrayList<>(List.of(
                shards))));
    }

    private static IndexShard shard(final int documents,
                                    final Long from,
                                    final Long to,
                                    final IndexShardStatus status) {
        return IndexShard.builder()
                .id((long) documents)
                .indexUuid(LUCENE.getUuid())
                .documentCount(documents)
                .partitionFromTime(from)
                .partitionToTime(to)
                .status(status)
                .build();
    }
}
