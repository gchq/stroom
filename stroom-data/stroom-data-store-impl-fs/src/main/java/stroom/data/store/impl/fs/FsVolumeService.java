package stroom.data.store.impl.fs;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolume.VolumeUseStatus;
import stroom.data.store.impl.fs.shared.FsVolumeState;
import stroom.docref.DocRef;
import stroom.index.shared.ValidationResult;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.statistics.api.InternalStatisticEvent;
import stroom.statistics.api.InternalStatisticKey;
import stroom.statistics.api.InternalStatisticsReceiver;
import stroom.task.api.TaskContext;
import stroom.util.AuditUtil;
import stroom.util.NullSafe;
import stroom.util.date.DateUtil;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.io.capacity.HasCapacitySelector;
import stroom.util.io.capacity.HasCapacitySelectorFactory;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.Flushable;
import stroom.util.shared.ResultPage;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;
import stroom.util.time.StroomDuration;

import com.google.common.collect.ImmutableSortedMap;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
@EntityEventHandler(type = FsVolumeService.ENTITY_TYPE, action = {
        EntityAction.CREATE,
        EntityAction.DELETE})
public class FsVolumeService implements EntityEvent.Handler, Clearable, Flushable, HasSystemInfo {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsVolumeService.class);

    private static final String LOCK_NAME = "REFRESH_FS_VOLUMES";
    static final String ENTITY_TYPE = "FILE_SYSTEM_VOLUME";
    private static final DocRef EVENT_DOCREF = new DocRef(ENTITY_TYPE, null, null);
    protected static final String TEMP_FILE_PREFIX = "stroomFsVolVal";

    private final FsVolumeDao fsVolumeDao;
    private final FsVolumeStateDao fileSystemVolumeStateDao;
    private final SecurityContext securityContext;
    private final Provider<FsVolumeConfig> volumeConfigProvider;
    private final InternalStatisticsReceiver statisticsReceiver;
    private final ClusterLockService clusterLockService;
    private final Provider<EntityEventBus> entityEventBusProvider;
    private final PathCreator pathCreator;
    // Hold a cache of the current picture of available volumes, with their used/free/total/etc. stats.
    // Allows for fast volume selection without having to hit the db each time.
    private final AtomicReference<VolumeList> currentVolumeList = new AtomicReference<>();
    private final AtomicReference<HasCapacitySelector> volumeSelector = new AtomicReference<>();
    private final NodeInfo nodeInfo;
    private final TaskContext taskContext;
    private final HasCapacitySelectorFactory hasCapacitySelectorFactory;

    private volatile boolean createdDefaultVolumes = false;

    @Inject
    public FsVolumeService(final FsVolumeDao fsVolumeDao,
                           final FsVolumeStateDao fileSystemVolumeStateDao,
                           final SecurityContext securityContext,
                           final Provider<FsVolumeConfig> volumeConfigProvider,
                           final InternalStatisticsReceiver statisticsReceiver,
                           final ClusterLockService clusterLockService,
                           final Provider<EntityEventBus> entityEventBusProvider,
                           final PathCreator pathCreator,
                           final NodeInfo nodeInfo,
                           final TaskContext taskContext,
                           final HasCapacitySelectorFactory hasCapacitySelectorFactory) {
        this.fsVolumeDao = fsVolumeDao;
        this.fileSystemVolumeStateDao = fileSystemVolumeStateDao;
        this.securityContext = securityContext;
        this.volumeConfigProvider = volumeConfigProvider;
        this.statisticsReceiver = statisticsReceiver;
        this.clusterLockService = clusterLockService;
        this.entityEventBusProvider = entityEventBusProvider;
        this.pathCreator = pathCreator;
        this.nodeInfo = nodeInfo;
        this.taskContext = taskContext;
        this.hasCapacitySelectorFactory = hasCapacitySelectorFactory;

        // Can't call this in the ctor as it causes a circular dep problem with EntityEventBus
    }

    public FsVolume create(final FsVolume fileVolume) {
        return securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION, () -> {
            FsVolume result = null;
            String pathString = getAbsVolumePath(fileVolume);
            try {
                if (pathString != null) {
                    Path volPath = Paths.get(pathString);
                    if (Files.exists(volPath) && !Files.isDirectory(volPath)) {
                        throw new RuntimeException(LogUtil.message(
                                "Unable to create volume as path '{}' exists but is not a directory.", volPath));
                    } else if (Files.isDirectory(volPath)) {
                        // The validation step creates a temp file to test access, so need to ignore that
                        final long count = FileUtil.count(volPath, filePath ->
                                filePath.getFileName().toString().startsWith(TEMP_FILE_PREFIX));
                        if (count > 0) {
                            throw new RuntimeException(
                                    "Attempt to create volume in a directory that is not empty: " + volPath);
                        }
                    }

                    Files.createDirectories(volPath);
                    LOGGER.info(() -> LogUtil.message("Creating volume in {}", pathString));

                    if (fileVolume.getByteLimit() == null) {
                        //set an arbitrary default limit size of 250MB on each volume to prevent the
                        //filesystem from running out of space, assuming they have 500MB free of course.
                        getDefaultVolumeLimit(volPath).ifPresent(fileVolume::setByteLimit);
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
                final String msg;
                if (pathString.equals(e.getMessage())) {
                    // Some java IO exceptions just have the path as the message, helpful.
                    msg = e.getClass().getSimpleName();
                } else {
                    msg = e.getMessage();
                }

                throw new RuntimeException(LogUtil.message(
                        "Unable to create volume due to an error creating directory {}: {}",
                        pathString, msg), e);
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
        return securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION, () ->
                fsVolumeDao.fetch(id));
    }

    public ResultPage<FsVolume> find(final FindFsVolumeCriteria criteria) {
        // Can't call this in the ctor as it causes a circular dep problem with EntityEventBus
        ensureDefaultVolumes();
        return doFind(criteria);
    }

    private ResultPage<FsVolume> doFind(final FindFsVolumeCriteria criteria) {
        return fsVolumeDao.find(criteria);
    }

    /**
     * @return An active and non-full volume selected by the configured volume selector
     */
    public FsVolume getVolume() {
        return securityContext.insecureResult(() -> {
            // Can't call this in the ctor as it causes a circular dep problem with EntityEventBus
            ensureDefaultVolumes();

            final Set<FsVolume> set = getVolumeSet(VolumeUseStatus.ACTIVE);
            if (set.size() > 0) {
                final FsVolume volume = set.iterator().next();
                LOGGER.trace("Using volume {}", volume);
                return volume;
            }
            return null;
        });
    }

    private Set<FsVolume> getVolumeSet(final VolumeUseStatus streamStatus) {
        final HasCapacitySelector volumeSelector = getVolumeSelector();
        final List<FsVolume> allVolumeList = getCurrentVolumeList().list;
        LOGGER.trace("allVolumeList {}", allVolumeList);
        final List<FsVolume> freeVolumes = FsVolumeListUtil.removeFullVolumes(allVolumeList);
        LOGGER.trace("freeVolumes {}", freeVolumes);
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

            LOGGER.debug(() -> LogUtil.message("All FS Volumes:\n{}",
                    generateAllVolumesAsciiTable(allVolumeList)));
        }

        return set;
    }

    private String generateAllVolumesAsciiTable(final List<FsVolume> allVolumes) {
        return AsciiTable.builder(allVolumes)
                .withColumn(Column.of("Path", FsVolume::getPath))
                .withColumn(Column.of("Status", FsVolume::getStatus))
                .withColumn(Column.integer(
                        "Total",
                        vol -> vol.getTotalCapacityBytes().orElse(-1)))
                .withColumn(Column.decimal(
                        "Used %",
                        vol -> vol.getUsedCapacityPercent().orElse(-1),
                        2))
                .withColumn(Column.decimal(
                        "Free %",
                        vol -> vol.getFreeCapacityPercent().orElse(-1),
                        2))
                .withColumn(Column.of("Is Full", FsVolume::isFull))
                .build();
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

    private HasCapacitySelector getVolumeSelector() {
        String requiredSelectorName = null;

        try {
            requiredSelectorName = volumeConfigProvider.get().getVolumeSelector();
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage);
        }

        final HasCapacitySelector currentSelector = volumeSelector.get();
        if (currentSelector != null
                && requiredSelectorName != null
                && currentSelector.getName().equals(requiredSelectorName)) {
            return currentSelector;
        } else {
            final String requiredSelectorNameCopy = requiredSelectorName;
            // Atomically update the selector reference to the new one
            return volumeSelector.accumulateAndGet(
                    null,
                    (curr, next) -> {
                        if (curr == null
                                || requiredSelectorNameCopy == null
                                || !curr.getName().equals(requiredSelectorNameCopy)) {
                            return hasCapacitySelectorFactory.createSelectorOrDefault(
                                    requiredSelectorNameCopy);
                        } else {
                            // Existing one is ok, maybe another thread did it
                            return curr;
                        }
                    });
        }
    }

    @Override
    public void onChange(final EntityEvent event) {
        LOGGER.debug("Clearing currentVolumeList");

        clearCurrentVolumeList();
    }

    private synchronized void clearCurrentVolumeList() {
        currentVolumeList.set(null);
    }

    private void fireChange(final EntityAction action) {
        clearCurrentVolumeList();
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

    /**
     * For use in testing
     */
    @Override
    public synchronized void clear() {
        final List<FsVolume> volumeList = doFind(FindFsVolumeCriteria.matchAll()).getValues();
        for (final FsVolume volume : volumeList) {
            final String path = getAbsVolumePath(volume);
            FileUtil.deleteDir(Paths.get(path));
            // Delete the db record
            fsVolumeDao.delete(volume.getId());
        }

        // Delete default volumes.
        LOGGER.info(() -> "Deleting default volumes");
        if (volumeConfigProvider.get().getDefaultStreamVolumePaths() != null) {
            final List<String> paths = volumeConfigProvider.get().getDefaultStreamVolumePaths();
            for (String path : paths) {
                final Path resolvedPath = Paths.get(
                        pathCreator.makeAbsolute(
                                pathCreator.replaceSystemProperties(path)));
                LOGGER.info("Deleting directory {}", resolvedPath.toAbsolutePath().normalize().toString());
                FileUtil.deleteDir(resolvedPath);
            }
        }

        // Clear state between tests.
        clearCurrentVolumeList();
        // Now recreate the default vols
        createdDefaultVolumes = false;
        LOGGER.debug("createdDefaultVolumes set to false");
    }

    private VolumeList getCurrentVolumeList() {
        VolumeList volumeList = currentVolumeList.get();
        if (volumeList == null) {
            synchronized (this) {
                volumeList = currentVolumeList.get();
                if (volumeList == null) {
                    volumeList = refresh(true);
                    currentVolumeList.set(volumeList);
                }
            }
        }
        return volumeList;
    }

    @Override
    public void flush() {
        // Called from UI so make sure it is up to date
        refresh(true);
    }

    void updateStatus() {
        // Each node needs to get a lock so that the first one in can update the state and then
        // every other node can then just read the state written by the first node.
        clusterLockService.lock(LOCK_NAME, () ->
                refresh(false));
    }

    private synchronized VolumeList refresh(final boolean isForcedRefresh) {
        taskContext.info(() -> "Refreshing volumes");

        // Can't call this in the ctor as it causes a circular dep problem with EntityEventBus
        ensureDefaultVolumes();

        final Instant now = Instant.now();
        final List<FsVolume> volumes = new ArrayList<>();

        final FindFsVolumeCriteria findVolumeCriteria = FindFsVolumeCriteria.matchAll();
        findVolumeCriteria.addSort(FindFsVolumeCriteria.FIELD_ID, false, false);
        final List<FsVolume> dbVolumes = find(findVolumeCriteria).getValues();

        final StroomDuration volumeStateUpdateThreshold = volumeConfigProvider.get().getMaxVolumeStateAge();
        final long updateTimeCutOffEpochMs = now.minus(volumeStateUpdateThreshold.getDuration()).toEpochMilli();

        // Get the oldest update time from the list then use that to see if we need to update all
        final Optional<Long> optMinUpdateTimeEpochMs = dbVolumes.stream()
                .map(vol -> NullSafe.get(vol, FsVolume::getVolumeState, FsVolumeState::getUpdateTimeMs))
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder());

        if (optMinUpdateTimeEpochMs.isEmpty()
                || isForcedRefresh
                || optMinUpdateTimeEpochMs.get() < updateTimeCutOffEpochMs) {
            for (final FsVolume volume : dbVolumes) {
                taskContext.info(() -> "Refreshing volume '" + getAbsVolumePath(volume) + "'");
                // Update the volume state and save in the DB.
                updateVolumeState(volume);

                // Record some statistics for the use of this volume.
                recordStats(volume);
                volumes.add(volume);
            }
        } else {
            LOGGER.debug(() -> LogUtil.message("Not updating state for vols {}, with min update time {}",
                    optMinUpdateTimeEpochMs.map(DateUtil::createNormalDateTimeString)));
            volumes.addAll(dbVolumes);
        }

        final VolumeList newList = new VolumeList(now.toEpochMilli(), volumes);
        final VolumeList currentList = currentVolumeList.get();
        if (currentList == null || currentList.createTime < newList.createTime) {
            currentVolumeList.set(newList);
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
            SortedMap<String, String> tags = ImmutableSortedMap.<String, String>naturalOrder()
                    .put("Id", String.valueOf(volume.getId()))
                    .put("Path", getAbsVolumePath(volume))
                    .put("Type", type)
                    .put("Node", nodeInfo.getThisNodeName())
                    .build();

            InternalStatisticEvent event = InternalStatisticEvent.createValueStat(
                    InternalStatisticKey.VOLUMES, timeMs, tags, bytes.doubleValue());
            events.add(event);
        }
    }

    private void updateVolumeState(final FsVolume volume) {
        final Path absPath = Paths.get(getAbsVolumePath(volume));

        try {
            FsVolumeState volumeState = volume.getVolumeState();
            volumeState.setUpdateTimeMs(System.currentTimeMillis());

            // Ensure the path exists
            if (Files.isDirectory(absPath)) {
                LOGGER.debug(() -> LogUtil.message("updateVolumeState() path exists: {}", absPath));
                setSizes(absPath, volume, volumeState);
            } else {
                Files.createDirectories(absPath);
                LOGGER.debug(() -> LogUtil.message("updateVolumeState() path created: {}", absPath));
                setSizes(absPath, volume, volumeState);
            }

            volumeState = saveVolumeState(volumeState);
            volume.setVolumeState(volumeState);

            LOGGER.debug(() -> LogUtil.message("updateVolumeState() exit {}", volume));

        } catch (final IOException | RuntimeException e) {
            LOGGER.error(() -> LogUtil.message("updateVolumeState() path not created: {}", absPath));
        }
    }

    private void setSizes(final Path path,
                          final FsVolume fsVolume,
                          final FsVolumeState volumeState) throws IOException {
        final FileStore fileStore = Files.getFileStore(path);
        final long osUsableSpace = fileStore.getUsableSpace();
        final long osFreeSpace = fileStore.getUnallocatedSpace();
        final long totalSpace = fileStore.getTotalSpace();
        final long usedSpace = totalSpace - osFreeSpace;
        // Calc free space based on the limit if one is set
        final long freeSpace = fsVolume.getCapacityLimitBytes()
                .stream()
                .map(limit -> Math.max(limit - usedSpace, 0))
                .findAny()
                .orElse(osUsableSpace);

        volumeState.setBytesTotal(totalSpace);
        volumeState.setBytesFree(freeSpace);
        volumeState.setBytesUsed(usedSpace);
    }

    private FsVolumeState saveVolumeState(final FsVolumeState volumeState) {
        // If another node updates the state at the same time it doesn't matter
        // as they will be updating to the same value. This saves having to
        // get a cluster lock
        return fileSystemVolumeStateDao.updateWithoutOptimisticLocking(volumeState);
    }

    /**
     * Public for use in tests that tear down volumes
     */
    public void ensureDefaultVolumes() {
        LOGGER.debug("ensureDefaultVolumes called, createdDefaultVolumes: {}", createdDefaultVolumes);
        if (!createdDefaultVolumes) {
            // will check createdDefaultVolumes again under sync inside createDefaultVolumes()
            securityContext.asProcessingUser(this::createDefaultVolumes);
        }
    }

    private synchronized void createDefaultVolumes() {
        LOGGER.debug("createDefaultVolumes called, createDefaultVolumes: {}", createdDefaultVolumes);

        // (re-)check state now we hold synch lock
        if (!createdDefaultVolumes) {
            try {
                securityContext.insecure(() -> {
                    final FsVolumeConfig volumeConfig = volumeConfigProvider.get();
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
                                    path = pathCreator.makeAbsolute(path);
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
                LOGGER.debug("createdDefaultVolumes set to true");
            }
        } else {
            LOGGER.debug("Volumes already created");
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
            return OptionalLong.of((long) (totalBytes * volumeConfigProvider.get()
                    .getDefaultStreamVolumeFilesystemUtilisation()));
        } catch (IOException e) {
            LOGGER.warn(() -> LogUtil.message("Unable to determine the total space on the filesystem for path: {}",
                    FileUtil.getCanonicalPath(path)));
            return OptionalLong.empty();
        }
    }

    @Override
    public SystemInfoResult getSystemInfo() {

        final VolumeList volumeList = getCurrentVolumeList();

        // Need to wrap with optional as Map.ofEntries does not support null values.
        final var volInfoList = volumeList.getList()
                .stream()
                .map(vol -> Map.ofEntries(
                        new SimpleEntry<>("path", Optional.ofNullable(getAbsVolumePath(vol))),
                        new SimpleEntry<>("limit", Optional.ofNullable(vol.getByteLimit())),
                        new SimpleEntry<>("state", Optional.ofNullable(vol.getStatus())),
                        new SimpleEntry<>("free", Optional.ofNullable(NullSafe.get(
                                vol.getVolumeState(),
                                FsVolumeState::getBytesFree))),
                        new SimpleEntry<>("total", Optional.ofNullable(NullSafe.get(
                                vol.getVolumeState(),
                                FsVolumeState::getBytesTotal))),
                        new SimpleEntry<>("used", Optional.ofNullable(NullSafe.get(
                                vol.getVolumeState(),
                                FsVolumeState::getBytesUsed))),
                        new SimpleEntry<>("dbStateUpdateTime", Optional.ofNullable(NullSafe.get(
                                vol.getVolumeState(),
                                FsVolumeState::getUpdateTimeMs,
                                DateUtil::createNormalDateTimeString)))))
                .collect(Collectors.toList());

        return SystemInfoResult.builder(this)
                .addDetail("volumeSelector", volumeConfigProvider.get().getVolumeSelector())
                .addDetail("volumeListCreateTime", DateUtil.createNormalDateTimeString(volumeList.getCreateTime()))
                .addDetail("volumeList", volInfoList)
                .build();
    }

    public ValidationResult validate(final FsVolume volume) {
        ValidationResult validationResult = ValidationResult.ok();

        if (NullSafe.isBlankString(volume, FsVolume::getPath)) {
            validationResult = ValidationResult.error("You must select a node for the volume.");
        }

        // Don't need to make absolute here as comparing like with like
        final FsVolume existingVol = volume.getId() != null
                ? securityContext.secureResult(() -> fsVolumeDao.fetch(volume.getId()))
                : null;
        final boolean hasPathChanged = !Objects.equals(
                NullSafe.get(existingVol, FsVolume::getPath),
                volume.getPath());

        if (hasPathChanged) {
            if (validationResult.isOk()) {
                validationResult = validateForDupPath(volume);
            }
            if (validationResult.isOk()) {
                validationResult = validateVolumePath(volume);
            }
        }
        return validationResult;
    }

    private ValidationResult validateForDupPath(final FsVolume volume) {
        final String absPath = getAbsVolumePath(volume);
        final List<FsVolume> volumes = fsVolumeDao.getAll();
        // We need to get all, so we can make all the db one's absolute in the same was as our one
        final boolean foundDup = volumes.stream()
                .anyMatch(dbVol ->
                        !Objects.equals(dbVol.getId(), volume.getId())
                                && Objects.equals(getAbsVolumePath(dbVol), absPath));
        if (foundDup) {
            return ValidationResult.error(LogUtil.message(
                    "Another volume already exists with path '{}'", absPath));
        } else {
            return ValidationResult.ok();
        }
    }

    private ValidationResult validateVolumePath(final FsVolume volume) {
        final Path absPath = Paths.get(getAbsVolumePath(volume));
        LOGGER.debug("path: {}", absPath);

        if (!Files.exists(absPath)) {
            try {
                Files.createDirectories(absPath);
            } catch (IOException e) {
                final String msg;
                if (absPath.toString().equals(e.getMessage())) {
                    // Some java IO exceptions just have the path as the message, helpful.
                    msg = e.getClass().getSimpleName();
                } else {
                    msg = e.getMessage();
                }
                return ValidationResult.error(LogUtil.message(
                        "Error creating index volume path '{}': {}",
                        absPath,
                        msg));
            }
        } else if (!Files.isDirectory(absPath)) {
            return ValidationResult.error(LogUtil.message(
                    "Error creating index volume path '{}': The path exists but is not a directory.",
                    absPath));
        }

        // Can't seem to find a good way of checking if we have write perms on the dir so create a file
        // then delete it, after a small delay
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile(absPath, TEMP_FILE_PREFIX, null);


        } catch (IOException e) {
            return ValidationResult.error(LogUtil.message(
                    "Error creating test file in directory {}. " +
                            "Does Stroom have the right permissions on this directory? " +
                            "Error message: {} {}",
                    absPath,
                    e.getClass().getSimpleName(),
                    e.getMessage()));
        } finally {
            // Wait a few secs before we delete the file in case some file systems prevent deletion
            // immediately after creation
            if (tempFile != null) {
                final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
                final Path finalTempFile = tempFile;
                executorService.schedule(() -> {
                    LOGGER.debug("About to delete file {}", finalTempFile);
                    try {
                        Files.deleteIfExists(finalTempFile);
                    } catch (IOException e) {
                        LOGGER.error("Unable to delete temporary file {}", finalTempFile, e);
                    }
                }, 5, TimeUnit.SECONDS);
            }
        }

        return ValidationResult.ok();
    }

    private String getAbsVolumePath(final FsVolume volume) {
        return pathCreator.makeAbsolute(
                pathCreator.replaceSystemProperties(volume.getPath()));
    }

    private static class VolumeList {

        private final long createTime;
        private final List<FsVolume> list;

        VolumeList(final long createTime, final List<FsVolume> list) {
            this.createTime = createTime;
            this.list = list;
        }

        public List<FsVolume> getList() {
            return list;
        }

        public long getCreateTime() {
            return createTime;
        }
    }

}
