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

package stroom.data.store.impl.fs;

import stroom.data.store.api.FsVolumeGroupService;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.AuditUtil;
import stroom.util.NextNameGenerator;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
@EntityEventHandler(type = FsVolumeGroupServiceImpl.ENTITY_TYPE, action = {
        EntityAction.UPDATE,
        EntityAction.CREATE,
        EntityAction.DELETE})
public class FsVolumeGroupServiceImpl implements FsVolumeGroupService, Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsVolumeGroupServiceImpl.class);

    private final FsVolumeGroupDao volumeGroupDao;
    private final SecurityContext securityContext;
    private final Provider<FsVolumeConfig> volumeConfigProvider;
    private final Provider<EntityEventBus> entityEventBusProvider;

    private volatile boolean createdDefaultVolumes;
    private volatile boolean creatingDefaultVolumes;

    @Inject
    public FsVolumeGroupServiceImpl(final FsVolumeGroupDao volumeGroupDao,
                                    final SecurityContext securityContext,
                                    final Provider<FsVolumeConfig> volumeConfigProvider,
                                    final Provider<EntityEventBus> entityEventBusProvider) {
        this.volumeGroupDao = volumeGroupDao;
        this.securityContext = securityContext;
        this.volumeConfigProvider = volumeConfigProvider;
        this.entityEventBusProvider = entityEventBusProvider;
        ensureDefaultVolumes();
    }

    @Override
    public List<String> getNames() {
        ensureDefaultVolumes();
        return securityContext.secureResult(volumeGroupDao::getNames);
    }

    @Override
    public List<FsVolumeGroup> getAll() {
        ensureDefaultVolumes();
        return securityContext.secureResult(volumeGroupDao::getAll);
    }

    @Override
    public FsVolumeGroup getOrCreate(final String name) {
        ensureDefaultVolumes();
        final FsVolumeGroup indexVolumeGroup = new FsVolumeGroup();
        indexVolumeGroup.setName(name);
        AuditUtil.stamp(securityContext, indexVolumeGroup);
        final FsVolumeGroup result = securityContext.secureResult(AppPermission.MANAGE_VOLUMES_PERMISSION,
                () -> volumeGroupDao.getOrCreate(indexVolumeGroup));
        fireChange(EntityAction.CREATE);
        return result;
    }

    @Override
    public FsVolumeGroup create() {
        ensureDefaultVolumes();
        final FsVolumeGroup indexVolumeGroup = new FsVolumeGroup();
        final String newName = NextNameGenerator.getNextName(volumeGroupDao.getNames(), "New group");
        indexVolumeGroup.setName(newName);
        AuditUtil.stamp(securityContext, indexVolumeGroup);
        final FsVolumeGroup result = securityContext.secureResult(AppPermission.MANAGE_VOLUMES_PERMISSION,
                () -> volumeGroupDao.getOrCreate(indexVolumeGroup));
        fireChange(EntityAction.CREATE);
        return result;
    }

    @Override
    public FsVolumeGroup update(final FsVolumeGroup indexVolumeGroup) {
        ensureDefaultVolumes();
        AuditUtil.stamp(securityContext, indexVolumeGroup);
        final FsVolumeGroup result = securityContext.secureResult(AppPermission.MANAGE_VOLUMES_PERMISSION,
                () -> volumeGroupDao.update(indexVolumeGroup));
        fireChange(EntityAction.UPDATE);
        return result;
    }

    @Override
    public FsVolumeGroup get(final String name) {
        ensureDefaultVolumes();
        return securityContext.secureResult(() -> volumeGroupDao.get(name));
    }

    @Override
    public FsVolumeGroup get(final int id) {
        ensureDefaultVolumes();
        return securityContext.secureResult(() ->
                volumeGroupDao.get(id));
    }

    @Override
    public void delete(final int id) {
        securityContext.secure(AppPermission.MANAGE_VOLUMES_PERMISSION,
                () -> {
//                    //TODO Transaction?
//                    var indexVolumesInGroup = volumeDao.getAll().stream()
//                            .filter(indexVolume ->
//                                    indexVolume.getVolumeGroupId().equals(id))
//                            .toList();
//                    indexVolumesInGroup.forEach(indexVolume ->
//                            volumeDao.delete(indexVolume.getId()));
                    volumeGroupDao.delete(id);
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
        return Optional.ofNullable(volumeConfigProvider.get().getDefaultStreamVolumeGroupName());
    }

    private synchronized void createDefaultVolumes() {
        if (!createdDefaultVolumes && !creatingDefaultVolumes) {
            try {
                creatingDefaultVolumes = true;
                securityContext.insecure(() -> {
                    final FsVolumeConfig volumeConfig = volumeConfigProvider.get();
                    final boolean isEnabled = volumeConfig.isCreateDefaultStreamVolumesOnStart();
                    if (isEnabled) {
                        if (volumeConfig.getDefaultStreamVolumeGroupName() != null) {
                            final FsVolumeGroup indexVolumeGroup = new FsVolumeGroup();
                            final String groupName = volumeConfig.getDefaultStreamVolumeGroupName();
                            indexVolumeGroup.setName(groupName);
                            AuditUtil.stamp(securityContext, indexVolumeGroup);

                            LOGGER.info("Creating default volume group [{}]", groupName);
//                            final FsVolumeGroup newGroup = volumeGroupDao.getOrCreate(indexVolumeGroup);

//                            // Now create associated volumes within the group
//                            if (volumeConfig.getDefaultStreamVolumePaths() != null) {
//                                final String nodeName = nodeInfo.getThisNodeName();
//
//                                // See if we have already created a volume for this node.
//                                final List<FsVolume> existingVolumesInGroup =
//                                        volumeDao.getVolumesInGroup(groupName);
//                                final boolean exists = existingVolumesInGroup
//                                        .stream()
//                                        .map(FsVolume::getNodeName)
//                                        .anyMatch(name -> name.equals(nodeName));
//                                if (!exists) {
//                                    final List<String> paths = volumeConfig.getDefaultFsVolumeGroupPaths();
//                                    for (String path : paths) {
//                                        final Path resolvedPath = pathCreator.toAppPath(path);
//
//                                        LOGGER.info("Creating index volume with path {}",
//                                                resolvedPath.toAbsolutePath().normalize());
//
//                                        final OptionalLong byteLimitOption = getDefaultVolumeLimit(
//                                                resolvedPath.toString());
//
//                                        final IndexVolume indexVolume = new IndexVolume();
//                                        indexVolume.setFsVolumeGroupId(newGroup.getId());
//                                        indexVolume.setBytesLimit(byteLimitOption.orElse(0L));
//                                        indexVolume.setNodeName(nodeName);
//                                        indexVolume.setPath(resolvedPath.toString());
//                                        AuditUtil.stamp(processingUserIdentity, indexVolume);
//
//                                        volumeDao.create(indexVolume);
//                                    }
//                                }
//                            } else {
//                                LOGGER.warn(() -> "Unable to create default index volume group. " +
//                                        "Properties defaultVolumeGroupPaths defaultVolumeGroupNodes " +
//                                        "and defaultVolumeGroupLimit must all be defined.");
//                            }
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

//    private OptionalLong getDefaultVolumeLimit(final String path) {
//        try {
//            final File parentDir = new File(path);
//            parentDir.mkdirs();
//            long totalBytes = Files.getFileStore(Path.of(path)).getTotalSpace();
//            // set an arbitrary limit of 90% of the filesystem total size to ensure we don't fill up the
//            // filesystem.  Limit can be configured from within stroom.
//            // Should be noted that although if you have multiple volumes on a filesystem the limit will apply
//            // to all volumes and any other data on the filesystem. I.e. once the amount of the filesystem in use
//            // is greater than the limit writes to those volumes will be prevented. See Volume.isFull() and
//            // this.updateVolumeState()
//            return OptionalLong.of(
//                    (long) (totalBytes * volumeConfigProvider.get().getDefaultIndexVolumeFilesystemUtilisation()));
//        } catch (IOException e) {
//            LOGGER.warn(() -> LogUtil.message("Unable to determine the total space on the filesystem for path: {}." +
//                    " Please manually set limit for index volume. {}",
//                    FileUtil.getCanonicalPath(Path.of(path)), e.getMessage()));
//            return OptionalLong.empty();
//        }
//    }

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
