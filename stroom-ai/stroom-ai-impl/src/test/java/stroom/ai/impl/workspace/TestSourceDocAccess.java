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

package stroom.ai.impl.workspace;

import stroom.ai.impl.workspace.SourceDocAccess.Access;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerNode;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TestSourceDocAccess {

    private final ExplorerNodeService explorerNodeService = Mockito.mock(ExplorerNodeService.class);
    private final DataSourceProviderRegistry registry = Mockito.mock(DataSourceProviderRegistry.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final AtomicLong now = new AtomicLong(1_000);
    private final SourceDocAccess sourceDocAccess = new SourceDocAccess(() -> explorerNodeService, () -> registry,
            securityContext, now::get);

    @BeforeEach
    void setUp() {
        Mockito.when(registry.getDataSourceTypes()).thenReturn(Set.of("Index", "View"));
        Mockito.when(explorerNodeService.getNodeByUuid("xslt-uuid"))
                .thenReturn(Optional.of(ExplorerNode.builder().type("XSLT").uuid("xslt-uuid").name("X").build()));
    }

    @Test
    void dataSourceTypesNeedUseAndOthersView() {
        assertThat(sourceDocAccess.requiredPermission("Index")).isEqualTo(DocumentPermission.USE);
        assertThat(sourceDocAccess.requiredPermission("View")).isEqualTo(DocumentPermission.USE);
        assertThat(sourceDocAccess.requiredPermission("XSLT")).isEqualTo(DocumentPermission.VIEW);
        assertThat(sourceDocAccess.requiredPermission("Dictionary")).isEqualTo(DocumentPermission.VIEW);
    }

    @Test
    void theTypeIsKeptBrieflyButAccessIsCheckedEveryTime() {
        Mockito.when(securityContext.hasDocumentPermission(new DocRef("XSLT", "xslt-uuid"),
                DocumentPermission.VIEW)).thenReturn(true, false);

        assertThat(sourceDocAccess.access("xslt-uuid")).isEqualTo(Access.ALLOWED);
        assertThat(sourceDocAccess.access("xslt-uuid")).isEqualTo(Access.DENIED);
        Mockito.verify(explorerNodeService, Mockito.times(1)).getNodeByUuid("xslt-uuid");

        // Once the time is up, the type is looked up again.
        now.addAndGet(SourceDocAccess.TYPE_CACHE_TIME.toMillis() + 1);
        sourceDocAccess.access("xslt-uuid");
        Mockito.verify(explorerNodeService, Mockito.times(2)).getNodeByUuid("xslt-uuid");
    }

    @Test
    void aSourceThatCantBeFoundIsNotKept() {
        Mockito.when(explorerNodeService.getNodeByUuid("gone-uuid")).thenReturn(Optional.empty());
        Mockito.when(registry.findDataSourceByUuid("gone-uuid")).thenReturn(Optional.empty());

        assertThat(sourceDocAccess.access("gone-uuid")).isEqualTo(Access.DENIED);
        assertThat(sourceDocAccess.access("gone-uuid")).isEqualTo(Access.DENIED);
        assertThat(sourceDocAccess.access(null)).isEqualTo(Access.DENIED);
        Mockito.verify(explorerNodeService, Mockito.times(2)).getNodeByUuid("gone-uuid");
        Mockito.verifyNoInteractions(securityContext);
    }

    @Test
    void aCheckThatFailsIsUnknownNotAllowed() {
        // A failed permission check doesn't allow use, but nor does it say access is gone.
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(), Mockito.any()))
                .thenThrow(new RuntimeException("no"));
        assertThat(sourceDocAccess.access("xslt-uuid")).isEqualTo(Access.UNKNOWN);

        // Nor does a failed lookup of the source's type.
        Mockito.when(explorerNodeService.getNodeByUuid("flaky-uuid")).thenThrow(new RuntimeException("db"));
        assertThat(sourceDocAccess.access("flaky-uuid")).isEqualTo(Access.UNKNOWN);
    }
}
