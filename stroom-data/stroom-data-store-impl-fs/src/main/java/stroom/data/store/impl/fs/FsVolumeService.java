package stroom.data.store.impl.fs;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolume.VolumeUseStatus;
import stroom.data.store.impl.fs.shared.FsVolumeState;
import stroom.docref.DocRef;
import stroom.util.io.PathCreator;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.statistics.api.InternalStatisticEvent;
import stroom.statistics.api.InternalStatisticKey;
import stroom.statistics.api.InternalStatisticsReceiver;
import stroom.util.AuditUtil;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.Flushable;
import stroom.util.shared.ResultPage;

import com.google.common.collect.ImmutableMap;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TODO JC: I'm not clear what currentVolumeList is for. Add comments?
 */
@Singleton
@EntityEventHandler(type = FsVolumeService.ENTITY_TYPE, action = {EntityAction.CREATE, EntityAction.DELETE})
public class FsVolumeService implements EntityEvent.Handler, Clearable, Flushable {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsVolumeService.class);

    private static final String LOCK_NAME = "REFRESH_FS_VOLUMES";
    static final String ENTITY_TYPE = "FILE_SYSTEM_VOLUME";
    private static final DocRef EVENT_DOCREF = new DocRef(ENTITY_TYPE, null, null);

    private static final Map<String, FsVolumeSelector> volumeSelectorMap;

    private static final FsVolumeSelector DEFAULT_VOLUME_SELECTOR;

    static {
        volumeSelectorMap = new HashMap<>();
        registerVolumeSelector(new MostFreePercentVolumeSelector());
        registerVolumeSelector(new MostFreeVolumeSelector());
        registerVolumeSelector(new RandomVolumeSelector());
        registerVolumeSelector(new RoundRobinIgnoreLeastFreePercentVolumeSelector());
        registerVolumeSelector(new RoundRobinIgnoreLeastFreeVolumeSelector());
        registerVolumeSelector(new RoundRobinVolumeSelector());
        registerVolumeSelector(new WeightedFreePercentRandomVolumeSelector());
        registerVolumeSelector(new WeightedFreeRandomVolumeSelector());
        DEFAULT_VOLUME_SELECTOR = volumeSelectorMap.get(RoundRobinVolumeSelector.NAME);
    }

    private final FsVolumeDao fsVolumeDao;
    private final FsVolumeStateDao fileSystemVolumeStateDao;
    private final SecurityContext securityContext;
    private final FsVolumeConfig volumeConfig;
    private final InternalStatisticsReceiver statisticsReceiver;
    private final ClusterLockService clusterLockService;
    private final Provider<EntityEventBus> entityEventBusProvider;
    private final PathCreator pathCreator;
    private final AtomicReference<VolumeList> currentVolumeList = new AtomicReference<>();

    private volatile boolean createdDefaultVolumes;
    private volatile boolean creatingDefaultVolumes;

    @Inject
    public FsVolumeService(final FsVolumeDao fsVolumeDao,
                           final FsVolumeStateDao fileSystemVolumeStateDao,
                           final SecurityContext securityContext,
                           final FsVolumeConfig volumeConfig,
                           final InternalStatisticsReceiver statisticsReceiver,
                           final ClusterLockService clusterLockService,
                           final Provider<EntityEventBus> entityEventBusProvider,
                           final PathCreator pathCreator) {
        this.fsVolumeDao = fsVolumeDao;
        this.fileSystemVolumeStateDao = fileSystemVolumeStateDao;
        this.securityContext = securityContext;
        this.volumeConfig = volumeConfig;
        this.statisticsReceiver = statisticsReceiver;
        this.clusterLockService = clusterLockService;
        this.entityEventBusProvider = entityEventBusProvider;
        this.pathCreator = pathCreator;
    }

    private static void registerVolumeSelector(final FsVolumeSelector volumeSelector) {
        volumeSelectorMap.put(volumeSelector.getName(), volumeSelector);
    }

    public FsVolume create(final FsVolume fileVolume) {
        return securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION, () -> {
            FsVolume result = null;
            String pathString = fileVolume.getPath();
            try {
                if (pathString != null) {
                    Path path = Paths.get(pathString);
                    if (Files.isDirectory(path)) {
                        final long count = FileUtil.count(path);
                        if (count > 0) {
                            throw new IOException("Attempt to create volume in a directory that is not empty: " + path);
                        }
                    }

                    Files.createDirectories(path);
                    LOGGER.info(() -> LogUtil.message("Creating volume in {}", pathString));

                    if (fileVolume.getByteLimit() == null) {
                        //set an arbitrary default limit size of 250MB on each volume to prevent the
                        //filesystem from running out of space, assuming they have 500MB free of course.
                        getDefaultVolumeLimit(path).ifPresent(fileVolume::setByteLimit);
                    }
                }
                fileVolume.setStatus(FsVolume.VolumeUseStatus.ACTIVE);

                final FsVolumeState fileVolumeState = fileSystemVolumeStateDao.create(new FsVolumeState());
                fileVolume.setVolumeState(fileVolumeState);

                AuditUtil.stamp(securityContext.getUserId(), fileVolume);
                result = fsVolumeDao.create(fileVolume);
                result.setVolumeState(fileVolume.getVolumeState());
            } catch (IOException e) {
                LOGGER.error("Unable to create volume due to an error creating directory {}", pathString, e);
            }

            fireChange(EntityAction.CREATE);

            return result;
        });
    }

    public FsVolume update(final FsVolume fileVolume) {
        return securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION, () -> {
            AuditUtil.stamp(securityContext.getUserId(), fileVolume);
            final FsVolume result = fsVolumeDao.update(fileVolume);

            fireChange(EntityAction.UPDATE);

            return result;
        });
    }

    public int delete(final int id) {
        return securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION, () -> {
            final int result = fsVolumeDao.delete(id);

            fireChange(EntityAction.DELETE);

            return result;
        });
    }

    public FsVolume fetch(final int id) {
        return securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION, () -> fsVolumeDao.fetch(id));
    }

    public ResultPage<FsVolume> find(final FindFsVolumeCriteria criteria) {
        return securityContext.insecureResult(() -> {
            ensureDefaultVolumes();
            return doFind(criteria);
        });
    }

    private ResultPage<FsVolume> doFind(final FindFsVolumeCriteria criteria) {
        return fsVolumeDao.find(criteria);
    }

    FsVolume getVolume() {
        return securityContext.insecureResult(() -> {
            final Set<FsVolume> set = getVolumeSet(VolumeUseStatus.ACTIVE);
            if (set.size() > 0) {
                return set.iterator().next();
            }
            return null;
        });
    }

    private Set<FsVolume> getVolumeSet(final VolumeUseStatus streamStatus) {
        final FsVolumeSelector volumeSelector = getVolumeSelector();
        final List<FsVolume> allVolumeList = getCurrentVolumeList().list;
        final List<FsVolume> freeVolumes = FsVolumeListUtil.removeFullVolumes(allVolumeList);
        Set<FsVolume> set = Collections.emptySet();

        final List<FsVolume> filteredVolumeList = getFilteredVolumeList(freeVolumes, streamStatus);
        if (filteredVolumeList.size() > 0) {
            set = Collections.singleton(volumeSelector.select(filteredVolumeList));
        }

        if (set.isEmpty()) {
            LOGGER.warn("No {} volume found, all vols: {}, non-full vols: {}, non-full {} vols: {}",
                    streamStatus,
                    allVolumeList.size(),
                    freeVolumes.size(),
                    streamStatus,
                    filteredVolumeList.size());
        }

        return set;
    }

    private List<FsVolume> getFilteredVolumeList(final List<FsVolume> allVolumes, final VolumeUseStatus streamStatus) {
        final List<FsVolume> list = new ArrayList<>();
        for (final FsVolume volume : allVolumes) {
            // Check the volume type matches.
            boolean ok = true;

            // Check the stream volume use status matches.
            if (streamStatus != null) {
                ok = streamStatus.equals(volume.getStatus());
            }

            if (ok) {
                list.add(volume);
            }
        }
        return list;
    }

    private FsVolumeSelector getVolumeSelector() {
        FsVolumeSelector volumeSelector = null;

        try {
            final String value = volumeConfig.getVolumeSelector();
            if (value != null) {
                volumeSelector = volumeSelectorMap.get(value);
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage);
        }

        if (volumeSelector == null) {
            volumeSelector = DEFAULT_VOLUME_SELECTOR;
        }

        return volumeSelector;
    }


    @Override
    public void onChange(final EntityEvent event) {
        currentVolumeList.set(null);
    }

    private void fireChange(final EntityAction action) {
        currentVolumeList.set(null);
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
        final List<FsVolume> volumeList = doFind(FindFsVolumeCriteria.matchAll()).getValues();
        for (final FsVolume volume : volumeList) {
            final String path = volume.getPath();
            FileUtil.deleteDir(Paths.get(path));
        }

        // Delete default volumes.
        LOGGER.info(() -> "Deleting default volumes");
        if (volumeConfig.getDefaultStreamVolumePaths() != null) {
            final List<String> paths = volumeConfig.getDefaultStreamVolumePaths();
            for (String path : paths) {
                final Path resolvedPath = Paths.get(pathCreator.replaceSystemProperties(path));
                LOGGER.info("Deleting directory {}", resolvedPath.toAbsolutePath().normalize().toString());
                FileUtil.deleteDir(resolvedPath);
            }
        }

        // Clear state between tests.
        currentVolumeList.set(null);
        createdDefaultVolumes = false;
    }

    private VolumeList getCurrentVolumeList() {
        VolumeList volumeList = currentVolumeList.get();
        if (volumeList == null) {
            synchronized (this) {
                volumeList = currentVolumeList.get();
                if (volumeList == null) {
                    volumeList = refresh();
                }
            }
        }
        return volumeList;
    }

    @Override
    public void flush() {
        refresh();
    }

    void updateStatus() {
        clusterLockService.tryLock(LOCK_NAME, this::refresh);
    }

    private VolumeList refresh() {
        final long now = System.currentTimeMillis();
        final List<FsVolume> volumes = new ArrayList<>();

        final FindFsVolumeCriteria findVolumeCriteria = FindFsVolumeCriteria.matchAll();
        findVolumeCriteria.addSort(FindFsVolumeCriteria.FIELD_ID, false, false);
        final List<FsVolume> volumeList = find(findVolumeCriteria).getValues();
        for (final FsVolume volume : volumeList) {
            FsVolumeState volumeState = updateVolumeState(volume);
            volumeState = saveVolumeState(volumeState);
            volume.setVolumeState(volumeState);

            // Record some statistics for the use of this volume.
            recordStats(volume);
            volumes.add(volume);
        }

        final VolumeList newList = new VolumeList(now, volumes);
        synchronized (this) {
            final VolumeList currentList = currentVolumeList.get();
            if (currentList == null || currentList.createTime < newList.createTime) {
                currentVolumeList.set(newList);
            }
        }

        return newList;
    }

    private void recordStats(final FsVolume volume) {
        if (statisticsReceiver != null) {
            try {
                final FsVolumeState volumeState = volume.getVolumeState();

                final long now = System.currentTimeMillis();
                final List<InternalStatisticEvent> events = new ArrayList<>();
                addStatisticEvent(events, now, volume, "Limit", volume.getByteLimit());
                addStatisticEvent(events, now, volume, "Used", volumeState.getBytesUsed());
                addStatisticEvent(events, now, volume, "Free", volumeState.getBytesFree());
                addStatisticEvent(events, now, volume, "Total", volumeState.getBytesTotal());
                statisticsReceiver.putEvents(events);
            } catch (final RuntimeException e) {
                LOGGER.warn(e::getMessage);
                LOGGER.debug(e::getMessage, e);
            }
        }
    }

    private void addStatisticEvent(final List<InternalStatisticEvent> events,
                                   final long timeMs,
                                   final FsVolume volume,
                                   final String type,
                                   final Long bytes) {
        if (bytes != null) {
            Map<String, String> tags = ImmutableMap.<String, String>builder()
                    .put("Id", String.valueOf(volume.getId()))
                    .put("Path", volume.getPath())
                    .put("Type", type)
                    .build();

            InternalStatisticEvent event = InternalStatisticEvent.createValueStat(
                    InternalStatisticKey.VOLUMES, timeMs, tags, bytes.doubleValue());
            events.add(event);
        }
    }

    private FsVolumeState updateVolumeState(final FsVolume volume) {
        final FsVolumeState volumeState = volume.getVolumeState();
        volumeState.setUpdateTimeMs(System.currentTimeMillis());
        final Path path = Paths.get(volume.getPath());

        // Ensure the path exists
        if (Files.isDirectory(path)) {
            LOGGER.debug(() -> LogUtil.message("updateVolumeState() path exists: {}", path));
            setSizes(path, volumeState);
        } else {
            try {
                Files.createDirectories(path);
                LOGGER.debug(() -> LogUtil.message("updateVolumeState() path created: {}", path));
                setSizes(path, volumeState);
            } catch (final IOException e) {
                LOGGER.error(() -> LogUtil.message("updateVolumeState() path not created: {}", path));
            }
        }

        LOGGER.debug(() -> LogUtil.message("updateVolumeState() exit {}", volume));
        return volumeState;
    }

    private void setSizes(final Path path, final FsVolumeState volumeState) {
        try {
            final FileStore fileStore = Files.getFileStore(path);
            final long usableSpace = fileStore.getUsableSpace();
            final long freeSpace = fileStore.getUnallocatedSpace();
            final long totalSpace = fileStore.getTotalSpace();

            volumeState.setBytesTotal(totalSpace);
            volumeState.setBytesFree(usableSpace);
            volumeState.setBytesUsed(totalSpace - freeSpace);
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private FsVolumeState saveVolumeState(final FsVolumeState volumeState) {
        return fileSystemVolumeStateDao.update(volumeState);
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
                    final boolean isEnabled = volumeConfig.isCreateDefaultStreamVolumesOnStart();
                    if (isEnabled) {
                        final FindFsVolumeCriteria findVolumeCriteria = FindFsVolumeCriteria.matchAll();
                        findVolumeCriteria.addSort(FindFsVolumeCriteria.FIELD_ID, false, false);
                        final List<FsVolume> existingVolumes = doFind(findVolumeCriteria).getValues();
                        if (existingVolumes.size() == 0) {
                            if (volumeConfig.getDefaultStreamVolumePaths() != null) {
                                final List<String> paths = volumeConfig.getDefaultStreamVolumePaths();
                                for (String path : paths) {
                                    path = pathCreator.replaceSystemProperties(path);
                                    final Path resolvedPath = Paths.get(path.trim());
                                    LOGGER.info("Creating default data volume with path {}",
                                            resolvedPath.toAbsolutePath().normalize());

                                    createVolume(resolvedPath);
                                }

                            } else {
                                LOGGER.warn(() -> "No suitable directory to create default volumes in");
                            }
                        } else {
                            LOGGER.info(() -> "Existing volumes exist, won't create default volumes");
                        }
                    } else {
                        LOGGER.info(() -> "Creation of default volumes is currently disabled");
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

    private void createVolume(final Path path) {
        final FsVolume fileVolume = new FsVolume();
        fileVolume.setPath(FileUtil.getCanonicalPath(path));
        create(fileVolume);

//        String pathStr = FileUtil.getCanonicalPath(path);
//        try {
//            Files.createDirectories(path);
//            LOGGER.info("Creating volume in {}",
//                    pathStr);
//            final FileVolumeState fileVolumeState = fileSystemVolumeStateDao.create();
//            final FileVolume fileVolume = new FileVolume();
//            fileVolume.setStatus(FileVolume.VolumeUseStatus.ACTIVE);
//            fileVolume.setPath(pathStr);
//            fileVolume.setVolumeState(fileVolumeState);
//            set an arbitrary default limit size of 250MB on each volume to prevent the
//            filesystem from running out of space, assuming they have 500MB free of course.
//            getDefaultVolumeLimit(path).ifPresent(fileVolume::setByteLimit);
//
//            create(fileVolume);
//
//
//            final FileVolume result = contextResultWithOptimisticLocking(connectionProvider, (context) -> {
//                AuditUtil.stamp(securityContext.getUserId(), fileVolume);
//                FsVolumeRecord record = context.newRecord(FS_VOLUME, fileVolume);
//                record.set(FS_VOLUME.STATUS, fileVolume.getStatus().getPrimitiveValue());
//                record.set(FS_VOLUME.FK_FS_VOLUME_STATE_ID, fileVolumeState.getId());
//                record.store();
//                return record.into(FileVolume.class);
//            });
//            result.setVolumeState(fileVolume.getVolumeState());
//        } catch (IOException e) {
//            LOGGER.error("Unable to create volume due to an error creating directory {}", pathStr, e);
//        }
    }

    private OptionalLong getDefaultVolumeLimit(final Path path) {
        try {
            long totalBytes = Files.getFileStore(path).getTotalSpace();
            // set an arbitrary limit of 90% of the filesystem total size to ensure we don't fill up the
            // filesystem.  Limit can be configured from within stroom.
            // Should be noted that although if you have multiple volumes on a filesystem the limit will apply
            // to all volumes and any other data on the filesystem. I.e. once the amount of the filesystem in use
            // is greater than the limit writes to those volumes will be prevented. See Volume.isFull() and
            // this.updateVolumeState()
            return OptionalLong.of((long) (totalBytes * volumeConfig.getDefaultStreamVolumeFilesystemUtilisation()));
        } catch (IOException e) {
            LOGGER.warn(() -> LogUtil.message("Unable to determine the total space on the filesystem for path: {}",
                    FileUtil.getCanonicalPath(path)));
            return OptionalLong.empty();
        }
    }

    private static class VolumeList {
        private final long createTime;
        private final List<FsVolume> list;

        VolumeList(final long createTime, final List<FsVolume> list) {
            this.createTime = createTime;
            this.list = list;
        }
    }
}
