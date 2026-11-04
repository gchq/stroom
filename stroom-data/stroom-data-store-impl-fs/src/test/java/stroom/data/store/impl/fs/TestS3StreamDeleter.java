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

package stroom.data.store.impl.fs;

import stroom.aws.s3.impl.S3Manager;
import stroom.aws.s3.shared.S3ClientConfig;
import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.meta.shared.SimpleMeta;
import stroom.meta.shared.SimpleMetaImpl;
import stroom.util.time.TimeBasis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestS3StreamDeleter {

    @Mock
    private MetaService metaService;
    @Mock
    private S3Store s3Store;
    @Mock
    private DataVolume dataVolume;
    @Mock
    private FsVolume volume;
    @Mock
    private S3ClientConfig s3ClientConfig;
    @Mock
    private S3Manager s3Manager;

    @Test
    void shouldDeleteS3ObjectAndCache() {
        final SimpleMeta simpleMeta = createMeta(10L);
        final Meta meta = new Meta();
        final Function<S3ClientConfig, S3Manager> s3ManagerFactory = ignored -> s3Manager;
        final S3StreamDeleter deleter = new S3StreamDeleter(metaService, s3Store, s3ManagerFactory);

        when(metaService.getMeta(simpleMeta.getId(), true)).thenReturn(meta);
        when(dataVolume.getVolume()).thenReturn(volume);
        when(volume.getS3ClientConfig()).thenReturn(s3ClientConfig);
        when(s3Store.delete(simpleMeta.getId())).thenReturn(true);

        final boolean result = deleter.delete(dataVolume, simpleMeta);

        assertThat(result).isTrue();
        verify(s3Manager).delete(meta, TimeBasis.META_CREATION_TIME);
        verify(s3Store).delete(simpleMeta.getId());
    }

    @Test
    void shouldFailWhenMetaCannotBeFound() {
        final SimpleMeta simpleMeta = createMeta(10L);
        final S3StreamDeleter deleter = new S3StreamDeleter(
                metaService,
                s3Store,
                ignored -> s3Manager);

        when(metaService.getMeta(simpleMeta.getId(), true)).thenReturn(null);

        final boolean result = deleter.delete(dataVolume, simpleMeta);

        assertThat(result).isFalse();
        verify(s3Store, never()).delete(simpleMeta.getId());
        verify(s3Manager, never()).delete(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldNotDeleteCacheWhenS3DeletionFails() {
        final SimpleMeta simpleMeta = createMeta(10L);
        final Meta meta = new Meta();
        final S3StreamDeleter deleter = new S3StreamDeleter(
                metaService,
                s3Store,
                ignored -> s3Manager);

        when(metaService.getMeta(simpleMeta.getId(), true)).thenReturn(meta);
        when(dataVolume.getVolume()).thenReturn(volume);
        when(volume.getS3ClientConfig()).thenReturn(s3ClientConfig);
        when(s3Manager.delete(meta, TimeBasis.META_CREATION_TIME))
                .thenThrow(new RuntimeException("S3 unavailable"));

        final boolean result = deleter.delete(dataVolume, simpleMeta);

        assertThat(result).isFalse();
        verify(s3Store, never()).delete(simpleMeta.getId());
    }

    @Test
    void shouldFailWhenCacheCannotBeDeleted() {
        final SimpleMeta simpleMeta = createMeta(10L);
        final Meta meta = new Meta();
        final S3StreamDeleter deleter = new S3StreamDeleter(
                metaService,
                s3Store,
                ignored -> s3Manager);

        when(metaService.getMeta(simpleMeta.getId(), true)).thenReturn(meta);
        when(dataVolume.getVolume()).thenReturn(volume);
        when(volume.getS3ClientConfig()).thenReturn(s3ClientConfig);
        when(s3Store.delete(simpleMeta.getId())).thenReturn(false);

        final boolean result = deleter.delete(dataVolume, simpleMeta);

        assertThat(result).isFalse();
        verify(s3Manager).delete(meta, TimeBasis.META_CREATION_TIME);
    }

    private SimpleMeta createMeta(final long id) {
        return SimpleMetaImpl.builder()
                .id(id)
                .feedName("FEED")
                .typeName("TYPE")
                .createMs(1000L)
                .statusMs(1000L)
                .build();
    }
}
