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

package stroom.documentation.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.documentation.shared.DocumentationDoc;
import stroom.resource.api.ResourceStore;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDocumentationResourceImpl {

    private final DocumentationStore store = Mockito.mock(DocumentationStore.class);
    private final DocumentResourceHelper helper = Mockito.mock(DocumentResourceHelper.class);
    private final DocumentationResourceImpl resource = new DocumentationResourceImpl(() -> store, () -> helper,
            () -> Mockito.mock(ResourceStore.class));

    @Test
    void aDocumentIsDownloadedAsMarkdownNamedAfterIt(@TempDir final Path dir) throws IOException {
        // It was downloaded as 'dictionary.txt', whatever the document.
        final DocRef docRef = new DocRef(DocumentationDoc.TYPE, "doc-uuid", "Acme VPN - CEF");
        Mockito.when(store.readDocument(docRef)).thenReturn(DocumentationDoc.builder()
                .uuid(docRef.getUuid())
                .name(docRef.getName())
                .data("# Acme VPN\n")
                .build());
        final ResourceStore resourceStore = Mockito.mock(ResourceStore.class);
        final ResourceKey key = new ResourceKey("key", "Acme VPN - CEF.md");
        Mockito.when(resourceStore.createTempFile(Mockito.anyString())).thenReturn(key);
        final Path file = dir.resolve("download");
        Mockito.when(resourceStore.getTempFile(key)).thenReturn(file);
        final DocumentationResourceImpl downloading = new DocumentationResourceImpl(() -> store, () -> helper,
                () -> resourceStore);

        final ResourceGeneration generation = downloading.download(docRef);

        Mockito.verify(resourceStore).createTempFile("Acme VPN - CEF.md");
        assertThat(generation.getResourceKey()).isEqualTo(key);
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo("# Acme VPN\n");
    }

    @Test
    void aDocumentThatIsGoneCannotBeDownloaded() {
        final DocRef gone = new DocRef(DocumentationDoc.TYPE, "gone-uuid", "Gone");

        assertThatThrownBy(() -> resource.download(gone))
                .isInstanceOf(EntityServiceException.class)
                .hasMessage("Unable to find documentation");
    }

    @Test
    void anUpdateMustBeOfTheDocumentItNames() {
        final DocumentationDoc doc = DocumentationDoc.builder().uuid("doc-uuid").name("Notes").build();

        assertThatThrownBy(() -> resource.update("other-uuid", doc))
                .isInstanceOf(EntityServiceException.class)
                .hasMessage("The document UUID must match the update UUID");
        Mockito.verifyNoInteractions(helper);

        Mockito.when(helper.update(store, doc)).thenReturn(doc);
        assertThat(resource.update("doc-uuid", doc)).isSameAs(doc);
    }

    @Test
    void aDocumentIsFetchedThroughTheHelperThatChecksPermissions() {
        final DocumentationDoc doc = DocumentationDoc.builder().uuid("doc-uuid").name("Notes").build();
        Mockito.when(helper.read(Mockito.eq(store), Mockito.any(DocRef.class))).thenReturn(doc);

        assertThat(resource.fetch("doc-uuid")).isSameAs(doc);

        final ArgumentCaptor<DocRef> asked = ArgumentCaptor.forClass(DocRef.class);
        Mockito.verify(helper).read(Mockito.eq(store), asked.capture());
        assertThat(asked.getValue().getUuid()).isEqualTo("doc-uuid");
        assertThat(asked.getValue().getType()).isEqualTo(DocumentationDoc.TYPE);
    }

    @Test
    void aFileNameHoldsNothingAFileSystemWouldRefuse() {
        assertThat(DocumentationResourceImpl.fileName("Delinea-SecretServer-V1.0 - CEF"))
                .isEqualTo("Delinea-SecretServer-V1.0 - CEF.md");
        assertThat(DocumentationResourceImpl.fileName("a/b\\c:d*e?f\"g<h>i|j\tk"))
                .isEqualTo("a_b_c_d_e_f_g_h_i_j_k.md");
        assertThat(DocumentationResourceImpl.fileName("  Notes  ")).isEqualTo("Notes.md");
        assertThat(DocumentationResourceImpl.fileName(null)).isEqualTo("documentation.md");
        assertThat(DocumentationResourceImpl.fileName("   ")).isEqualTo("documentation.md");
    }
}
