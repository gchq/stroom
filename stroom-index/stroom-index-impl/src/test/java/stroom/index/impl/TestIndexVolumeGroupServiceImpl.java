/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.index.impl;

import stroom.index.impl.selection.VolumeConfig;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeGroup;
import stroom.node.api.NodeInfo;
import stroom.security.api.UserIdentityFactory;
import stroom.security.mock.MockSecurityContext;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.io.PathCreator;

import jakarta.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestIndexVolumeGroupServiceImpl {

    @Mock
    private IndexVolumeGroupDao indexVolumeGroupDao;

    @Mock
    private IndexVolumeDao indexVolumeDao;

    private final MockSecurityContext securityContext = new MockSecurityContext();

    @Mock
    private Provider<VolumeConfig> volumeConfigProvider;

    @Mock
    private VolumeConfig volumeConfig;

    @Mock
    private UserIdentityFactory userIdentityFactory;

    @Mock
    private PathCreator pathCreator;

    @Mock
    private NodeInfo nodeInfo;

    @Mock
    private Provider<EntityEventBus> entityEventBusProvider;

    @Mock
    private EntityEventBus entityEventBus;

    @BeforeEach
    void beforeEach() {
        when(volumeConfigProvider.get()).thenReturn(volumeConfig);
        when(volumeConfig.isCreateDefaultIndexVolumesOnStart()).thenReturn(false);
        lenient().when(entityEventBusProvider.get()).thenReturn(entityEventBus);
        lenient().when(entityEventBus.buildFiring()).thenCallRealMethod();
    }

    @Test
    void testUpdateFiresEventWithOldAndNewNames() {
        final IndexVolumeGroup existingGroup = group(1, "old-name");
        final IndexVolumeGroup inputGroup = group(1, "new-name");
        final IndexVolumeGroup updatedGroup = group(1, "new-name");
        when(indexVolumeGroupDao.get(1)).thenReturn(existingGroup);
        when(indexVolumeGroupDao.update(any(IndexVolumeGroup.class))).thenReturn(updatedGroup);

        final IndexVolumeGroupServiceImpl service = createService();

        assertThat(service.update(inputGroup)).isSameAs(updatedGroup);

        final EntityEvent event = capturedEvent();
        final IndexVolumeGroupServiceImpl.IndexVolumeGroupEntityEventData data =
                event.getDataObject(IndexVolumeGroupServiceImpl.IndexVolumeGroupEntityEventData.class);
        assertThat(event.getAction()).isEqualTo(EntityAction.UPDATE);
        assertThat(data.getOldGroupName()).isEqualTo("old-name");
        assertThat(data.getGroupName()).isEqualTo("new-name");
    }

    @Test
    void testDeleteDeletesGroupVolumesAndFiresEventWithGroupName() {
        final IndexVolumeGroup group = group(1, "group-name");
        final IndexVolume volume = IndexVolume.builder()
                .id(10)
                .indexVolumeGroupId(1)
                .build();
        when(indexVolumeGroupDao.get(1)).thenReturn(group);
        when(indexVolumeDao.getAll()).thenReturn(List.of(volume));

        final IndexVolumeGroupServiceImpl service = createService();

        service.delete(1);

        verify(indexVolumeDao).delete(10);
        verify(indexVolumeGroupDao).delete(1);
        final EntityEvent event = capturedEvent();
        final IndexVolumeGroupServiceImpl.IndexVolumeGroupEntityEventData data =
                event.getDataObject(IndexVolumeGroupServiceImpl.IndexVolumeGroupEntityEventData.class);
        assertThat(event.getAction()).isEqualTo(EntityAction.DELETE);
        assertThat(data.getOldGroupName()).isNull();
        assertThat(data.getGroupName()).isEqualTo("group-name");
    }

    @Test
    void testGetOrCreateExistingGroupDoesNotFireEvent() {
        final IndexVolumeGroup group = group(1, "group-name");
        when(indexVolumeGroupDao.get("group-name")).thenReturn(group);

        final IndexVolumeGroupServiceImpl service = createService();

        assertThat(service.getOrCreate("group-name")).isSameAs(group);

        verify(indexVolumeGroupDao, never()).getOrCreate(any(IndexVolumeGroup.class));
        verify(entityEventBus, never()).fire(any(EntityEvent.class));
    }

    @Test
    void testUpdateMissingGroupFailsWithoutUpdating() {
        final IndexVolumeGroup inputGroup = group(1, "new-name");
        when(indexVolumeGroupDao.get(1)).thenReturn(null);

        final IndexVolumeGroupServiceImpl service = createService();

        assertThatThrownBy(() -> service.update(inputGroup))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Index volume group cannot be null");

        verify(indexVolumeGroupDao, never()).update(any(IndexVolumeGroup.class));
    }

    private IndexVolumeGroupServiceImpl createService() {
        return new IndexVolumeGroupServiceImpl(
                indexVolumeGroupDao,
                indexVolumeDao,
                securityContext,
                volumeConfigProvider,
                userIdentityFactory,
                pathCreator,
                nodeInfo,
                entityEventBusProvider);
    }

    private EntityEvent capturedEvent() {
        final ArgumentCaptor<EntityEvent> eventCaptor = ArgumentCaptor.forClass(EntityEvent.class);
        verify(entityEventBus).fire(eventCaptor.capture());
        return eventCaptor.getValue();
    }

    private IndexVolumeGroup group(final int id, final String name) {
        return IndexVolumeGroup.builder()
                .id(id)
                .name(name)
                .build();
    }
}
