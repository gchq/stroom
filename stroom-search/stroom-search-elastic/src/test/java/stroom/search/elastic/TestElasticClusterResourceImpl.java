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

package stroom.search.elastic;

import stroom.docref.DocRef;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticClusterTestResponse;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.PermissionException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestElasticClusterResourceImpl {

    private static final String UUID = "f1c0a5e2-1b2c-4d5e-8f90-123456789abc";

    @Mock
    private SecurityContext securityContext;
    @Mock
    private ElasticConnectionResolver elasticConnectionResolver;
    @Mock
    private ElasticClientFactory elasticClientFactory;

    private ElasticClusterResourceImpl resource;

    @BeforeEach
    void setUp() {
        resource = new ElasticClusterResourceImpl(
                () -> null,
                () -> null,
                ElasticConfig::new,
                () -> elasticConnectionResolver,
                () -> elasticClientFactory,
                () -> securityContext);
    }

    @Test
    void testTestCluster_requiresEditPermission() {
        final DocRef docRef = new DocRef(ElasticClusterDoc.TYPE, UUID);
        when(securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT))
                .thenReturn(false);

        assertThatThrownBy(() -> resource.testCluster(createCluster(UUID)))
                .isInstanceOf(PermissionException.class);

        // The stored credentials must not be resolved, let alone sent anywhere
        verify(elasticConnectionResolver, never()).resolve(any());
        verify(elasticClientFactory, never()).create(any(), any());
    }

    @Test
    void testTestCluster_noCluster() {
        assertThatThrownBy(() -> resource.testCluster(null))
                .isInstanceOf(EntityServiceException.class);

        verify(elasticConnectionResolver, never()).resolve(any());
    }

    @Test
    void testTestCluster_unresolvableCredentialReportsFailure() {
        final DocRef docRef = new DocRef(ElasticClusterDoc.TYPE, UUID);
        when(securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT))
                .thenReturn(true);
        when(elasticConnectionResolver.resolve(any()))
                .thenThrow(new IllegalArgumentException("Unable to find API key credential 'foo'"));

        final ElasticClusterTestResponse response = resource.testCluster(createCluster(UUID));

        assertThat(response.isOk())
                .isFalse();
        assertThat(response.getMessage())
                .isEqualTo("Unable to find API key credential 'foo'");
        verify(elasticClientFactory, never()).create(any(), any());
    }

    private ElasticClusterDoc createCluster(final String uuid) {
        return ElasticClusterDoc.builder()
                .uuid(uuid)
                .connection(new ElasticConnectionConfig(List.of("https://evil.example.com"), "foo", null))
                .build();
    }
}
