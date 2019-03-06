package stroom.data.store.impl.fs;

import com.google.common.collect.ImmutableMap;
import org.jooq.Condition;
import org.jooq.Record;
import org.jooq.TableField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.lock.api.ClusterLockService;
import stroom.data.store.impl.fs.db.jooq.tables.records.FsVolumeRecord;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolume.VolumeUseStatus;
import stroom.data.store.impl.fs.shared.FsVolumeState;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
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

import static stroom.data.store.impl.fs.db.jooq.tables.FsVolume.FS_VOLUME;
import static stroom.data.store.impl.fs.db.jooq.tables.FsVolumeState.FS_VOLUME_STATE;
import static stroom.db.util.JooqUtil.contextWithOptimisticLocking;

@Singleton
@EntityEventHandler(type = FsVolumeServiceImpl.ENTITY_TYPE, action = {EntityAction.CREATE, EntityAction.DELETE})
public class FsVolumeServiceImpl implements FsVolumeService, EntityEvent.Handler {
    private static final Logger LOGGER = LoggerFactory.getLogger(DataVolumeServiceImpl.class);

    private static final String LOCK_NAME = "REFRESH_FS_VOLUMES";
    static final String ENTITY_TYPE = "FILE_SYSTEM_VOLUME";
    private static final DocRef EVENT_DOCREF = new DocRef(ENTITY_TYPE, null, null);

    static final Path DEFAULT_VOLUMES_SUBDIR = Paths.get("volumes");
    static final Path DEFAULT_STREAM_VOLUME_SUBDIR = Paths.get("defaultStreamVolume");

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

    private final ConnectionProvider connectionProvider;
    private final Security security;
    private final SecurityContext securityContext;
    private final FsVolumeConfig volumeConfig;
    private final InternalStatisticsReceiver statisticsReceiver;
    private final FsVolumeStateDao fileSystemVolumeStateDao;
    private final ClusterLockService clusterLockService;
    private final Provider<EntityEventBus> entityEventBusProvider;

    private final AtomicReference<List<FsVolume>> currentVolumeState = new AtomicReference<>();

    private volatile boolean createdDefaultVolumes;
    private boolean creatingDefaultVolumes;

    @Inject
    FsVolumeServiceImpl(final ConnectionProvider connectionProvider,
                        final Security security,
                        final SecurityContext securityContext,
                        final FsVolumeConfig volumeConfig,
                        final InternalStatisticsReceiver statisticsReceiver,
                        final FsVolumeStateDao fileSystemVolumeStateDao,
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

    private static void registerVolumeSelector(final FsVolumeSelector volumeSelector) {
        volumeSelectorMap.put(volumeSelector.getName(), volumeSelector);
    }

    @Override
    public FsVolume create(final FsVolume fileVolume) {
        return security.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION, () -> {
            FsVolume result = null;
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
                fileVolume.setStatus(FsVolume.VolumeUseStatus.ACTIVE);

                final FsVolumeState fileVolumeState = fileSystemVolumeStateDao.create(new FsVolumeState());
                fileVolume.setVolumeState(fileVolumeState);

                result = contextWithOptimisticLocking(connectionProvider, (context) -> {
                    AuditUtil.stamp(securityContext.getUserId(), fileVolume);
                    final FsVolumeRecord record = context.newRecord(FS_VOLUME, fileVolume);
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
    public FsVolume update(final FsVolume fileVolume) {
        return security.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION, () -> {
            final FsVolume result = contextWithOptimisticLocking(connectionProvider, (context) -> {
                AuditUtil.stamp(securityContext.getUserId(), fileVolume);
                final FsVolumeRecord record = context.newRecord(FS_VOLUME, fileVolume);
                volumeToRecord(fileVolume, record);
                // This depends on there being a field named 'id' that is what we expect it to be.
                // I'd rather this was implicit/opinionated than forced into place with an interface.
                LOGGER.debug("Updating a {} with id {}", FS_VOLUME.getName(), record.getValue("id"));
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
                    .select(FS_VOLUME.FK_FS_VOLUME_STATE_ID)
                    .from(FS_VOLUME)
                    .where(FS_VOLUME.ID.eq(id))
                    .fetchOptional(FS_VOLUME.FK_FS_VOLUME_STATE_ID);

            final int result = context
                    .deleteFrom(FS_VOLUME)
                    .where(FS_VOLUME.ID.eq(id))
                    .execute();

            stateIdOptional.ifPresent(stateId -> context
                    .deleteFrom(FS_VOLUME_STATE)
                    .where(FS_VOLUME_STATE.ID.eq(stateId))
                    .execute());

            fireChange(EntityAction.DELETE);

            return result;
        }));
    }

    public FsVolume fetch(final int id) {
        return security.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION, () ->
                JooqUtil.contextResult(connectionProvider, context -> context
                        .select()
                        .from(FS_VOLUME)
                        .join(FS_VOLUME_STATE)
                        .on(FS_VOLUME_STATE.ID.eq(FS_VOLUME.FK_FS_VOLUME_STATE_ID))
                        .where(FS_VOLUME.ID.eq(id))
                        .fetchOptional()
                        .map(this::recordToVolume)
                        .orElse(null)));
    }

    @Override
    public BaseResultList<FsVolume> find(final FindFsVolumeCriteria criteria) {
        return security.insecureResult(() -> {
            ensureDefaultVolumes();

            final Optional<Condition> volumeStatusCondition = volumeStatusCriteriaSetToCondition(FS_VOLUME.STATUS, criteria.getStatusSet());
            final List<Condition> conditions = new ArrayList<>();
            volumeStatusCondition.ifPresent(conditions::add);

            final List<FsVolume> result = JooqUtil.contextResult(connectionProvider, context ->
                    JooqUtil.applyLimits(
                            context
                                    .select()
                                    .from(FS_VOLUME)
                                    .join(FS_VOLUME_STATE)
                                    .on(FS_VOLUME_STATE.ID.eq(FS_VOLUME.FK_FS_VOLUME_STATE_ID))
                                    .where(conditions)
                            , criteria.getPageRequest())
                            .fetch()
                            .map(this::recordToVolume));
            return BaseResultList.createCriterialBasedList(result, criteria);
        });
    }

    private void volumeToRecord(final FsVolume fileVolume, final FsVolumeRecord record) {
        record.set(FS_VOLUME.STATUS, fileVolume.getStatus().getPrimitiveValue());
        record.set(FS_VOLUME.FK_FS_VOLUME_STATE_ID, fileVolume.getVolumeState().getId());
    }

    private FsVolume recordToVolume(Record record) {
        final FsVolumeState fileSystemVolumeState = new FsVolumeState(
                record.get(FS_VOLUME_STATE.ID),
                record.get(FS_VOLUME_STATE.VERSION),
                record.get(FS_VOLUME_STATE.BYTES_USED),
                record.get(FS_VOLUME_STATE.BYTES_FREE),
                record.get(FS_VOLUME_STATE.BYTES_TOTAL),
                record.get(FS_VOLUME_STATE.UPDATE_TIME_MS));
        return recordToVolume(record, fileSystemVolumeState);
    }

    private FsVolume recordToVolume(Record record, final FsVolumeState fileSystemVolumeState) {
        final FsVolume fileVolume = new FsVolume();
        fileVolume.setId(record.get(FS_VOLUME.ID));
        fileVolume.setVersion(record.get(FS_VOLUME.VERSION));
        fileVolume.setCreateTimeMs(record.get(FS_VOLUME.CREATE_TIME_MS));
        fileVolume.setCreateUser(record.get(FS_VOLUME.CREATE_USER));
        fileVolume.setUpdateTimeMs(record.get(FS_VOLUME.UPDATE_TIME_MS));
        fileVolume.setUpdateUser(record.get(FS_VOLUME.UPDATE_USER));
        fileVolume.setPath(record.get(FS_VOLUME.PATH));
        fileVolume.setStatus(VolumeUseStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(record.get(FS_VOLUME.STATUS)));
        fileVolume.setByteLimit(record.get(FS_VOLUME.BYTE_LIMIT));
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
    public FsVolume getVolume() {
        return security.insecureResult(() -> {
            final Set<FsVolume> set = getVolumeSet(VolumeUseStatus.ACTIVE);
            if (set.size() > 0) {
                return set.iterator().next();
            }
            return null;
        });
    }

    private Set<FsVolume> getVolumeSet(final VolumeUseStatus streamStatus) {
        final FsVolumeSelector volumeSelector = getVolumeSelector();
        final List<FsVolume> allVolumeList = getCurrentState();
        final List<FsVolume> freeVolumes = FsVolumeListUtil.removeFullVolumes(allVolumeList);
        Set<FsVolume> set = Collections.emptySet();

        final List<FsVolume> filteredVolumeList = getFilteredVolumeList(freeVolumes, streamStatus);
        if (filteredVolumeList.size() > 0) {
            set = Collections.singleton(volumeSelector.select(filteredVolumeList));
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

    private List<FsVolume> getCurrentState() {
        List<FsVolume> state = currentVolumeState.get();
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
        clusterLockService.tryLock(LOCK_NAME, this::refresh);
    }

    public List<FsVolume> refresh() {
        final List<FsVolume> newState = new ArrayList<>();

        final FindFsVolumeCriteria findVolumeCriteria = new FindFsVolumeCriteria();
        findVolumeCriteria.addSort(FindFsVolumeCriteria.FIELD_ID, Direction.ASCENDING, false);
        final List<FsVolume> volumeList = find(findVolumeCriteria);
        for (final FsVolume volume : volumeList) {
            FsVolumeState volumeState = updateVolumeState(volume);
            volumeState = saveVolumeState(volumeState);
            volume.setVolumeState(volumeState);

            // Record some statistics for the use of this volume.
            recordStats(volume);
            newState.add(volume);
        }

        return newState;
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
                    LOGGER.warn(e.getMessage());
                    LOGGER.debug(e.getMessage(), e);
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
            LOGGER.error(e.getMessage(), e);
        }
    }

    FsVolumeState saveVolumeState(final FsVolumeState volumeState) {
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
                    final List<FsVolume> existingVolumes = getCurrentState();
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
//            final FileVolume result = contextWithOptimisticLocking(connectionProvider, (context) -> {
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
