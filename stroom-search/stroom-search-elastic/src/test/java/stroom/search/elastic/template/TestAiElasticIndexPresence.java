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

package stroom.search.elastic.template;

import stroom.ai.api.ElasticIndexPresence.Presence;
import stroom.ai.api.ElasticIndexPresence.State;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.security.api.SecurityContext;
import stroom.util.shared.PermissionException;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestAiElasticIndexPresence {

    private static final DocRef CLUSTER_REF = ElasticClusterDoc.buildDocRef()
            .uuid("cluster-uuid")
            .name("ES_DEV")
            .build();
    private static final ElasticConnectionConfig CONNECTION = new ElasticConnectionConfig();

    @Mock
    private ElasticIndexStore elasticIndexStore;
    @Mock
    private ElasticClusterStore elasticClusterStore;
    @Mock
    private DocumentResourceHelper documentResourceHelper;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private ElasticClientCache elasticClientCache;
    @Mock
    private ElasticIndexTemplateService elasticIndexTemplateService;

    private AiElasticIndexPresence elasticIndexPresence;

    @BeforeEach
    void setUp() {
        elasticIndexPresence = new AiElasticIndexPresence(new ElasticIndexTemplateReader(
                () -> elasticIndexStore,
                () -> elasticClusterStore,
                () -> documentResourceHelper,
                () -> securityContext,
                () -> elasticClientCache,
                () -> elasticIndexTemplateService));
    }

    @Test
    void testLooksTheNameUpWithTheClusterDocsConnection() throws IOException {
        givenClusterDoc();
        givenClient();
        final Presence found = new Presence(State.PRESENT, List.of("logs-app"), List.of(), List.of(), 12L, null);
        when(elasticIndexTemplateService.presence(eq("logs-app"), any())).thenReturn(found);

        assertThat(elasticIndexPresence.check(CLUSTER_REF, " logs-app ")).isEqualTo(found);
        // Read as the caller, Read checked, not as the processing user
        verifyNoInteractions(securityContext);
    }

    @Test
    void testUnknownWhenTheClusterDocCantBeRead() {
        when(documentResourceHelper.read(elasticClusterStore, CLUSTER_REF))
                .thenThrow(new PermissionException(null, "You do not have permission"));

        final Presence presence = elasticIndexPresence.check(CLUSTER_REF, "logs-app");

        assertThat(presence.state()).isEqualTo(State.UNKNOWN);
        assertThat(presence.message()).isEqualTo("The Elastic Cluster doc 'ES_DEV' can't be read by this user");
        verifyNoInteractions(elasticClientCache);
    }

    @Test
    void testUnknownWhenTheClusterDocIsMissing() {
        when(documentResourceHelper.read(elasticClusterStore, CLUSTER_REF))
                .thenThrow(new DocumentNotFoundException(CLUSTER_REF));

        assertThat(elasticIndexPresence.check(CLUSTER_REF, "logs-app").state()).isEqualTo(State.UNKNOWN);
    }

    @Test
    void testUnknownWhenTheClusterCantBeReached() throws IOException {
        givenClusterDoc();
        givenClient();
        when(elasticIndexTemplateService.presence(eq("logs-app"), any()))
                .thenThrow(new ConnectException("Connection refused"));

        final Presence presence = elasticIndexPresence.check(CLUSTER_REF, "logs-app");

        assertThat(presence.state()).isEqualTo(State.UNKNOWN);
        assertThat(presence.message()).isEqualTo("Connection refused");
    }

    @Test
    void testUnknownForMissingArguments() {
        assertThat(elasticIndexPresence.check(null, "logs-app").state()).isEqualTo(State.UNKNOWN);
        assertThat(elasticIndexPresence.check(CLUSTER_REF, " ").state()).isEqualTo(State.UNKNOWN);
        assertThat(elasticIndexPresence.check(new DocRef("Pipeline", "p"), "logs-app").message())
                .isEqualTo("Not an Elastic Cluster doc: Pipeline");
        verifyNoInteractions(documentResourceHelper);
    }

    @Test
    void testUnknownForAnythingElse() {
        when(documentResourceHelper.read(elasticClusterStore, CLUSTER_REF))
                .thenThrow(new UncheckedIOException(new IOException("odd")));

        assertThat(elasticIndexPresence.check(CLUSTER_REF, "logs-app").state()).isEqualTo(State.UNKNOWN);
    }

    private void givenClusterDoc() {
        when(documentResourceHelper.read(elasticClusterStore, CLUSTER_REF)).thenReturn(ElasticClusterDoc.builder()
                .uuid(CLUSTER_REF.getUuid())
                .name(CLUSTER_REF.getName())
                .connection(CONNECTION)
                .build());
    }

    private void givenClient() {
        final Rest5ClientTransport transport = mock(Rest5ClientTransport.class);
        when(transport.restClient()).thenReturn(mock(Rest5Client.class));
        final ElasticsearchClient client = mock(ElasticsearchClient.class);
        when(client._transport()).thenReturn(transport);
        when(elasticClientCache.contextResult(eq(CONNECTION), any())).thenAnswer(invocation ->
                invocation.<Function<ElasticsearchClient, ?>>getArgument(1).apply(client));
    }
}
