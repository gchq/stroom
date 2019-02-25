package stroom.data.store.impl.fs;

import com.google.common.collect.ImmutableMap;
import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.TableField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.lock.api.ClusterLockService;
import stroom.data.store.impl.fs.db.jooq.tables.records.FileVolumeRecord;
import stroom.data.store.impl.fs.shared.FSVolume;
import stroom.data.store.impl.fs.shared.FSVolume.VolumeUseStatus;
import stroom.data.store.impl.fs.shared.FSVolumeState;
import stroom.data.store.impl.fs.shared.FindFSVolumeCriteria;
import stroom.db.util.AuditUtil;
import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.entity.shared.EntityAction;
import stroom.entity.shared.EntityEvent;
import stroom.entity.shared.EntityEventBus;
import stroom.entity.shared.EntityEventHandler;
import stroom.security.Security;
import stroom.security.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.statistics.api.InternalStatisticEvent;
import stroom.statistics.api.InternalStatisticKey;
import stroom.statistics.api.InternalStatisticsReceiver;
import stroom.util.io.FileUtil;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.CriteriaSet;
import stroom.util.shared.Sort.Direction;

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
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static stroom.data.store.impl.fs.db.jooq.tables.FileVolume.FILE_VOLUME;
import static stroom.data.store.impl.fs.db.jooq.tables.FileVolumeState.FILE_VOLUME_STATE;
import static stroom.db.util.JooqUtil.contextWithOptimisticLocking;

@Singleton
@EntityEventHandler(type = FileSystemVolumeServiceImpl.ENTITY_TYPE, action = {EntityAction.CREATE, EntityAction.DELETE})
public class FileSystemVolumeServiceImpl implements FileSystemVolumeService, EntityEvent.Handler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataVolumeServiceImpl.class);

    private static final String LOCK_NAME = "REFRESH_FILE_VOLUMES";
    static final String ENTITY_TYPE = "FILE_SYSTEM_VOLUME";
    private static final DocRef EVENT_DOCREF = new DocRef(ENTITY_TYPE, null, null);

    static final Path DEFAULT_VOLUMES_SUBDIR = Paths.get("volumes");
    static final Path DEFAULT_STREAM_VOLUME_SUBDIR = Paths.get("defaultStreamVolume");

    private static final Map<String, FileVolumeSelector> volumeSelectorMap;

    private static final FileVolumeSelector DEFAULT_VOLUME_SELECTOR;

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

    private final ConnectionProvider connectionProvider;
    private final Security security;
    private final SecurityContext securityContext;
    private final FileSystemVolumeConfig volumeConfig;
    private final InternalStatisticsReceiver statisticsReceiver;
    private final FileSystemVolumeStateDao fileSystemVolumeStateDao;
    private final ClusterLockService clusterLockService;
    private final Provider<EntityEventBus> entityEventBusProvider;

    private final AtomicReference<List<FSVolume>> currentVolumeState = new AtomicReference<>();

    private volatile boolean createdDefaultVolumes;
    private boolean creatingDefaultVolumes;

    @Inject
    FileSystemVolumeServiceImpl(final ConnectionProvider connectionProvider,
                                final Security security,
                                final SecurityContext securityContext,
                                final FileSystemVolumeConfig volumeConfig,
                                final InternalStatisticsReceiver statisticsReceiver,
                                final FileSystemVolumeStateDao fileSystemVolumeStateDao,
                                final ClusterLockService clusterLockService,
                                final Provider<EntityEventBus> entityEventBusProvider) {
        this.connectionProvider = connectionProvider;
        this.security = security;
        this.securityContext = securityContext;
        this.volumeConfig = volumeConfig;
        this.statisticsReceiver = statisticsReceiver;
        this.fileSystemVolumeStateDao = fileSystemVolumeStateDao;
        this.clusterLockService = clusterLockService;
        this.entityEventBusProvider = entityEventBusProvider;
    }

    private static void registerVolumeSelector(final FileVolumeSelector volumeSelector) {
        volumeSelectorMap.put(volumeSelector.getName(), volumeSelector);
    }

    @Override
    public FSVolume create(final FSVolume fileVolume) {
        return security.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION, () -> {
            FSVolume result = null;
            String pathString = fileVolume.getPath();
            try {
                if (pathString != null) {
                    Path path = Paths.get(pathString);
                    Files.createDirectories(path);
                    LOGGER.info("Creating volume in {}", pathString);

                    if (fileVolume.getByteLimit() == null) {
                        //set an arbitrary default limit size of 250MB on each volume to prevent the
                        //filesystem from running out of space, assuming they have 500MB free of course.
                        getDefaultVolumeLimit(path).ifPresent(fileVolume::setByteLimit);
                    }
                }
                fileVolume.setStatus(FSVolume.VolumeUseStatus.ACTIVE);

                final FSVolumeState fileVolumeState = fileSystemVolumeStateDao.create(new FSVolumeState());
                fileVolume.setVolumeState(fileVolumeState);

                result = contextWithOptimisticLocking(connectionProvider, (context) -> {
                    AuditUtil.stamp(securityContext.getUserId(), fileVolume);
                    final FileVolumeRecord record = context.newRecord(FILE_VOLUME, fileVolume);
                    volumeToRecord(fileVolume, record);
                    record.store();
                    return recordToVolume(record, fileVolume.getVolumeState());
                });
                result.setVolumeState(fileVolume.getVolumeState());
            } catch (IOException e) {
                LOGGER.error("Unable to create volume due to an error creating directory {}", pathString, e);
            }

            fireChange(EntityAction.CREATE);

            return result;
        });
    }

    @Override
    public FSVolume update(final FSVolume fileVolume) {
        return security.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION, () -> {
            final FSVolume result = contextWithOptimisticLocking(connectionProvider, (context) -> {
                AuditUtil.stamp(securityContext.getUserId(), fileVolume);
                final FileVolumeRecord record = context.newRecord(FILE_VOLUME, fileVolume);
                volumeToRecord(fileVolume, record);
                // This depends on there being a field named 'id' that is what we expect it to be.
                // I'd rather this was implicit/opinionated than forced into place with an interface.
                LOGGER.debug("Updating a {} with id {}", FILE_VOLUME.getName(), record.getValue("id"));
                record.update();
                return recordToVolume(record, fileVolume.getVolumeState());
            });
            result.setVolumeState(fileVolume.getVolumeState());

            fireChange(EntityAction.UPDATE);

            return result;
        });
    }

    @Override
    public int delete(final int id) {
        return security.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION, () -> JooqUtil.contextResult(connectionProvider, context -> {
            final Optional<Integer> stateIdOptional = context
                    .select(FILE_VOLUME.FK_FILE_VOLUME_STATE_ID)
                    .from(FILE_VOLUME)
                    .where(FILE_VOLUME.ID.eq(id))
                    .fetchOptional(FILE_VOLUME.FK_FILE_VOLUME_STATE_ID);

            final int result = context
                    .deleteFrom(FILE_VOLUME)
                    .where(FILE_VOLUME.ID.eq(id))
                    .execute();

            stateIdOptional.ifPresent(stateId -> context
                    .deleteFrom(FILE_VOLUME_STATE)
                    .where(FILE_VOLUME_STATE.ID.eq(stateId))
                    .execute());

            fireChange(EntityAction.DELETE);

            return result;
        }));
    }

    public FSVolume fetch(final int id) {
        return security.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION, () ->
                JooqUtil.contextResult(connectionProvider, context -> context
                        .select()
                        .from(FILE_VOLUME)
                        .join(FILE_VOLUME_STATE)
                        .on(FILE_VOLUME_STATE.ID.eq(FILE_VOLUME.FK_FILE_VOLUME_STATE_ID))
                        .where(FILE_VOLUME.ID.eq(id))
                        .fetchOptional()
                        .map(this::recordToVolume)
                        .orElse(null)));
    }

    @Override
    public BaseResultList<FSVolume> find(final FindFSVolumeCriteria criteria) {
        return security.insecureResult(() -> {
            ensureDefaultVolumes();

            final Optional<Condition> volumeStatusCondition = volumeStatusCriteriaSetToCondition(FILE_VOLUME.STATUS, criteria.getStatusSet());
            final List<Condition> conditions = new ArrayList<>();
            volumeStatusCondition.ifPresent(conditions::add);

            final List<FSVolume> result = JooqUtil.contextResult(connectionProvider, context ->
                    JooqUtil.applyLimits(
                            context
                                    .select()
                                    .from(FILE_VOLUME)
                                    .join(FILE_VOLUME_STATE)
                                    .on(FILE_VOLUME_STATE.ID.eq(FILE_VOLUME.FK_FILE_VOLUME_STATE_ID))
                                    .where(conditions)
                            , criteria.getPageRequest())
                            .fetch()
                            .map(this::recordToVolume));
            return BaseResultList.createCriterialBasedList(result, criteria);
        });
    }

    private void volumeToRecord(final FSVolume fileVolume, final FileVolumeRecord record) {
        record.set(FILE_VOLUME.STATUS, fileVolume.getStatus().getPrimitiveValue());
        record.set(FILE_VOLUME.FK_FILE_VOLUME_STATE_ID, fileVolume.getVolumeState().getId());
    }

    private FSVolume recordToVolume(Record record) {
        final FSVolumeState fileSystemVolumeState = new FSVolumeState(
                record.get(FILE_VOLUME_STATE.ID),
                record.get(FILE_VOLUME_STATE.VERSION),
                record.get(FILE_VOLUME_STATE.BYTES_USED),
                record.get(FILE_VOLUME_STATE.BYTES_FREE),
                record.get(FILE_VOLUME_STATE.BYTES_TOTAL),
                record.get(FILE_VOLUME_STATE.UPDATE_TIME_MS));
        return recordToVolume(record, fileSystemVolumeState);
    }

    private FSVolume recordToVolume(Record record, final FSVolumeState fileSystemVolumeState) {
        final FSVolume fileVolume = new FSVolume();
        fileVolume.setId(record.get(FILE_VOLUME.ID));
        fileVolume.setVersion(record.get(FILE_VOLUME.VERSION));
        fileVolume.setCreateTimeMs(record.get(FILE_VOLUME.CREATE_TIME_MS));
        fileVolume.setCreateUser(record.get(FILE_VOLUME.CREATE_USER));
        fileVolume.setUpdateTimeMs(record.get(FILE_VOLUME.UPDATE_TIME_MS));
        fileVolume.setUpdateUser(record.get(FILE_VOLUME.UPDATE_USER));
        fileVolume.setPath(record.get(FILE_VOLUME.PATH));
        fileVolume.setStatus(VolumeUseStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(record.get(FILE_VOLUME.STATUS)));
        fileVolume.setByteLimit(record.get(FILE_VOLUME.BYTE_LIMIT));
        fileVolume.setVolumeState(fileSystemVolumeState);
        return fileVolume;
    }

    private Optional<Condition> volumeStatusCriteriaSetToCondition(final TableField<?, ?> field, final CriteriaSet<VolumeUseStatus> criteriaSet) {
        final CriteriaSet<Number> set = new CriteriaSet<>();
        set.setMatchAll(criteriaSet.getMatchAll());
        set.setMatchNull(criteriaSet.getMatchNull());
        set.setSet(criteriaSet.getSet().stream().map(VolumeUseStatus::getPrimitiveValue).collect(Collectors.toSet()));
        return criteriaSetToCondition(field, set);
    }

    private Optional<Condition> criteriaSetToCondition(final TableField<?, ?> field, final CriteriaSet<Number> criteriaSet) {
        if (criteriaSet == null || !criteriaSet.isConstrained()) {
            return Optional.empty();
        }

        if (criteriaSet.isMatchNothing()) {
            return Optional.of(field.in(Collections.emptySet()));
        }

        if (criteriaSet.getMatchNull() != null && criteriaSet.getMatchNull()) {
            if (criteriaSet.getSet().isEmpty()) {
                return Optional.of(field.isNull());
            }
            return Optional.of(field.in(criteriaSet.getSet()).or(field.isNull()));
        }

        return Optional.of(field.in(criteriaSet.getSet()));
    }


    @Override
    public FSVolume getVolume() {
        return security.insecureResult(() -> {
            final Set<FSVolume> set = getVolumeSet(VolumeUseStatus.ACTIVE);
            if (set.size() > 0) {
                return set.iterator().next();
            }
            return null;
        });
    }

    private Set<FSVolume> getVolumeSet(final VolumeUseStatus streamStatus) {
        final FileVolumeSelector volumeSelector = getVolumeSelector();
        final List<FSVolume> allVolumeList = getCurrentState();
        final List<FSVolume> freeVolumes = FileVolumeListUtil.removeFullVolumes(allVolumeList);
        Set<FSVolume> set = Collections.emptySet();

        final List<FSVolume> filteredVolumeList = getFilteredVolumeList(freeVolumes, streamStatus);
        if (filteredVolumeList.size() > 0) {
            set = Collections.singleton(volumeSelector.select(filteredVolumeList));
        }

        return set;
    }

    private List<FSVolume> getFilteredVolumeList(final List<FSVolume> allVolumes, final VolumeUseStatus streamStatus) {
        final List<FSVolume> list = new ArrayList<>();
        for (final FSVolume volume : allVolumes) {
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

    private FileVolumeSelector getVolumeSelector() {
        FileVolumeSelector volumeSelector = null;

        try {
            final String value = volumeConfig.getVolumeSelector();
            if (value != null) {
                volumeSelector = volumeSelectorMap.get(value);
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage());
        }

        if (volumeSelector == null) {
            volumeSelector = DEFAULT_VOLUME_SELECTOR;
        }

        return volumeSelector;
    }


    @Override
    public void onChange(final EntityEvent event) {
        currentVolumeState.set(null);
    }

    private void fireChange(final EntityAction action) {
        currentVolumeState.set(null);
        if (entityEventBusProvider != null) {
            try {
                final EntityEventBus entityEventBus = entityEventBusProvider.get();
                if (entityEventBus != null) {
                    entityEventBus.fire(new EntityEvent(EVENT_DOCREF, action));
                }
            } catch (final RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void clear() {
        // Clear state between tests.
        currentVolumeState.set(null);
        createdDefaultVolumes = false;
    }

    private List<FSVolume> getCurrentState() {
        List<FSVolume> state = currentVolumeState.get();
        if (state == null) {
            synchronized (this) {
                state = currentVolumeState.get();
                if (state == null) {
                    state = refresh();
                    currentVolumeState.set(state);
                }
            }
        }
        return state;
    }

    @Override
    public void flush() {
        refresh();
    }

    void updateStatus() {
        if (clusterLockService.tryLock(LOCK_NAME)) {
            try {
                refresh();
            } finally {
                clusterLockService.releaseLock(LOCK_NAME);
            }
        }
    }

    public List<FSVolume> refresh() {
        final List<FSVolume> newState = new ArrayList<>();

        final FindFSVolumeCriteria findVolumeCriteria = new FindFSVolumeCriteria();
        findVolumeCriteria.addSort(FindFSVolumeCriteria.FIELD_ID, Direction.ASCENDING, false);
        final List<FSVolume> volumeList = find(findVolumeCriteria);
        for (final FSVolume volume : volumeList) {
            FSVolumeState volumeState = updateVolumeState(volume);
            volumeState = saveVolumeState(volumeState);
            volume.setVolumeState(volumeState);

            // Record some statistics for the use of this volume.
            recordStats(volume);
            newState.add(volume);
        }

        return newState;
    }

    private void recordStats(final FSVolume volume) {
        if (statisticsReceiver != null) {
                try {
                    final FSVolumeState volumeState = volume.getVolumeState();

                    final long now = System.currentTimeMillis();
                    final List<InternalStatisticEvent> events = new ArrayList<>();
                    addStatisticEvent(events, now, volume, "Limit", volume.getByteLimit());
                    addStatisticEvent(events, now, volume, "Used", volumeState.getBytesUsed());
                    addStatisticEvent(events, now, volume, "Free", volumeState.getBytesFree());
                    addStatisticEvent(events, now, volume, "Total", volumeState.getBytesTotal());
                    statisticsReceiver.putEvents(events);
                } catch (final RuntimeException e) {
                    LOGGER.warn(e.getMessage());
                    LOGGER.debug(e.getMessage(), e);
                }
        }
    }

    private void addStatisticEvent(final List<InternalStatisticEvent> events,
                                   final long timeMs,
                                   final FSVolume volume,
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

    private FSVolumeState updateVolumeState(final FSVolume volume) {
        final FSVolumeState volumeState = volume.getVolumeState();
        volumeState.setUpdateTimeMs(System.currentTimeMillis());
        final Path path = Paths.get(volume.getPath());

        // Ensure the path exists
        if (Files.isDirectory(path)) {
            LOGGER.debug("updateVolumeState() path exists: " + path);
            setSizes(path, volumeState);
        } else {
            try {
                Files.createDirectories(path);
                LOGGER.debug("updateVolumeState() path created: " + path);
                setSizes(path, volumeState);
            } catch (final IOException e) {
                LOGGER.error("updateVolumeState() path not created: " + path);
            }
        }

        LOGGER.debug("updateVolumeState() exit" + volume);
        return volumeState;
    }

    private void setSizes(final Path path, final FSVolumeState volumeState) {
        try {
            final FileStore fileStore = Files.getFileStore(path);
            final long usableSpace = fileStore.getUsableSpace();
            final long freeSpace = fileStore.getUnallocatedSpace();
            final long totalSpace = fileStore.getTotalSpace();

            volumeState.setBytesTotal(totalSpace);
            volumeState.setBytesFree(usableSpace);
            volumeState.setBytesUsed(totalSpace - freeSpace);
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    FSVolumeState saveVolumeState(final FSVolumeState volumeState) {
        return fileSystemVolumeStateDao.update(volumeState);
    }

    void ensureDefaultVolumes() {
        if (!createdDefaultVolumes) {
            createDefaultVolumes();
        }
    }

    private synchronized void createDefaultVolumes() {
        if (!createdDefaultVolumes && !creatingDefaultVolumes) {
            creatingDefaultVolumes = true;

            security.insecure(() -> {
                final boolean isEnabled = volumeConfig.isCreateDefaultOnStart();

                if (isEnabled) {
                    final List<FSVolume> existingVolumes = getCurrentState();
                    if (existingVolumes.size() == 0) {
                        final Optional<Path> optDefaultVolumePath = getDefaultVolumesPath();
                        if (optDefaultVolumePath.isPresent()) {
                            LOGGER.info("Creating default volumes");
                            final Path streamVolPath = optDefaultVolumePath.get().resolve(DEFAULT_STREAM_VOLUME_SUBDIR);
                            createVolume(streamVolPath);
                        } else {
                            LOGGER.warn("No suitable directory to create default volumes in");
                        }
                    } else {
                        LOGGER.info("Existing volumes exist, won't create default volumes");
                    }
                } else {
                    LOGGER.info("Creation of default volumes is currently disabled");
                }

                createdDefaultVolumes = true;
                creatingDefaultVolumes = false;
            });
        }
    }

    private void createVolume(final Path path) {
        final FSVolume fileVolume = new FSVolume();
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
//            final FileVolume result = contextWithOptimisticLocking(connectionProvider, (context) -> {
//                AuditUtil.stamp(securityContext.getUserId(), fileVolume);
//                FileVolumeRecord record = context.newRecord(FILE_VOLUME, fileVolume);
//                record.set(FILE_VOLUME.STATUS, fileVolume.getStatus().getPrimitiveValue());
//                record.set(FILE_VOLUME.FK_FILE_VOLUME_STATE_ID, fileVolumeState.getId());
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
            return OptionalLong.of((long) (totalBytes * 0.9));
        } catch (IOException e) {
            LOGGER.warn("Unable to determine the total space on the filesystem for path: ", FileUtil.getCanonicalPath(path));
            return OptionalLong.empty();
        }
    }

    private Optional<Path> getDefaultVolumesPath() {
        return Stream.<Supplier<Optional<Path>>>of(
                this::getApplicationJarDir,
                () -> Optional.of(FileUtil.getTempDir()),
                Optional::empty)
                .map(Supplier::get)
                .filter(Optional::isPresent)
                .findFirst()
                .map(Optional::get)
                .flatMap(path -> Optional.of(path.resolve(DEFAULT_VOLUMES_SUBDIR)));
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
            LOGGER.warn("Unable to determine application jar directory due to: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
