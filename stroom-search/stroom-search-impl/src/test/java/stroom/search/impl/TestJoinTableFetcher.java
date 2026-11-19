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

package stroom.search.impl;

import stroom.docref.DocRef;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.query.api.JoinKey;
import stroom.query.api.JoinType;
import stroom.query.api.Query;
import stroom.query.api.QueryJoin;
import stroom.query.api.QueryKey;
import stroom.query.common.v2.JoinTableTransfer;
import stroom.query.common.v2.JoinTables;
import stroom.query.common.v2.JoinTables.Registration;
import stroom.security.api.SecurityContext;
import stroom.util.jersey.WebTargetFactory;

import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.client.WebTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestJoinTableFetcher {

    private static final QueryKey QUERY_KEY = new QueryKey("search");
    private static final Query JOINED_QUERY = Query.builder()
            .dataSource(new DocRef("Index", "events", "events"))
            .joins(List.of(new QueryJoin(
                    JoinType.INNER,
                    "hr",
                    Query.builder().dataSource(new DocRef("DataConnection", "hr", "hr")).build(),
                    null,
                    List.of(new JoinKey("${UserId}", "${hr.Username}")),
                    null)))
            .build();

    private JoinTables joinTables;
    private WebTargetFactory webTargetFactory;
    private Invocation.Builder request;
    private JoinTableFetcher fetcher;

    @BeforeEach
    void setUp() {
        joinTables = Mockito.mock(JoinTables.class);
        Mockito.when(joinTables.acquire(QUERY_KEY)).thenReturn(Optional.empty());
        final NodeInfo nodeInfo = Mockito.mock(NodeInfo.class);
        Mockito.when(nodeInfo.getThisNodeName()).thenReturn("node2");
        final NodeService nodeService = Mockito.mock(NodeService.class);
        Mockito.when(nodeService.getBaseEndpointUrl("node1")).thenReturn("http://node1:8080");
        Mockito.when(nodeService.getBaseEndpointUrl("node2")).thenReturn("http://node2:8080");
        final WebTarget webTarget = Mockito.mock(WebTarget.class);
        request = Mockito.mock(Invocation.Builder.class);
        Mockito.when(webTarget.queryParam(Mockito.anyString(), Mockito.any())).thenReturn(webTarget);
        Mockito.when(webTarget.resolveTemplate(Mockito.anyString(), Mockito.any())).thenReturn(webTarget);
        Mockito.when(webTarget.request(Mockito.anyString())).thenReturn(request);
        webTargetFactory = Mockito.mock(WebTargetFactory.class);
        Mockito.when(webTargetFactory.create(Mockito.anyString())).thenReturn(webTarget);
        fetcher = new JoinTableFetcher(
                nodeInfo, nodeService, webTargetFactory, joinTables, Mockito.mock(SecurityContext.class));
    }

    @Test
    void queryWithoutJoinsFetchesNothing() {
        final Optional<Registration> held = fetcher.fetchIfNeeded(task(Query.builder().build(), "node1"));

        assertThat(held).isEmpty();
        Mockito.verifyNoInteractions(webTargetFactory);
        Mockito.verifyNoInteractions(joinTables);
    }

    @Test
    void tablesThisNodeHoldsAreHeldNotFetched() {
        final Registration registration = Mockito.mock(Registration.class);
        Mockito.when(joinTables.acquire(QUERY_KEY)).thenReturn(Optional.of(registration));

        final Optional<Registration> held = fetcher.fetchIfNeeded(task(JOINED_QUERY, "node1"));

        assertThat(held).containsSame(registration);
        Mockito.verifyNoInteractions(webTargetFactory);
    }

    @Test
    void tablesAreFetchedFromTheSourceNodeAndRegistered() {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        JoinTableTransfer.write(List.of(), outputStream);
        Mockito.when(request.get(InputStream.class)).thenReturn(new ByteArrayInputStream(outputStream.toByteArray()));
        final Registration registration = Mockito.mock(Registration.class);
        Mockito.when(joinTables.registerAndAcquire(
                Mockito.eq(QUERY_KEY), Mockito.eq(JOINED_QUERY), Mockito.eq(List.of()), Mockito.any()))
                .thenReturn(registration);

        final Optional<Registration> held = fetcher.fetchIfNeeded(task(JOINED_QUERY, "node1"));

        assertThat(held).containsSame(registration);
        Mockito.verify(webTargetFactory).create(Mockito.startsWith("http://node1:8080/"));
    }

    @Test
    void searchWithJoinsFromANodeThatDidNotSayWhichItIsFails() {
        assertThatThrownBy(() -> fetcher.fetchIfNeeded(task(JOINED_QUERY, null)))
                .isInstanceOf(SearchException.class)
                .hasMessageContaining("join tables can't be fetched");
    }

    @Test
    void failedFetchFailsTheNodeSearchNamingTheNode() {
        Mockito.when(request.get(InputStream.class)).thenThrow(new ProcessingException("connection refused"));

        assertThatThrownBy(() -> fetcher.fetchIfNeeded(task(JOINED_QUERY, "node1")))
                .isInstanceOf(SearchException.class)
                .hasMessageContaining("from node node1")
                .hasMessageContaining("connection refused");
        Mockito.verify(joinTables, Mockito.never()).registerAndAcquire(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    private static NodeSearchTask task(final Query query, final String sourceNode) {
        return new NodeSearchTask(
                NodeSearchTaskType.LUCENE,
                null,
                "Cluster Search",
                null,
                QUERY_KEY,
                query,
                List.of(),
                null,
                List.of(),
                sourceNode);
    }
}
