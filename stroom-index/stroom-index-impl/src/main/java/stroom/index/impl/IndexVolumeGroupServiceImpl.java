/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.index.api.IndexVolumeGroupService;
import stroom.index.impl.selection.VolumeConfig;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeGroup;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.api.UserIdentityFactory;
import stroom.security.shared.AppPermission;
import stroom.util.AuditUtil;
import stroom.util.NextNameGenerator;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

@Singleton
@EntityEventHandler(type = IndexVolumeServiceImpl.ENTITY_TYPE, action = {
        EntityAction.UPDATE,
        EntityAction.CREATE,
        EntityAction.DELETE})
public class IndexVolumeGroupServiceImpl implements IndexVolumeGroupService, Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexVolumeGroupServiceImpl.class);

    private final IndexVolumeGroupDao indexVolumeGroupDao;
    private final IndexVolumeDao indexVolumeDao;
    private final SecurityContext securityContext;
    private final Provider<VolumeConfig> volumeConfigProvider;
    private final UserIdentityFactory userIdentityFactory;
    private final PathCreator pathCreator;
    private final NodeInfo nodeInfo;
    private final Provider<EntityEventBus> entityEventBusProvider;

    private volatile boolean createdDefaultVolumes;
    private volatile boolean creatingDefaultVolumes;

    @Inject
    public IndexVolumeGroupServiceImpl(final IndexVolumeGroupDao indexVolumeGroupDao,
                                       final IndexVolumeDao indexVolumeDao,
                                       final SecurityContext securityContext,
                                       final Provider<VolumeConfig> volumeConfigProvider,
                                       final UserIdentityFactory userIdentityFactory,
                                       final PathCreator pathCreator,
                                       final NodeInfo nodeInfo,
                                       final Provider<EntityEventBus> entityEventBusProvider) {
        this.indexVolumeGroupDao = indexVolumeGroupDao;
        this.indexVolumeDao = indexVolumeDao;
        this.securityContext = securityContext;
        this.volumeConfigProvider = volumeConfigProvider;
        this.userIdentityFactory = userIdentityFactory;
        this.pathCreator = pathCreator;
        this.nodeInfo = nodeInfo;
        this.entityEventBusProvider = entityEventBusProvider;
        ensureDefaultVolumes();
    }

    @Override
    public List<String> getNames() {
        ensureDefaultVolumes();
        return securityContext.secureResult(indexVolumeGroupDao::getNames);
    }

    @Override
    public List<IndexVolumeGroup> getAll() {
        ensureDefaultVolumes();
        return securityContext.secureResult(indexVolumeGroupDao::getAll);
    }

    @Override
    public IndexVolumeGroup getOrCreate(final String name) {
        ensureDefaultVolumes();
        final IndexVolumeGroup indexVolumeGroup = new IndexVolumeGroup();
        indexVolumeGroup.setName(name);
        AuditUtil.stamp(securityContext, indexVolumeGroup);
        final IndexVolumeGroup result = securityContext.secureResult(AppPermission.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeGroupDao.getOrCreate(indexVolumeGroup));
        fireChange(EntityAction.CREATE);
        return result;
    }

    @Override
    public IndexVolumeGroup create() {
        ensureDefaultVolumes();
        final IndexVolumeGroup indexVolumeGroup = new IndexVolumeGroup();
        final String newName = NextNameGenerator.getNextName(indexVolumeGroupDao.getNames(), "New group");
        indexVolumeGroup.setName(newName);
        AuditUtil.stamp(securityContext, indexVolumeGroup);
        final IndexVolumeGroup result = securityContext.secureResult(AppPermission.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeGroupDao.getOrCreate(indexVolumeGroup));
        fireChange(EntityAction.CREATE);
        return result;
    }

    @Override
    public IndexVolumeGroup update(final IndexVolumeGroup indexVolumeGroup) {
        ensureDefaultVolumes();
        AuditUtil.stamp(securityContext, indexVolumeGroup);
        final IndexVolumeGroup result = securityContext.secureResult(AppPermission.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeGroupDao.update(indexVolumeGroup));
        fireChange(EntityAction.UPDATE);
        return result;
    }

    @Override
    public IndexVolumeGroup get(final String name) {
        ensureDefaultVolumes();
        return securityContext.secureResult(() -> indexVolumeGroupDao.get(name));
    }

    @Override
    public IndexVolumeGroup get(final int id) {
        ensureDefaultVolumes();
        return securityContext.secureResult(() ->
                indexVolumeGroupDao.get(id));
    }

    @Override
    public void delete(final int id) {
        securityContext.secure(AppPermission.MANAGE_VOLUMES_PERMISSION,
                () -> {
                    //TODO Transaction?
                    final List<IndexVolume> indexVolumesInGroup = indexVolumeDao.getAll()
                            .stream()
                            .filter(indexVolume ->
                                    indexVolume.getIndexVolumeGroupId().equals(id))
                            .toList();
                    indexVolumesInGroup.forEach(indexVolume ->
                            indexVolumeDao.delete(indexVolume.getId()));
                    indexVolumeGroupDao.delete(id);
                });
        fireChange(EntityAction.DELETE);
    }

    public void ensureDefaultVolumes() {
        if (!createdDefaultVolumes) {
            createDefaultVolumes();
        }
    }

    @Override
    public Optional<String> getDefaultVolumeGroup() {
        return Optional.ofNullable(volumeConfigProvider.get().getDefaultIndexVolumeGroupName());
    }

    private synchronized void createDefaultVolumes() {
        if (!createdDefaultVolumes && !creatingDefaultVolumes) {
            try {
                creatingDefaultVolumes = true;
                securityContext.insecure(() -> {
                    final VolumeConfig volumeConfig = volumeConfigProvider.get();
                    final boolean isEnabled = volumeConfig.isCreateDefaultIndexVolumesOnStart();
                    if (isEnabled) {
                        if (volumeConfig.getDefaultIndexVolumeGroupName() != null) {
                            final IndexVolumeGroup indexVolumeGroup = new IndexVolumeGroup();
                            final UserIdentity processingUserIdentity = userIdentityFactory.getServiceUserIdentity();
                            final String groupName = volumeConfig.getDefaultIndexVolumeGroupName();
                            indexVolumeGroup.setName(groupName);
                            AuditUtil.stamp(processingUserIdentity, indexVolumeGroup);

                            LOGGER.info("Creating default index volume group [{}]", groupName);
                            final IndexVolumeGroup newGroup = indexVolumeGroupDao.getOrCreate(indexVolumeGroup);

                            // Now create associated volumes within the group
                            if (volumeConfig.getDefaultIndexVolumeGroupPaths() != null) {
                                final String nodeName = nodeInfo.getThisNodeName();

                                // See if we have already created a volume for this node.
                                final List<IndexVolume> existingVolumesInGroup =
                                        indexVolumeDao.getVolumesInGroup(groupName);
                                final boolean exists = existingVolumesInGroup
                                        .stream()
                                        .map(IndexVolume::getNodeName)
                                        .anyMatch(name -> name.equals(nodeName));
                                if (!exists) {
                                    final List<String> paths = volumeConfig.getDefaultIndexVolumeGroupPaths();
                                    for (final String path : paths) {
                                        final Path resolvedPath = pathCreator.toAppPath(path);

                                        LOGGER.info("Creating index volume with path {}",
                                                resolvedPath.toAbsolutePath().normalize());

                                        final OptionalLong byteLimitOption = getDefaultVolumeLimit(
                                                resolvedPath.toString());

                                        final IndexVolume indexVolume = new IndexVolume();
                                        indexVolume.setIndexVolumeGroupId(newGroup.getId());
                                        indexVolume.setBytesLimit(byteLimitOption.orElse(0L));
                                        indexVolume.setNodeName(nodeName);
                                        indexVolume.setPath(resolvedPath.toString());
                                        AuditUtil.stamp(processingUserIdentity, indexVolume);

                                        indexVolumeDao.create(indexVolume);
                                    }
                                }
                            } else {
                                LOGGER.warn(() -> "Unable to create default index volume group. " +
                                                  "Properties defaultVolumeGroupPaths defaultVolumeGroupNodes " +
                                                  "and defaultVolumeGroupLimit must all be defined.");
                            }
                        } else {
                            LOGGER.warn(() -> "Unable to create default index " +
                                              "Property defaultVolumeGroupName must be defined.");
                        }
                    } else {
                        LOGGER.info(() -> "Creation of default index group is currently disabled");
                    }
                });
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            } finally {
                createdDefaultVolumes = true;
                creatingDefaultVolumes = false;
            }
        }
    }

    private OptionalLong getDefaultVolumeLimit(final String path) {
        try {
            final File parentDir = new File(path);
            parentDir.mkdirs();
            final long totalBytes = Files.getFileStore(Path.of(path)).getTotalSpace();
            // set an arbitrary limit of 90% of the filesystem total size to ensure we don't fill up the
            // filesystem.  Limit can be configured from within stroom.
            // Should be noted that although if you have multiple volumes on a filesystem the limit will apply
            // to all volumes and any other data on the filesystem. I.e. once the amount of the filesystem in use
            // is greater than the limit writes to those volumes will be prevented. See Volume.isFull() and
            // this.updateVolumeState()
            return OptionalLong.of(
                    (long) (totalBytes * volumeConfigProvider.get().getDefaultIndexVolumeFilesystemUtilisation()));
        } catch (final IOException e) {
            LOGGER.warn(() -> LogUtil.message("Unable to determine the total space on the filesystem for path: {}." +
                                              " Please manually set limit for index volume. {}",
                    FileUtil.getCanonicalPath(Path.of(path)), e.getMessage()));
            return OptionalLong.empty();
        }
    }

    private void fireChange(final EntityAction action) {
        if (entityEventBusProvider != null) {
            try {
                final EntityEventBus entityEventBus = entityEventBusProvider.get();
                if (entityEventBus != null) {
                    entityEventBus.fire(new EntityEvent(EVENT_DOCREF, action));
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e::getMessage, e);
            }
        }
    }

    @Override
    public void clear() {
        createdDefaultVolumes = false;
        creatingDefaultVolumes = false;
    }
}
