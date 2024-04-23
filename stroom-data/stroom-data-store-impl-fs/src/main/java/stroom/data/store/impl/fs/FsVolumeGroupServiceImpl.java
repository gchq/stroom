package stroom.data.store.impl.fs;

import stroom.data.store.api.FsVolumeGroupService;
import stroom.data.store.impl.fs.shared.FsVolumeGroup;
import stroom.data.store.impl.fs.shared.FsVolumeGroup.Builder;
import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.AuditUtil;
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
import java.util.Objects;

@Singleton
@EntityEventHandler(type = FsVolumeGroup.DOCUMENT_TYPE, action = {
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

//    @Override
//    public List<String> getNames() {
//        ensureDefaultVolumes();
//        return securityContext.secureResult(volumeGroupDao::getNames);
//    }

    @Override
    public List<FsVolumeGroup> getAll() {
        ensureDefaultVolumes();
        return securityContext.secureResult(volumeGroupDao::getAll);
    }

    @Override
    public FsVolumeGroup create(final FsVolumeGroup fsVolumeGroup) {
        ensureDefaultVolumes();
        Objects.requireNonNull(fsVolumeGroup);
        final Builder builder = fsVolumeGroup.copy();
        if (fsVolumeGroup.getUuid() == null) {
            builder.withRandomUuid();
        }
        final FsVolumeGroup copy = builder.build();
        AuditUtil.stamp(securityContext, copy);

        final FsVolumeGroup fsVolumeGroup2 = securityContext.secureResult(() ->
                volumeGroupDao.create(copy));

        fireChange(EntityAction.CREATE, fsVolumeGroup2);
        return fsVolumeGroup2;
    }

    @Override
    public FsVolumeGroup getOrCreate(final DocRef docRef, final boolean isDefaultVolumeGroup) {
        Objects.requireNonNull(docRef);
        ensureDefaultVolumes();
        final FsVolumeGroup indexVolumeGroup = new FsVolumeGroup();
        indexVolumeGroup.setName(docRef.getName());
        indexVolumeGroup.setUuid(docRef.getUuid());
        indexVolumeGroup.setDefaultVolume(isDefaultVolumeGroup);
        AuditUtil.stamp(securityContext, indexVolumeGroup);
        final FsVolumeGroup result = securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> volumeGroupDao.getOrCreate(indexVolumeGroup));
        fireChange(EntityAction.CREATE, result);
        return result;
    }

//    @Override
//    public FsVolumeGroup create() {
//        ensureDefaultVolumes();
//        final FsVolumeGroup indexVolumeGroup = new FsVolumeGroup();
//        var newName = NextNameGenerator.getNextName(volumeGroupDao.getNames(), "New group");
//        indexVolumeGroup.setName(newName);
//        AuditUtil.stamp(securityContext, indexVolumeGroup);
//        final FsVolumeGroup result = securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
//                () -> volumeGroupDao.getOrCreate(indexVolumeGroup));
//        fireChange(EntityAction.CREATE);
//        return result;
//    }

    @Override
    public FsVolumeGroup update(final FsVolumeGroup indexVolumeGroup) {
        ensureDefaultVolumes();
        AuditUtil.stamp(securityContext, indexVolumeGroup);
        final FsVolumeGroup result = securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> volumeGroupDao.update(indexVolumeGroup));
        fireChange(EntityAction.UPDATE, result);
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
    public FsVolumeGroup get(final DocRef docRef) {
        ensureDefaultVolumes();
        Objects.requireNonNull(docRef);
        return securityContext.secureResult(() ->
                volumeGroupDao.get(docRef));
    }

    @Override
    public FsVolumeGroup getDefaultVolumeGroup() {
        ensureDefaultVolumes();
        return securityContext.secureResult(volumeGroupDao::getDefaultVolumeGroup);
    }

    @Override
    public void delete(int id) {
        securityContext.secure(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> {
                    final FsVolumeGroup fsVolumeGroup = volumeGroupDao.get(id);
                    if (fsVolumeGroup != null) {
                        volumeGroupDao.delete(id);
                        fireChange(EntityAction.DELETE, fsVolumeGroup);
                    }
                });
    }

    public void ensureDefaultVolumes() {
        if (!createdDefaultVolumes) {
            createDefaultVolumes();
        }
    }

    @Override
    public List<FsVolumeGroup> find(final List<String> nameFilters,
                                    final boolean allowWildCards) {
        return volumeGroupDao.find(nameFilters, allowWildCards);
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
                            final FsVolumeGroup fsVolumeGroup = new FsVolumeGroup();
                            final String groupName = volumeConfig.getDefaultStreamVolumeGroupName();
                            fsVolumeGroup.setName(groupName);
                            fsVolumeGroup.setDefaultVolume(true);
                            fsVolumeGroup.setUuid(FsVolumeGroup.DEFAULT_VOLUME_UUID);
                            AuditUtil.stamp(securityContext, fsVolumeGroup);

                            LOGGER.info("Creating default volume group [{}]", groupName);
                            volumeGroupDao.getOrCreate(fsVolumeGroup);
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

    private void fireChange(final EntityAction action, final FsVolumeGroup fsVolumeGroup) {
        if (entityEventBusProvider != null) {
            try {
                final EntityEventBus entityEventBus = entityEventBusProvider.get();
                if (entityEventBus != null) {
                    entityEventBus.fire(new EntityEvent(fsVolumeGroup.asDocRef(), action));
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
