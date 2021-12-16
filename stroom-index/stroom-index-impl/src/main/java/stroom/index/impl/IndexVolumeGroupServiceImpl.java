package stroom.index.impl;

import stroom.index.impl.selection.VolumeConfig;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeGroup;
import stroom.node.api.NodeInfo;
import stroom.security.api.ProcessingUserIdentityProvider;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.AuditUtil;
import stroom.util.NextNameGenerator;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalLong;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class IndexVolumeGroupServiceImpl implements IndexVolumeGroupService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexVolumeGroupServiceImpl.class);
    private final IndexVolumeGroupDao indexVolumeGroupDao;
    private final IndexVolumeDao indexVolumeDao;
    private final SecurityContext securityContext;
    private final Provider<VolumeConfig> volumeConfigProvider;
    private final ProcessingUserIdentityProvider processingUserIdentityProvider;
    private final PathCreator pathCreator;
    private final NodeInfo nodeInfo;

    private volatile boolean createdDefaultVolumes;
    private volatile boolean creatingDefaultVolumes;

    @Inject
    public IndexVolumeGroupServiceImpl(final IndexVolumeGroupDao indexVolumeGroupDao,
                                       final IndexVolumeDao indexVolumeDao,
                                       final SecurityContext securityContext,
                                       final Provider<VolumeConfig> volumeConfigProvider,
                                       final ProcessingUserIdentityProvider processingUserIdentityProvider,
                                       final PathCreator pathCreator,
                                       final NodeInfo nodeInfo) {
        this.indexVolumeGroupDao = indexVolumeGroupDao;
        this.indexVolumeDao = indexVolumeDao;
        this.securityContext = securityContext;
        this.volumeConfigProvider = volumeConfigProvider;
        this.processingUserIdentityProvider = processingUserIdentityProvider;
        this.pathCreator = pathCreator;
        this.nodeInfo = nodeInfo;
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
        AuditUtil.stamp(securityContext.getUserId(), indexVolumeGroup);
        return securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeGroupDao.getOrCreate(indexVolumeGroup));
    }

    @Override
    public IndexVolumeGroup create() {
        ensureDefaultVolumes();
        final IndexVolumeGroup indexVolumeGroup = new IndexVolumeGroup();
        var newName = NextNameGenerator.getNextName(indexVolumeGroupDao.getNames(), "New group");
        indexVolumeGroup.setName(newName);
        AuditUtil.stamp(securityContext.getUserId(), indexVolumeGroup);
        return securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeGroupDao.getOrCreate(indexVolumeGroup));
    }

    @Override
    public IndexVolumeGroup update(final IndexVolumeGroup indexVolumeGroup) {
        ensureDefaultVolumes();
        AuditUtil.stamp(securityContext.getUserId(), indexVolumeGroup);
        return securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeGroupDao.update(indexVolumeGroup));
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
    public void delete(int id) {
        securityContext.secure(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> {
                    //TODO Transaction?
                    var indexVolumesInGroup = indexVolumeDao.getAll().stream()
                            .filter(indexVolume ->
                                    indexVolume.getIndexVolumeGroupId().equals(id))
                            .collect(Collectors.toList());
                    indexVolumesInGroup.forEach(indexVolume ->
                            indexVolumeDao.delete(indexVolume.getId()));
                    indexVolumeGroupDao.delete(id);
                });
    }

    private void ensureDefaultVolumes() {
        if (!createdDefaultVolumes) {
            createDefaultVolumes();
        }
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
                            final String processingUserId = processingUserIdentityProvider.get().getId();
                            final String groupName = volumeConfig.getDefaultIndexVolumeGroupName();
                            indexVolumeGroup.setName(groupName);
                            AuditUtil.stamp(processingUserId, indexVolumeGroup);

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
                                    for (String path : paths) {
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
                                        indexVolume.setCreateTimeMs(System.currentTimeMillis());
                                        indexVolume.setUpdateTimeMs(System.currentTimeMillis());
                                        indexVolume.setCreateUser(processingUserId);
                                        indexVolume.setUpdateUser(processingUserId);

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
            long totalBytes = Files.getFileStore(Path.of(path)).getTotalSpace();
            // set an arbitrary limit of 90% of the filesystem total size to ensure we don't fill up the
            // filesystem.  Limit can be configured from within stroom.
            // Should be noted that although if you have multiple volumes on a filesystem the limit will apply
            // to all volumes and any other data on the filesystem. I.e. once the amount of the filesystem in use
            // is greater than the limit writes to those volumes will be prevented. See Volume.isFull() and
            // this.updateVolumeState()
            return OptionalLong.of(
                    (long) (totalBytes * volumeConfigProvider.get().getDefaultIndexVolumeFilesystemUtilisation()));
        } catch (IOException e) {
            LOGGER.warn(() -> LogUtil.message("Unable to determine the total space on the filesystem for path: {}." +
                    " Please manually set limit for index volume.", FileUtil.getCanonicalPath(Path.of(path))));
            return OptionalLong.empty();
        }
    }
}
