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

import stroom.ai.api.ElasticIndexTemplates;
import stroom.ai.api.ElasticIndexTemplates.IndexTemplates;
import stroom.ai.api.ElasticIndexTemplates.ReadException;
import stroom.ai.api.ElasticIndexTemplates.Reason;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.search.elastic.ElasticClientCache;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.template.ElasticIndexTemplatesResponse.ComponentTemplate;
import stroom.search.elastic.template.ElasticIndexTemplatesResponse.IndexTemplate;
import stroom.search.elastic.template.ElasticIndexTemplatesResponse.OverlappingTemplate;
import stroom.search.elastic.template.ElasticIndexTemplatesResponse.Status;
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
import java.net.ConnectException;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestAiElasticIndexTemplates {

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

    private AiElasticIndexTemplates elasticIndexTemplates;

    @BeforeEach
    void setUp() {
        elasticIndexTemplates = new AiElasticIndexTemplates(new ElasticIndexTemplateReader(
                () -> elasticIndexStore,
                () -> elasticClusterStore,
                () -> documentResourceHelper,
                () -> securityContext,
                () -> elasticClientCache,
                () -> elasticIndexTemplateService));
    }

    @Test
    void testReadsTemplatesWithTheDocsClusterConnection() throws IOException {
        givenIndexDoc(indexDoc("ecs-keycloak-v1", CLUSTER_REF));
        givenClusterDoc();
        givenClient();
        when(elasticIndexTemplateService.read(eq("ecs-keycloak-v1"), any())).thenReturn(
                new ElasticIndexTemplatesResponse(
                        "ecs-keycloak-v1",
                        "ecs-keycloak-v1-000003",
                        null,
                        new IndexTemplate(
                                Status.OK,
                                "ecs-keycloak",
                                "{\"composed_of\":[\"ecs@mappings\",\"keycloak\"]}",
                                List.of(new OverlappingTemplate("ecs", List.of("ecs-*"), 100)),
                                null),
                        List.of(new ComponentTemplate("ecs@mappings", Status.OK, "{\"template\":{}}", null),
                                new ComponentTemplate("keycloak", Status.FORBIDDEN, null, "unauthorised"))));

        final IndexTemplates actual = elasticIndexTemplates.read(INDEX_REF);

        assertThat(actual).isEqualTo(new IndexTemplates(
                "ecs-keycloak-v1",
                "ecs-keycloak-v1-000003",
                null,
                new ElasticIndexTemplates.IndexTemplate(
                        ElasticIndexTemplates.Status.OK,
                        "ecs-keycloak",
                        "{\"composed_of\":[\"ecs@mappings\",\"keycloak\"]}",
                        List.of(new ElasticIndexTemplates.OverlappingTemplate("ecs", List.of("ecs-*"), 100)),
                        null),
                List.of(new ElasticIndexTemplates.ComponentTemplate(
                                "ecs@mappings", ElasticIndexTemplates.Status.OK, "{\"template\":{}}", null),
                        new ElasticIndexTemplates.ComponentTemplate(
                                "keycloak", ElasticIndexTemplates.Status.FORBIDDEN, null, "unauthorised"))));
        // The cluster is read as the processing user, as a search reads it
        verify(securityContext).asProcessingUserResult(any());
        verify(elasticClientCache).contextResult(eq(CONNECTION), any());
    }

    @Test
    void testFailedPartsKeepTheirStatusAndMessage() throws IOException {
        givenIndexDoc(indexDoc("ecs-keycloak-v1", CLUSTER_REF));
        givenClusterDoc();
        givenClient();
        when(elasticIndexTemplateService.read(eq("ecs-keycloak-v1"), any())).thenReturn(
                new ElasticIndexTemplatesResponse(
                        "ecs-keycloak-v1",
                        null,
                        "resolve refused",
                        IndexTemplate.failed(Status.NOT_FOUND, "No index template matches 'ecs-keycloak-v1'"),
                        List.of()));

        final IndexTemplates actual = elasticIndexTemplates.read(INDEX_REF);

        assertThat(actual.resolvedName()).isNull();
        assertThat(actual.resolveMessage()).isEqualTo("resolve refused");
        assertThat(actual.indexTemplate().status()).isEqualTo(ElasticIndexTemplates.Status.NOT_FOUND);
        assertThat(actual.indexTemplate().message()).isEqualTo("No index template matches 'ecs-keycloak-v1'");
        assertThat(actual.indexTemplate().overlapping()).isEmpty();
        assertThat(actual.componentTemplates()).isEmpty();
    }

    @Test
    void testNoReadPermissionIsNotFound() {
        when(documentResourceHelper.read(elasticIndexStore, INDEX_REF))
                .thenThrow(new PermissionException(null, "You do not have permission to use ecs-keycloak-v1"));

        final ReadException e = assertReason(INDEX_REF, Reason.NOT_FOUND);

        // Nothing says the doc exists
        assertThat(e.getMessage()).isEqualTo("Elastic Index 8d1c-uuid not found");
        verify(securityContext, never()).asProcessingUserResult(any());
    }

    @Test
    void testNoSuchDoc() {
        when(documentResourceHelper.read(elasticIndexStore, INDEX_REF))
                .thenThrow(new DocumentNotFoundException(INDEX_REF));

        assertReason(INDEX_REF, Reason.NOT_FOUND);
    }

    @Test
    void testNullRead() {
        when(documentResourceHelper.read(elasticIndexStore, INDEX_REF)).thenReturn(null);

        assertReason(INDEX_REF, Reason.NOT_FOUND);
    }

    @Test
    void testWrongTypeIsNotFound() {
        assertReason(DocRef.builder().type("Feed").uuid("feed-uuid").build(), Reason.NOT_FOUND);

        verifyNoInteractions(documentResourceHelper);
    }

    @Test
    void testNoIndexName() {
        givenIndexDoc(indexDoc(null, CLUSTER_REF));

        final ReadException e = assertReason(INDEX_REF, Reason.NOT_CONFIGURED);

        assertThat(e.getMessage()).isEqualTo("Elasticsearch index name not specified");
    }

    @Test
    void testNoCluster() {
        givenIndexDoc(indexDoc("ecs-keycloak-v1", null));

        final ReadException e = assertReason(INDEX_REF, Reason.NOT_CONFIGURED);

        assertThat(e.getMessage()).isEqualTo("Elasticsearch cluster configuration not specified");
    }

    @Test
    void testClusterDocMissing() {
        givenIndexDoc(indexDoc("ecs-keycloak-v1", CLUSTER_REF));
        givenProcessingUser();
        when(elasticClusterStore.readDocument(CLUSTER_REF)).thenThrow(new DocumentNotFoundException(CLUSTER_REF));

        assertReason(INDEX_REF, Reason.CLUSTER_UNAVAILABLE);
    }

    @Test
    void testClusterUnreachable() throws IOException {
        givenIndexDoc(indexDoc("ecs-keycloak-v1", CLUSTER_REF));
        givenClusterDoc();
        givenClient();
        when(elasticIndexTemplateService.read(eq("ecs-keycloak-v1"), any()))
                .thenThrow(new ConnectException("Connection refused"));

        final ReadException e = assertReason(INDEX_REF, Reason.CLUSTER_UNAVAILABLE);

        assertThat(e.getMessage()).isEqualTo("Connection refused");
    }

    private ReadException assertReason(final DocRef docRef, final Reason reason) {
        final Throwable thrown = catchThrowable(() -> elasticIndexTemplates.read(docRef));
        assertThat(thrown).isInstanceOf(ReadException.class);
        final ReadException e = (ReadException) thrown;
        assertThat(e.getReason()).isEqualTo(reason);
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
