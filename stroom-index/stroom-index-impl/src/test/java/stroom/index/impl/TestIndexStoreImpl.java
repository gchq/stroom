package stroom.index.impl;

import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.State;
import stroom.index.shared.LuceneIndexDoc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestIndexStoreImpl {

    final IndexSerialiser indexSerialiser = new IndexSerialiser(new Serialiser2FactoryImpl());

    @Mock
    private StoreFactory mockStoreFactory;
    @Mock
    private Store mockStore;
    @Mock
    private DocRefInfoService mockDocRefInfoService;
    @Mock
    private IndexFieldService mockIndexFieldService;

    @Captor
    private ArgumentCaptor<Map<String, byte[]>> dataMapCaptor;

    @SuppressWarnings("deprecation")
    @Test
    void importDocument() throws IOException {
        Mockito.when(mockStoreFactory.createStore(Mockito.any(), Mockito.anyString(), Mockito.any()))
                .thenReturn(mockStore);

        Mockito.when(mockStore.importDocument(
                        Mockito.any(),
                        dataMapCaptor.capture(),
                        Mockito.any(),
                        Mockito.any()))
                .thenReturn(null);
        final DocRef docRef = FsVolumeGroup.buildDocRef()
                .randomUuid()
                .name("MyVolGrp")
                .build();

        Mockito.doAnswer(
                        invocation -> {
                            final String name = invocation.getArgument(1, String.class);
                            if ("MyVolGrp".equals(name)) {
                                return List.of(docRef);
                            } else {
                                return Collections.emptyList();
                            }
                        }).when(mockDocRefInfoService)
                .findByName(Mockito.any(), Mockito.any(), Mockito.eq(false));

        final IndexStoreImpl indexStore = new IndexStoreImpl(
                mockStoreFactory,
                indexSerialiser,
                () -> mockIndexFieldService,
                mockDocRefInfoService);

        final LuceneIndexDoc indexDoc = new LuceneIndexDoc();
        indexDoc.setVolumeGroupName("MyVolGrp");
        final Map<String, byte[]> map = indexSerialiser.write(indexDoc);

        final ImportState importState = new ImportState(null, null);
        importState.setState(State.UPDATE);
        indexStore.importDocument(null, map, importState, null);

        final Map<String, byte[]> map2 = dataMapCaptor.getValue();

        final LuceneIndexDoc indexDoc2 = indexSerialiser.read(map2);
        assertThat(indexDoc2.getVolumeGroupName())
                .isNull();
        assertThat(indexDoc2.getVolumeGroupDocRef())
                .isEqualTo(docRef);
    }
}
