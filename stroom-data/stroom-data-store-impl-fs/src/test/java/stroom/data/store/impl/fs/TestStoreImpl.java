package stroom.data.store.impl.fs;

import stroom.aws.s3.shared.S3Location;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.DataException;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.Source;
import stroom.data.store.api.Target;
import stroom.data.store.impl.DataVolumeService;
import stroom.data.store.impl.fs.shared.DataVolume;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestStoreImpl {

    @Mock
    private MetaService mockMetaService;
    @Mock
    private FsVolumeService mockFsVolumeService;
    @Mock
    private DataVolumeService mockDataVolumeService;
    @Mock
    private StreamStore mockStreamStore;

    private StoreImpl storeImpl;

    @BeforeEach
    void setUp() {
        storeImpl = new StoreImpl(
                Map.of(FsVolumeType.STANDARD, mockStreamStore),
                mockMetaService,
                mockFsVolumeService,
                mockDataVolumeService);
    }

    private abstract static class TestSegmentInputStream extends SegmentInputStream {

        private final InputStream delegate;
        private final long size;

        TestSegmentInputStream(final InputStream delegate, final long size) {
            this.delegate = delegate;
            this.size = size;
        }

        @Override
        public long size() {
            return size;
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public long count() {
            return 1;
        }

        @Override
        public void include(long segment) {
        }

        @Override
        public void includeAll() {
        }

        @Override
        public void exclude(long segment) {
        }

        @Override
        public void excludeAll() {
        }
    }

    @Nested
    class OpenTarget {

        @Test
        void testOpenTarget_Success() {
            // Given
            final MetaProperties metaProperties = MetaProperties.builder().build();
            final String volumeGroup = "testGroup";
            final FsVolume fsVolume = mock(FsVolume.class);
            final Meta meta = mock(Meta.class);
            final DataVolume dataVolume = mock(DataVolume.class);
            final Target target = mock(Target.class);

            when(mockFsVolumeService.getVolume(volumeGroup)).thenReturn(fsVolume);
            when(mockMetaService.create(metaProperties)).thenReturn(meta);
            when(meta.getId()).thenReturn(1L);
            when(mockDataVolumeService.createDataVolume(1L, fsVolume)).thenReturn(dataVolume);
            when(dataVolume.volume()).thenReturn(fsVolume);
            when(fsVolume.getVolumeType()).thenReturn(FsVolumeType.STANDARD);
            when(mockStreamStore.openTarget(meta, dataVolume)).thenReturn(target);

            // When
            final Target result = storeImpl.openTarget(metaProperties, volumeGroup);

            // Then
            assertThat(result).isEqualTo(target);
        }

        @Test
        void testOpenTarget_NoVolume() {
            // Given
            final MetaProperties metaProperties = MetaProperties.builder().build();
            when(mockFsVolumeService.getVolume(anyString())).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> storeImpl.openTarget(metaProperties, "group"))
                    .isInstanceOf(DataException.class)
                    .hasMessageContaining("no writable volumes");
        }
    }

    @Nested
    class AddExistingS3Source {

        @Test
        void testAddExistingS3Source_Success() {
            // Given
            final MetaProperties metaProperties = MetaProperties.builder().build();
            final S3Location s3Location = new S3Location("region", "bucket", "key");
            final FsVolume fsVolume = mock(FsVolume.class);
            final Meta meta = mock(Meta.class);

            when(mockFsVolumeService.getS3Volume(s3Location)).thenReturn(Optional.of(fsVolume));
            when(mockMetaService.create(metaProperties, Status.UNLOCKED)).thenReturn(meta);
            when(meta.getId()).thenReturn(1L);

            // When
            storeImpl.addExistingS3Source(metaProperties, s3Location);

            // Then
            verify(mockDataVolumeService).createS3LocationDataVolume(1L, fsVolume, Set.of(s3Location), true);
        }

        @Test
        void testAddExistingS3Source_NoVolume() {
            // Given
            final MetaProperties metaProperties = MetaProperties.builder().build();
            final S3Location s3Location = new S3Location("region", "bucket", "key");
            when(mockFsVolumeService.getS3Volume(s3Location)).thenReturn(Optional.empty());

            // When & Then
            assertThatThrownBy(() -> storeImpl.addExistingS3Source(metaProperties, s3Location))
                    .isInstanceOf(DataException.class)
                    .hasMessageContaining("No S3 volume found");
        }
    }

    @Nested
    class OpenSource {

        @Test
        void testOpenSource_Success() {
            // Given
            final long streamId = 1L;
            final Meta meta = mock(Meta.class);
            final DataVolume dataVolume = mock(DataVolume.class);
            final FsVolume fsVolume = mock(FsVolume.class);
            final Source source = mock(Source.class);

            when(mockMetaService.getMeta(streamId, false)).thenReturn(meta);
            when(meta.getId()).thenReturn(streamId);
            when(mockDataVolumeService.findDataVolume(streamId)).thenReturn(dataVolume);
            when(dataVolume.volume()).thenReturn(fsVolume);
            when(fsVolume.getVolumeType()).thenReturn(FsVolumeType.STANDARD);
            when(mockStreamStore.openSource(meta, dataVolume)).thenReturn(source);

            // When
            final Source result = storeImpl.openSource(streamId);

            // Then
            assertThat(result).isEqualTo(source);
        }

        @Test
        void testOpenSource_MetaNotFound() {
            // Given
            when(mockMetaService.getMeta(anyLong(), anyBoolean())).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> storeImpl.openSource(1L, false))
                    .isInstanceOf(DataException.class)
                    .hasMessageContaining("Unable to find meta data for id=1 with valid status");

            assertThatThrownBy(() -> storeImpl.openSource(1L, true))
                    .isInstanceOf(DataException.class)
                    .hasMessageContaining("Unable to find meta data for id=1 with any status");
        }

        @Test
        void testOpenSource_DataVolumeNotFound() {
            // Given
            final Meta meta = mock(Meta.class);
            when(mockMetaService.getMeta(anyLong(), anyBoolean())).thenReturn(meta);
            when(mockDataVolumeService.findDataVolume(anyLong())).thenReturn(null);

            // When & Then
            assertThatThrownBy(() -> storeImpl.openSource(1L))
                    .isInstanceOf(DataException.class)
                    .hasMessageContaining("Unable to find any volume");
        }
    }

    @Nested
    class LogicallyDeleteTarget {

        @Test
        void testLogicallyDeleteTarget_Success() {
            // Given
            final Target target = mock(Target.class);

            // When
            storeImpl.logicallyDeleteTarget(target);

            // Then
            verify(target).logicallyDelete();
        }

        @Test
        void testLogicallyDeleteTarget_Null() {
            // When & Then
            storeImpl.logicallyDeleteTarget(null); // Should not throw exception
        }

        @Test
        void testLogicallyDeleteTarget_Exception() {
            // Given
            final Target target = mock(Target.class);
            doThrow(new RuntimeException("Test")).when(target).logicallyDelete();

            // When & Then
            storeImpl.logicallyDeleteTarget(target); // Should handle exception internally
            verify(target).logicallyDelete();
        }
    }

    @Nested
    class GetAttributes {

        @Test
        void testGetAttributes_Success() throws IOException {
            // Given
            final long metaId = 1L;
            final Source source = mock(Source.class);
            final AttributeMap attributes = new AttributeMap();
            attributes.put("key", "value");

            // Mock openSource behavior
            final Meta meta = mock(Meta.class);
            final DataVolume dataVolume = mock(DataVolume.class);
            final FsVolume fsVolume = mock(FsVolume.class);
            when(mockMetaService.getMeta(metaId, true)).thenReturn(meta);
            when(meta.getId()).thenReturn(metaId);
            when(mockDataVolumeService.findDataVolume(metaId)).thenReturn(dataVolume);
            when(dataVolume.volume()).thenReturn(fsVolume);
            when(fsVolume.getVolumeType()).thenReturn(FsVolumeType.STANDARD);
            when(mockStreamStore.openSource(meta, dataVolume)).thenReturn(source);

            when(source.getAttributes()).thenReturn(attributes);

            // When
            final Map<String, String> result = storeImpl.getAttributes(metaId);

            // Then
            assertThat(result).isEqualTo(attributes);
            verify(source).close();
        }

        @Test
        void testGetAttributes_IOException() throws IOException {
            // Given
            final long metaId = 1L;
            final Source source = mock(Source.class);

            // Mock openSource behavior
            final Meta meta = mock(Meta.class);
            final DataVolume dataVolume = mock(DataVolume.class);
            final FsVolume fsVolume = mock(FsVolume.class);
            when(mockMetaService.getMeta(metaId, true)).thenReturn(meta);
            when(meta.getId()).thenReturn(metaId);
            when(mockDataVolumeService.findDataVolume(metaId)).thenReturn(dataVolume);
            when(dataVolume.volume()).thenReturn(fsVolume);
            when(fsVolume.getVolumeType()).thenReturn(FsVolumeType.STANDARD);
            when(mockStreamStore.openSource(meta, dataVolume)).thenReturn(source);

            doThrow(new IOException("Test")).when(source).close();

            // When & Then
            assertThatThrownBy(() -> storeImpl.getAttributes(metaId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Test");
        }
    }

    @Nested
    class GetAttributeMapForPart {

        @Test
        void testGetAttributeMapForPart_Success() throws IOException {


            // Given
            final long streamId = 1L;
            final long partNo = 1L;
            final Source source = mock(Source.class);
            final InputStreamProvider provider = mock(InputStreamProvider.class);
            final TestSegmentInputStream inputStream = new TestSegmentInputStream(
                    new ByteArrayInputStream("key1:value1\nkey2:value2\n".getBytes()), 100) {
            };
            when(provider.get(StreamTypeNames.META)).thenReturn(inputStream);
//            when(provider.get(anyString())).thenReturn(inputStream);
            final Meta meta = Meta.builder()
                    .id(streamId)
                    .build();
            when(mockMetaService.getMeta(streamId, false))
                    .thenReturn(meta);
            final DataVolume mockDataVolume = mock(DataVolume.class);
            final FsVolume mockFsVolume = mock(FsVolume.class);
            when(mockDataVolumeService.findDataVolume(streamId))
                    .thenReturn(mockDataVolume);
            when(mockDataVolume.volume()).thenReturn(mockFsVolume);
            when(mockFsVolume.getVolumeType()).thenReturn(FsVolumeType.STANDARD);
            when(mockStreamStore.openSource(meta, mockDataVolume))
                    .thenReturn(source);
            when(source.get(partNo))
                    .thenReturn(provider);

            // When
            final AttributeMap result = storeImpl.getAttributeMapForPart(streamId, 1L);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.get("key1")).isEqualTo("value1");
            assertThat(result.get("key2")).isEqualTo("value2");
            verify(source).close();
        }

        @Test
        void testGetAttributeMapForPart_SmallStream() throws IOException {
            // Given
            final long streamId = 1L;
            final Source source = mock(Source.class);
            final InputStreamProvider provider = mock(InputStreamProvider.class);
            final TestSegmentInputStream inputStream = new TestSegmentInputStream(
                    new ByteArrayInputStream("too small".getBytes()), 5) {
            };
//            when(provider.get(anyString())).thenReturn(inputStream);
            final Meta meta = Meta.builder()
                    .id(streamId)
                    .build();
            when(mockMetaService.getMeta(streamId, false))
                    .thenReturn(meta);
            final DataVolume mockDataVolume = mock(DataVolume.class);
            final FsVolume mockFsVolume = mock(FsVolume.class);
            when(mockDataVolumeService.findDataVolume(streamId))
                    .thenReturn(mockDataVolume);
            when(mockDataVolume.volume()).thenReturn(mockFsVolume);
            when(mockFsVolume.getVolumeType()).thenReturn(FsVolumeType.STANDARD);
            when(mockStreamStore.openSource(meta, mockDataVolume))
                    .thenReturn(source);

            // When
            final AttributeMap result = storeImpl.getAttributeMapForPart(streamId, 1L);

            // Then
            assertThat(result).isEmpty();
        }

        @Test
        void testGetAttributeMapForPart_IOException() throws IOException {
            // Given
            final long streamId = 1L;
            final Source source = mock(Source.class);

            // Mock openSource behaviour
            final Meta meta = mock(Meta.class);
            final DataVolume dataVolume = mock(DataVolume.class);
            final FsVolume fsVolume = mock(FsVolume.class);
            when(mockMetaService.getMeta(anyLong(), anyBoolean())).thenReturn(meta);
            when(mockDataVolumeService.findDataVolume(anyLong())).thenReturn(dataVolume);
            when(dataVolume.volume()).thenReturn(fsVolume);
            when(fsVolume.getVolumeType()).thenReturn(FsVolumeType.STANDARD);
            when(mockStreamStore.openSource(any(), any())).thenReturn(source);

            doThrow(new IOException("Test")).when(source).close();

            // When & Then
            assertThatThrownBy(() -> storeImpl.getAttributeMapForPart(streamId, 1L))
                    .isInstanceOf(UncheckedIOException.class);
        }
    }

    @Nested
    class GetStreamStore {

        @Test
        void testGetStreamStore_Success() {
            // When
            final StreamStore result = storeImpl.getStreamStore(FsVolumeType.STANDARD);

            // Then
            assertThat(result).isEqualTo(mockStreamStore);
        }

        @Test
        void testGetStreamStore_Null() {
            // When & Then
            assertThatThrownBy(() -> storeImpl.getStreamStore(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void testGetStreamStore_NotFound() {
            // When & Then
            assertThatThrownBy(() -> storeImpl.getStreamStore(FsVolumeType.S3_V1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No StreamStore for S3 v1");
        }
    }
}
