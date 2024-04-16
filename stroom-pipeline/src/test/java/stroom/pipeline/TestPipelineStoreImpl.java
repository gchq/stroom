package stroom.pipeline;

import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.pipeline.shared.data.PipelineProperties;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;

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
class TestPipelineStoreImpl {

    final PipelineSerialiser pipelineSerialiser = new PipelineSerialiser(new Serialiser2FactoryImpl());

    @Mock
    private StoreFactory mockStoreFactory;
    @Mock
    private Store mockStore;
    @Mock
    private DocRefInfoService mockDocRefInfoService;
    @Captor
    private ArgumentCaptor<Map<String, byte[]>> dataMapCaptor;

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

        final PipelineStoreImpl pipelineStore = new PipelineStoreImpl(
                mockStoreFactory,
                pipelineSerialiser,
                null,
                null,
                mockDocRefInfoService);

        final PipelineDoc pipelineDoc = new PipelineDoc();
        final PipelineData pipelineData = new PipelineData();
        final PipelineProperties pipelineProperties = new PipelineProperties();

        final PipelineProperty pipelineProperty1 = new PipelineProperty("foo", "volumeGroup");
        pipelineProperty1.setValue(new PipelinePropertyValue("MyVolGrp"));
        pipelineProperties.getAdd().add(pipelineProperty1);

        final PipelineProperty pipelineProperty2 = new PipelineProperty("foo", "xxx");
        pipelineProperty2.setValue(new PipelinePropertyValue("123"));
        pipelineProperties.getAdd().add(pipelineProperty2);

        final PipelineProperty pipelineProperty3 = new PipelineProperty("foo", "volumeGroup");
        pipelineProperty3.setValue(new PipelinePropertyValue("MyVolGrp"));
        pipelineProperties.getRemove().add(pipelineProperty3);

        final PipelineProperty pipelineProperty4 = new PipelineProperty("foo", "xxx");
        pipelineProperty4.setValue(new PipelinePropertyValue("123"));
        pipelineProperties.getRemove().add(pipelineProperty4);

        pipelineData.setProperties(pipelineProperties);
        pipelineDoc.setPipelineData(pipelineData);

        final Map<String, byte[]> map = pipelineSerialiser.write(pipelineDoc);

        pipelineStore.importDocument(null, map, null, null);

        final Map<String, byte[]> map2 = dataMapCaptor.getValue();

        final PipelineDoc pipelineDoc2 = pipelineSerialiser.read(map2);

        final List<PipelineProperty> addedProperties = pipelineDoc2.getPipelineData().getAddedProperties();
        assertThat(addedProperties)
                .hasSize(2);
        assertThat(addedProperties.get(0).getName())
                .isEqualTo("volumeGroup");
        assertThat(addedProperties.get(0).getValue().getEntity())
                .isEqualTo(docRef);
        assertThat(addedProperties.get(0).getValue().getString())
                .isNull();
        assertThat(addedProperties.get(1).getName())
                .isEqualTo("xxx");
        assertThat(addedProperties.get(1).getValue())
                .isEqualTo(new PipelinePropertyValue("123"));

        final List<PipelineProperty> removedProperties = pipelineDoc2.getPipelineData().getRemovedProperties();
        assertThat(removedProperties)
                .hasSize(2);
        assertThat(removedProperties.get(0).getName())
                .isEqualTo("volumeGroup");
        assertThat(removedProperties.get(0).getValue().getEntity())
                .isEqualTo(docRef);
        assertThat(removedProperties.get(0).getValue().getString())
                .isNull();
        assertThat(removedProperties.get(1).getName())
                .isEqualTo("xxx");
        assertThat(removedProperties.get(1).getValue())
                .isEqualTo(new PipelinePropertyValue("123"));
    }
}
