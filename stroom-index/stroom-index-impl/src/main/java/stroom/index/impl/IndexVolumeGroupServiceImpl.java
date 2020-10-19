package stroom.index.impl;

import stroom.index.impl.selection.VolumeConfig;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeGroup;
import stroom.security.api.ProcessingUserIdentityProvider;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.AuditUtil;
import stroom.util.NextNameGenerator;
import stroom.util.io.FileUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IndexVolumeGroupServiceImpl implements IndexVolumeGroupService {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexVolumeGroupServiceImpl.class);
    private final IndexVolumeGroupDao indexVolumeGroupDao;
    private final IndexVolumeDao indexVolumeDao;
    private final SecurityContext securityContext;
    private final VolumeConfig volumeConfig;
    private final ProcessingUserIdentityProvider processingUserIdentityProvider;
    private final TempDirProvider tempDirProvider;

    private volatile boolean createdDefaultVolumes;
    private volatile boolean creatingDefaultVolumes;

    @Inject
    public IndexVolumeGroupServiceImpl(final IndexVolumeGroupDao indexVolumeGroupDao,
                                       final IndexVolumeDao indexVolumeDao,
                                       final SecurityContext securityContext,
                                       final VolumeConfig volumeConfig,
                                       final ProcessingUserIdentityProvider processingUserIdentityProvider,
                                       final TempDirProvider tempDirProvider) {
        this.indexVolumeGroupDao = indexVolumeGroupDao;
        this.indexVolumeDao = indexVolumeDao;
        this.securityContext = securityContext;
        this.volumeConfig = volumeConfig;
        this.processingUserIdentityProvider = processingUserIdentityProvider;
        this.tempDirProvider = tempDirProvider;
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
        return securityContext.secureResult(() -> indexVolumeGroupDao.get(id));
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
                    indexVolumesInGroup.forEach(indexVolume -> indexVolumeDao.delete(indexVolume.getId()));
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
                    final boolean isEnabled = volumeConfig.isCreateDefaultIndexVolumesOnStart();
                    if (isEnabled) {
                        List<String> allVolGroups = getNames();

                        if (allVolGroups == null || allVolGroups.size() == 0) {
                            if (volumeConfig.getDefaultIndexVolumeGroupName() != null) {
                                final IndexVolumeGroup indexVolumeGroup = new IndexVolumeGroup();
                                final String processingUserId = processingUserIdentityProvider.get().getId();
                                final String groupName = volumeConfig.getDefaultIndexVolumeGroupName();
                                indexVolumeGroup.setName(groupName);
                                AuditUtil.stamp(processingUserId, indexVolumeGroup);

                                LOGGER.info("Creating default index volume group [{}]", groupName);
                                IndexVolumeGroup newGroup = indexVolumeGroupDao.getOrCreate(indexVolumeGroup);

                                //Now create associated volumes within the group
                                if (volumeConfig.getDefaultIndexVolumeGroupPaths() != null &&
                                        volumeConfig.getDefaultIndexVolumeGroupNodes() != null) {

                                    final List<String> paths = volumeConfig.getDefaultIndexVolumeGroupPaths();
                                    final List<String> nodes = volumeConfig.getDefaultIndexVolumeGroupNodes();
                                    if (nodes.size() == paths.size()) {
                                        int i = 0;
                                        for (String path : paths) {
                                            Path resolvedPath = getDefaultVolumesPath().get().resolve(path.trim());

                                            LOGGER.info("Creating index volume with path {}",
                                                    resolvedPath.toAbsolutePath().normalize());

                                            OptionalLong byteLimitOption = getDefaultVolumeLimit(resolvedPath.toString());

                                            IndexVolume indexVolume = new IndexVolume();
                                            indexVolume.setIndexVolumeGroupId(newGroup.getId());
                                            indexVolume.setBytesLimit(byteLimitOption.orElse(0l));
                                            indexVolume.setNodeName(nodes.get(i++));
                                            indexVolume.setPath(resolvedPath.toString());
                                            indexVolume.setCreateTimeMs(System.currentTimeMillis());
                                            indexVolume.setUpdateTimeMs(System.currentTimeMillis());
                                            indexVolume.setCreateUser(processingUserId);
                                            indexVolume.setUpdateUser(processingUserId);

                                            indexVolumeDao.create(indexVolume);
                                        }
                                    } else {
                                        LOGGER.error(() -> "Unable to create default index volume group. " +
                                                "Properties defaultVolumeGroupPaths defaultVolumeGroupNodes " +
                                                "and defaultVolumeGroupLimit must both contain the same number of " +
                                                "comma-delimited elements.");
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
                            LOGGER.info(() -> "Existing index volumes exist, won't create default index group");
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


    private Optional<Path> getDefaultVolumesPath() {
        return Stream.<Supplier<Optional<Path>>>of(
                this::getApplicationJarDir,
                this::getDotStroomDir,
                () -> Optional.of(tempDirProvider.get()),
                Optional::empty
        )
                .map(Supplier::get)
                .filter(Optional::isPresent)
                .findFirst()
                .map(Optional::get);
    }

    private Optional<Path> getDotStroomDir() {
        final String userHome = System.getProperty("user.home");
        if (userHome == null) {
            return Optional.empty();
        } else {
            final Path dotStroomDir = Paths.get(userHome)
                    .resolve(".stroom");
            if (Files.isDirectory(dotStroomDir)) {
                return Optional.of(dotStroomDir);
            } else {
                return Optional.empty();
            }
        }
    }

    private OptionalLong getDefaultVolumeLimit(final String path) {
        try {
            File parentDir = new File(path);
            parentDir.mkdirs();
            long totalBytes = Files.getFileStore(Path.of(path)).getTotalSpace();
            // set an arbitrary limit of 90% of the filesystem total size to ensure we don't fill up the
            // filesystem.  Limit can be configured from within stroom.
            // Should be noted that although if you have multiple volumes on a filesystem the limit will apply
            // to all volumes and any other data on the filesystem. I.e. once the amount of the filesystem in use
            // is greater than the limit writes to those volumes will be prevented. See Volume.isFull() and
            // this.updateVolumeState()
            return OptionalLong.of((long) (totalBytes * volumeConfig.getDefaultIndexVolumeFilesystemUtilisation()));
        } catch (IOException e) {
            LOGGER.warn(() -> LogUtil.message("Unable to determine the total space on the filesystem for path: {}." +
                    " Please manually set limit for index volume.", FileUtil.getCanonicalPath(Path.of(path))));
            return OptionalLong.empty();
        }
    }

    private Optional<Path> getApplicationJarDir() {
        try {
            String codeSourceLocation = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
            if (Pattern.matches(".*/stroom[^/]*.jar$", codeSourceLocation)) {
                return Optional.of(Paths.get(codeSourceLocation).getParent());
            } else {
                return Optional.empty();
            }
        } catch (final RuntimeException e) {
            LOGGER.warn(() -> LogUtil.message("Unable to determine application jar directory due to: {}", e.getMessage()));
            return Optional.empty();
        }
    }
}
