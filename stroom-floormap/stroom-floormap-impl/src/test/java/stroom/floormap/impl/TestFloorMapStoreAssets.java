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

package stroom.floormap.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DependencyRemapFunction;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.shared.DocDataType;
import stroom.document.asset.impl.DocumentAssetService;
import stroom.importexport.api.ByteArrayImportExportAsset;
import stroom.importexport.api.ImportExportAsset;
import stroom.importexport.api.ImportExportDocument;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportState;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// Pins that a floor map's uploaded assets travel with the document.
///
/// Assets live in the `stroom.document.asset` subsystem's own table keyed on the owning
/// document's UUID, not inside the serialised document, so [stroom.docstore.api.Store] carries
/// none of them. Every lifecycle operation that should move them has to say so, and nothing fails
/// loudly when one forgets: a content pack exported without its assets imports cleanly and the floor
/// map simply renders with every graphic and background missing, on the far system, later. That is
/// how this was found — the Floor Map content pack shipped without any assets in it.
///
/// These tests therefore assert the delegation itself rather than any asset-store behaviour. The
/// question is only ever "was the asset service told", because whenever the answer was no, nothing
/// else noticed.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestFloorMapStoreAssets {

    private static final DocRef DOC_REF =
            new DocRef("FloorMap", "doc-uuid-1", "Ground Floor");

    @Mock
    private StoreFactory storeFactory;
    @Mock
    private FloorMapSerialiser serialiser;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private DocumentAssetService documentAssetService;
    @Mock
    private Store<stroom.floormap.shared.FloorMapDoc> store;

    private FloorMapStoreImpl floorMapStore;

    @BeforeEach
    void setUp() {
        //noinspection unused invocation
        Mockito.when(storeFactory.<stroom.floormap.shared.FloorMapDoc,
                        stroom.floormap.shared.FloorMapDoc.Builder>createStore(
                        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> store);
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(), Mockito.any()))
                .thenReturn(true);
        floorMapStore = new FloorMapStoreImpl(
                storeFactory, serialiser, securityContext, documentAssetService);
    }

    /// Export must attach the document's assets, or a content pack silently omits them.
    ///
    /// They go on as *path* assets rather than extension assets because their keys are
    /// user-chosen file names, not a fixed set of extensions.
    @Test
    void testExportAttachesTheDocumentsAssets() throws IOException {
        final ImportExportDocument exported = new ImportExportDocument();
        Mockito.when(store.exportDocument(Mockito.eq(DOC_REF), Mockito.anyBoolean(), Mockito.any()))
                .thenReturn(exported);
        Mockito.when(documentAssetService.getAssetsForExport(DOC_REF))
                .thenReturn(List.of(asset("plan.png"), asset("desk.svg")));

        final ImportExportDocument result = floorMapStore.exportDocument(DOC_REF, false, List.of());

        assertThat(result.getPathAssets())
                .extracting(ImportExportAsset::getKey)
                .containsExactlyInAnyOrder("plan.png", "desk.svg");
    }

    /// An asset-less document must still export, and must not invent a path asset.
    @Test
    void testExportOfADocumentWithNoAssetsAddsNothing() throws IOException {
        final ImportExportDocument exported = new ImportExportDocument();
        Mockito.when(store.exportDocument(Mockito.eq(DOC_REF), Mockito.anyBoolean(), Mockito.any()))
                .thenReturn(exported);
        Mockito.when(documentAssetService.getAssetsForExport(DOC_REF)).thenReturn(List.of());

        assertThat(floorMapStore.exportDocument(DOC_REF, false, List.of()).getPathAssets()).isEmpty();
    }

    /// Import must hand the incoming path assets to the asset service, keyed on the new document.
    @Test
    void testImportRestoresTheAssetsThatTravelledWithTheDocument() throws IOException {
        final ImportExportDocument incoming = new ImportExportDocument();
        incoming.addPathAsset(asset("plan.png"));
        incoming.addPathAsset(asset("desk.svg"));
        Mockito.when(store.importDocument(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(DOC_REF);

        floorMapStore.importDocument(
                DOC_REF, incoming, new ImportState(DOC_REF, "Ground Floor"), ImportSettings.auto());

        @SuppressWarnings("unchecked")
        final ArgumentCaptor<Collection<ImportExportAsset>> captor =
                ArgumentCaptor.forClass(Collection.class);
        Mockito.verify(documentAssetService).setAssetsFromImport(Mockito.eq(DOC_REF), captor.capture());
        assertThat(captor.getValue())
                .extracting(ImportExportAsset::getKey)
                .containsExactlyInAnyOrder("plan.png", "desk.svg");
    }

    /// Deleting the document must delete its assets.
    ///
    /// Nothing else is keyed to find them afterwards, so rows left behind are unreachable and
    /// permanent — a leak that grows with every deleted floor map.
    @Test
    void testDeleteAlsoDeletesTheDocumentsAssets() throws IOException {
        floorMapStore.deleteDocument(DOC_REF);
        Mockito.verify(documentAssetService).deleteAssetsForDoc(DOC_REF);
    }

    /// Duplicating the document must duplicate its assets, not alias or drop them.
    @Test
    void testCopyAlsoCopiesTheDocumentsAssets() throws IOException {
        final DocRef copyDocRef = new DocRef("FloorMap", "doc-uuid-2", "Ground Floor (copy)");
        Mockito.when(store.readDocument(DOC_REF)).thenReturn(sourceDoc());
        Mockito.when(store.createDocument(Mockito.anyString(), Mockito.any()))
                .thenReturn(copyDocRef);

        floorMapStore.copyDocument(DOC_REF, "Ground Floor", true, Set.of());

        Mockito.verify(documentAssetService).copyAssetsToDoc(DOC_REF, copyDocRef);
    }

    /// Copy reads the source document, so it must be authorised by VIEW on the source.
    ///
    /// The override reaches the unchecked `getStore()` handle directly, which skips the
    /// check [stroom.docstore.api.AbstractDocumentStore#copyDocument] performs, so the check
    /// has to be reinstated in the override — and stay there.
    @Test
    void testCopyChecksViewPermissionOnTheSource() {
        final DocRef copyDocRef = new DocRef("FloorMap", "doc-uuid-2", "Ground Floor (copy)");
        Mockito.when(store.readDocument(DOC_REF)).thenReturn(sourceDoc());
        Mockito.when(store.createDocument(Mockito.anyString(), Mockito.any()))
                .thenReturn(copyDocRef);

        floorMapStore.copyDocument(DOC_REF, "Ground Floor", true, Set.of());

        Mockito.verify(securityContext).hasDocumentPermission(DOC_REF, DocumentPermission.VIEW);
    }

    /// The dependency remapper must not be the thing that carries assets - it only sees DocRefs.
    @Test
    void testDependencyRemapIsUnrelatedToAssets() {
        final DependencyRemapFunction<stroom.floormap.shared.FloorMapDoc> fn =
                floorMapStore.getDependencyRemapFunction();
        assertThat(fn).isNotNull();
        Mockito.verifyNoInteractions(documentAssetService);
    }

    /// A minimally valid source document - AbstractDoc rejects a null UUID.
    private static stroom.floormap.shared.FloorMapDoc sourceDoc() {
        return stroom.floormap.shared.FloorMapDoc.builder()
                .uuid(DOC_REF.getUuid())
                .name(DOC_REF.getName())
                .build();
    }

    private static ImportExportAsset asset(final String key) {
        return new ByteArrayImportExportAsset(
                key, DocDataType.BINARY, key.getBytes(StandardCharsets.UTF_8));
    }
}
