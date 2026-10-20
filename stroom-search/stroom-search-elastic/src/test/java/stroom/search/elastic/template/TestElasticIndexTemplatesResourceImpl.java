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

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.template.ElasticIndexTemplatesResponse.IndexTemplate;
import stroom.search.elastic.template.ElasticIndexTemplatesResponse.Status;
import stroom.security.api.SecurityContext;
import stroom.util.shared.PermissionException;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.rest5_client.Rest5ClientTransport;
import co.elastic.clients.transport.rest5_client.low_level.Rest5Client;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.ConnectException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestElasticIndexTemplatesResourceImpl {

    private static final DocRef CLUSTER_REF = ElasticClusterDoc.buildDocRef()
            .uuid("cluster-uuid")
            .name("cluster")
            .build();
    private static final DocRef INDEX_REF = ElasticIndexDoc.buildDocRef()
            .uuid("8d1c-uuid")
            .name("ecs-keycloak-v1")
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

    private ElasticIndexTemplatesResourceImpl resource;

    @BeforeEach
    void setUp() {
        resource = new ElasticIndexTemplatesResourceImpl(
                () -> elasticIndexStore,
                () -> elasticClusterStore,
                () -> documentResourceHelper,
                () -> securityContext,
                () -> elasticClientCache,
                () -> elasticIndexTemplateService);
    }

    @Test
    void testReadsTemplatesWithTheDocsClusterConnection() throws IOException {
        givenIndexDoc(indexDoc("ecs-keycloak-v1", CLUSTER_REF));
        givenClusterDoc();
        givenClient();
        final ElasticIndexTemplatesResponse expected = new ElasticIndexTemplatesResponse(
                "ecs-keycloak-v1",
                null,
                null,
                IndexTemplate.failed(Status.FORBIDDEN, "unauthorised"),
                List.of());
        when(elasticIndexTemplateService.read(eq("ecs-keycloak-v1"), any())).thenReturn(expected);

        final ElasticIndexTemplatesResponse actual = resource.fetchTemplates(
                new ElasticIndexTemplatesRequest(INDEX_REF));

        assertThat(actual).isSameAs(expected);
        // The cluster is read as the processing user, as a search reads it
        verify(securityContext).asProcessingUserResult(any());
        verify(elasticClientCache).contextResult(eq(CONNECTION), any());
    }

    @Test
    void testNoReadPermission() {
        when(documentResourceHelper.read(elasticIndexStore, INDEX_REF))
                .thenThrow(new PermissionException(null, "You do not have permission to use ecs-keycloak-v1"));

        // The PermissionException mapper makes this a 403
        assertThatThrownBy(() -> resource.fetchTemplates(new ElasticIndexTemplatesRequest(INDEX_REF)))
                .isInstanceOf(PermissionException.class);
        verify(securityContext, never()).asProcessingUserResult(any());
    }

    @Test
    void testNoSuchDoc() {
        when(documentResourceHelper.read(elasticIndexStore, INDEX_REF))
                .thenThrow(new DocumentNotFoundException(INDEX_REF));

        assertStatus(new ElasticIndexTemplatesRequest(INDEX_REF), 404);
    }

    @Test
    void testNullRead() {
        when(documentResourceHelper.read(elasticIndexStore, INDEX_REF)).thenReturn(null);

        assertStatus(new ElasticIndexTemplatesRequest(INDEX_REF), 404);
    }

    @Test
    void testMissingRequest() {
        assertStatus(null, 400);
        assertStatus(new ElasticIndexTemplatesRequest(null), 400);
    }

    @Test
    void testWrongType() {
        assertStatus(new ElasticIndexTemplatesRequest(DocRef.builder().type("Feed").uuid("feed-uuid").build()), 400);
    }

    @Test
    void testNoIndexName() {
        givenIndexDoc(indexDoc(null, CLUSTER_REF));

        assertStatus(new ElasticIndexTemplatesRequest(INDEX_REF), 400);
    }

    @Test
    void testNoCluster() {
        givenIndexDoc(indexDoc("ecs-keycloak-v1", null));

        assertStatus(new ElasticIndexTemplatesRequest(INDEX_REF), 400);
    }

    @Test
    void testClusterDocMissing() {
        givenIndexDoc(indexDoc("ecs-keycloak-v1", CLUSTER_REF));
        givenProcessingUser();
        when(elasticClusterStore.readDocument(CLUSTER_REF)).thenThrow(new DocumentNotFoundException(CLUSTER_REF));

        assertStatus(new ElasticIndexTemplatesRequest(INDEX_REF), 503);
    }

    @Test
    void testClusterUnreachable() throws IOException {
        givenIndexDoc(indexDoc("ecs-keycloak-v1", CLUSTER_REF));
        givenClusterDoc();
        givenClient();
        when(elasticIndexTemplateService.read(eq("ecs-keycloak-v1"), any()))
                .thenThrow(new ConnectException("Connection refused"));

        final WebApplicationException e = assertStatus(new ElasticIndexTemplatesRequest(INDEX_REF), 503);

        assertThat(e.getResponse().getEntity())
                .isEqualTo(Map.of("code", 503, "message", "Connection refused"));
    }

    private WebApplicationException assertStatus(final ElasticIndexTemplatesRequest request, final int status) {
        final Throwable thrown = catchThrowable(() -> resource.fetchTemplates(request));
        assertThat(thrown).isInstanceOf(WebApplicationException.class);
        final WebApplicationException e = (WebApplicationException) thrown;
        assertThat(e.getResponse().getStatus()).isEqualTo(status);
        return e;
    }

    private void givenIndexDoc(final ElasticIndexDoc doc) {
        when(documentResourceHelper.read(elasticIndexStore, INDEX_REF)).thenReturn(doc);
    }

    private void givenProcessingUser() {
        when(securityContext.asProcessingUserResult(any())).thenAnswer(invocation ->
                invocation.<Supplier<?>>getArgument(0).get());
    }

    private void givenClusterDoc() {
        givenProcessingUser();
        when(elasticClusterStore.readDocument(CLUSTER_REF)).thenReturn(ElasticClusterDoc.builder()
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

    private static ElasticIndexDoc indexDoc(final String indexName, final DocRef clusterRef) {
        return ElasticIndexDoc.builder()
                .uuid(INDEX_REF.getUuid())
                .name(INDEX_REF.getName())
                .indexName(indexName)
                .clusterRef(clusterRef)
                .build();
    }
}
