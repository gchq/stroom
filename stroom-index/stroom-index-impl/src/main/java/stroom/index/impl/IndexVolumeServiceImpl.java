package stroom.index.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.impl.selection.VolumeConfig;
import stroom.index.shared.IndexException;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolumeFields;
import stroom.index.shared.IndexVolumeGroup;
import stroom.index.shared.ValidationResult;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionUtil;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.statistics.api.InternalStatisticEvent;
import stroom.statistics.api.InternalStatisticKey;
import stroom.statistics.api.InternalStatisticsReceiver;
import stroom.task.api.TaskContext;
import stroom.util.AuditUtil;
import stroom.util.NextNameGenerator;
import stroom.util.NullSafe;
import stroom.util.date.DateUtil;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.io.FileUtil;
import stroom.util.io.capacity.HasCapacitySelector;
import stroom.util.io.capacity.HasCapacitySelectorFactory;
import stroom.util.io.capacity.MostFreeCapacitySelector;
import stroom.util.io.capacity.MostFreePercentCapacitySelector;
import stroom.util.io.capacity.RandomCapacitySelector;
import stroom.util.io.capacity.RoundRobinCapacitySelector;
import stroom.util.io.capacity.RoundRobinIgnoreLeastFreeCapacitySelector;
import stroom.util.io.capacity.RoundRobinIgnoreLeastFreePercentCapacitySelector;
import stroom.util.io.capacity.WeightedFreePercentRandomCapacitySelector;
import stroom.util.io.capacity.WeightedFreeRandomCapacitySelector;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.ResultPage;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;

import com.google.common.collect.ImmutableSortedMap;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import static com.google.common.base.Strings.isNullOrEmpty;

@Singleton // Because of currentVolumeMap
@EntityEventHandler(type = IndexVolumeServiceImpl.ENTITY_TYPE, action = {
        EntityAction.UPDATE,
        EntityAction.CREATE,
        EntityAction.DELETE})
@EntityEventHandler(type = IndexVolumeGroupService.ENTITY_TYPE, action = {
        EntityAction.UPDATE,
        EntityAction.CREATE,
        EntityAction.DELETE})
public class IndexVolumeServiceImpl implements IndexVolumeService, Clearable, EntityEvent.Handler, HasSystemInfo {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexVolumeServiceImpl.class);

    static final String ENTITY_TYPE = "INDEX_VOLUME";
    private static final DocRef EVENT_DOCREF = new DocRef(ENTITY_TYPE, null, null);
    private static final String CACHE_NAME = "Index Volume Selector Cache";

    private static final Map<String, HasCapacitySelector> VOLUME_SELECTOR_MAP = Stream.of(
                    new MostFreePercentCapacitySelector(),
                    new MostFreeCapacitySelector(),
                    new RandomCapacitySelector(),
                    new RoundRobinIgnoreLeastFreePercentCapacitySelector(),
                    new RoundRobinIgnoreLeastFreeCapacitySelector(),
                    new RoundRobinCapacitySelector(),
                    new WeightedFreePercentRandomCapacitySelector(),
                    new WeightedFreeRandomCapacitySelector()
            )
            .collect(Collectors.toMap(HasCapacitySelector::getName, Function.identity()));

    private final IndexVolumeDao indexVolumeDao;
    private final SecurityContext securityContext;
    private final NodeInfo nodeInfo;
    private final InternalStatisticsReceiver statisticsReceiver;
    private final TaskContext taskContext;
    private final Provider<EntityEventBus> entityEventBusProvider;
    private final Provider<VolumeConfig> volumeConfigProvider;
    private final IndexVolumeGroupService indexVolumeGroupService;
    private final ICache<String, HasCapacitySelector> volGroupToVolSelectorCache;
    private final HasCapacitySelectorFactory hasCapacitySelectorFactory;
    private final NodeService nodeService;

    // Holds map of groupName => index vols BELONGING TO THIS NODE
    private final AtomicReference<VolumeMap> currentVolumeMap = new AtomicReference<>();

    @Inject
    IndexVolumeServiceImpl(final IndexVolumeDao indexVolumeDao,
                           final SecurityContext securityContext,
                           final NodeInfo nodeInfo,
                           final InternalStatisticsReceiver statisticsReceiver,
                           final TaskContext taskContext,
                           final Provider<EntityEventBus> entityEventBusProvider,
                           final Provider<VolumeConfig> volumeConfigProvider,
                           final IndexVolumeGroupService indexVolumeGroupService,
                           final CacheManager cacheManager,
                           final HasCapacitySelectorFactory hasCapacitySelectorFactory,
                           final NodeService nodeService) {
        this.indexVolumeDao = indexVolumeDao;
        this.securityContext = securityContext;
        this.nodeInfo = nodeInfo;
        this.statisticsReceiver = statisticsReceiver;
        this.taskContext = taskContext;
        this.entityEventBusProvider = entityEventBusProvider;
        this.volumeConfigProvider = volumeConfigProvider;
        this.indexVolumeGroupService = indexVolumeGroupService;
        this.hasCapacitySelectorFactory = hasCapacitySelectorFactory;
        this.nodeService = nodeService;

        // Most selectors are stateful, and we need one per vol grp so the round-robin works.
        this.volGroupToVolSelectorCache = cacheManager.create(
                CACHE_NAME,
                () -> volumeConfigProvider.get().getVolumeSelectorCache(),
                volGroupName -> createVolumeSelector());
    }

    private HasCapacitySelector createVolumeSelector() {
        String requiredSelectorName = null;

        try {
            requiredSelectorName = volumeConfigProvider.get().getVolumeSelector();
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage);
        }
        return hasCapacitySelectorFactory.createSelectorOrDefault(requiredSelectorName);
    }

    @Override
    public ResultPage<IndexVolume> find(final ExpressionCriteria criteria) {
        return securityContext.secureResult(() -> indexVolumeDao.find(criteria));
    }

    @Override
    public ValidationResult validate(final IndexVolume indexVolume) {
        ValidationResult validationResult = ValidationResult.ok();
        if (indexVolume.getNodeName() == null || indexVolume.getNodeName().isEmpty()) {
            validationResult = ValidationResult.error("You must select a node for the volume.");
        }
        if (validationResult.isOk()
                && NullSafe.isBlankString(indexVolume, IndexVolume::getPath)) {
            validationResult = ValidationResult.error("You must provide a path for the volume.");
        }
        if (validationResult.isOk()) {
            validationResult = validateForDupPathInOtherGroups(indexVolume);
        }
        if (validationResult.isOk()) {
            validationResult = validateForDupPathInThisGroup(indexVolume);
        }
        if (validationResult.isOk()) {
            validationResult = validateVolumePath(indexVolume.getPath());
        }
        return validationResult;
    }

    private ValidationResult validateForDupPathInThisGroup(final IndexVolume indexVolume) {
        final List<IndexVolume> volumeGroups = securityContext.secureResult(() ->
                indexVolumeDao.getVolumesInGroup(indexVolume.getIndexVolumeGroupId()));

        final boolean foundDupPathAndNode = volumeGroups.stream()
                .anyMatch(vol ->
                        !Objects.equals(vol.getId(), indexVolume.getId())
                                && Objects.equals(vol.getPath(), indexVolume.getPath())
                                && Objects.equals(vol.getNodeName(), indexVolume.getNodeName()));

        if (foundDupPathAndNode) {
            return ValidationResult.error(
                    LogUtil.message("An index volume already exists in this group with node '{}' and path '{}'.",
                            indexVolume.getNodeName(),
                            indexVolume.getPath()));
        } else {
            return ValidationResult.ok();
        }
    }

    private ValidationResult validateForDupPathInOtherGroups(final IndexVolume indexVolume) {
        // Get all groups holding with this path and node name
        final Set<IndexVolumeGroup> volumeGroups = securityContext.secureResult(() -> indexVolumeDao.getGroups(
                indexVolume.getNodeName(),
                indexVolume.getPath()));

        final Set<String> dupGroupNames = volumeGroups.stream()
                .filter(grp -> !Objects.equals(indexVolume.getIndexVolumeGroupId(), grp.getId()))
                .map(IndexVolumeGroup::getName)
                .collect(Collectors.toSet());

        if (!dupGroupNames.isEmpty()) {
            return ValidationResult.warning(LogUtil.message("""
                            Path '{}' on node '{}' is already a member of index volume Group{} {}.
                            It is NOT recommended to have the same index volume node and path belonging to \
                            multiple volume groups. Click OK to ignore this and set it anyway.""",
                    indexVolume.getPath(),
                    indexVolume.getNodeName(),
                    (dupGroupNames.size() > 1
                            ? "s"
                            : ""),
                    dupGroupNames.stream()
                            .map(grp -> "'" + grp + "'")
                            .collect(Collectors.joining(", "))));
        } else {
            return ValidationResult.ok();
        }
    }

    private ValidationResult validateVolumePath(final String indexVolPath) {
        final Path path = Paths.get(indexVolPath);

        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                final String msg;
                if (indexVolPath.equals(e.getMessage())) {
                    // Some java IO exceptions just have the path as the message, helpful.
                    msg = e.getClass().getSimpleName();
                } else {
                    msg = e.getMessage();
                }
                return ValidationResult.error(LogUtil.message(
                        "Error creating index volume path '{}': {}",
                        indexVolPath,
                        msg));
            }
        } else if (!Files.isDirectory(path)) {
            return ValidationResult.error(LogUtil.message(
                    "Error creating index volume path '{}': The path exists but is not a directory.",
                    indexVolPath));
        }
        return ValidationResult.ok();
    }

    @Override
    public IndexVolume create(IndexVolume indexVolume) {
        AuditUtil.stamp(securityContext.getUserId(), indexVolume);

        final List<String> names = indexVolumeDao.getAll().stream().map(i -> isNullOrEmpty(i.getNodeName())
                        ? ""
                        : i.getNodeName())
                .collect(Collectors.toList());
        indexVolume.setNodeName(isNullOrEmpty(indexVolume.getNodeName())
                ? NextNameGenerator.getNextName(names, "New index volume")
                : indexVolume.getNodeName());
        indexVolume.setPath(isNullOrEmpty(indexVolume.getPath())
                ? null
                : indexVolume.getPath());
        indexVolume.setIndexVolumeGroupId(indexVolume.getIndexVolumeGroupId());

        final IndexVolume result = securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeDao.create(indexVolume));
        fireChange(EntityAction.CREATE);
        return result;
    }

    @Override
    public IndexVolume read(final int id) {
        return securityContext.secureResult(() -> indexVolumeDao.fetch(id).orElse(null));
    }

    @Override
    public IndexVolume update(IndexVolume indexVolume) {
        final IndexVolume loadedIndexVolume = securityContext.secureResult(() ->
                indexVolumeDao.fetch(indexVolume.getId()).orElse(
                        null));

        loadedIndexVolume.setIndexVolumeGroupId(indexVolume.getIndexVolumeGroupId());
        loadedIndexVolume.setPath((indexVolume.getPath()));
        loadedIndexVolume.setNodeName(indexVolume.getNodeName());
        loadedIndexVolume.setBytesLimit(indexVolume.getBytesLimit());
        loadedIndexVolume.setState(indexVolume.getState());

        AuditUtil.stamp(securityContext.getUserId(), loadedIndexVolume);

        final IndexVolume result = securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeDao.update(loadedIndexVolume));
        fireChange(EntityAction.UPDATE);
        return result;
    }

    @Override
    public Boolean delete(final int id) {
        final Boolean result = securityContext.secureResult(PermissionNames.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeDao.delete(id));
        fireChange(EntityAction.DELETE);
        return result;
    }

    @Override
    public void rescan() {
        internalRescan();
    }

    private synchronized VolumeMap internalRescan() {
        // Update the index stats for all indexes belonging to this node so all
        // nodes can pile in and do this at the same time
        final String nodeName = nodeInfo.getThisNodeName();
        taskContext.info(() -> "Updating index volume status for node " + nodeName);
        final ExpressionOperator expression = ExpressionUtil.equals(IndexVolumeFields.NODE_NAME, nodeName);
        final List<IndexVolume> volumes = find(new ExpressionCriteria(expression)).getValues();
        for (final IndexVolume volume : volumes) {
            taskContext.info(() -> "Updating index volume status for '" + volume.getPath() + "' and node " + nodeName);
            updateVolumeState(volume);

            // Record some statistics for the use of this volume.
            recordStats(volume);
        }

        taskContext.info(() -> "Caching index volumes for node " + nodeName);
        // Now cache the index vols for this node, grouped by vol group name
        final VolumeMap newMap = new VolumeMap(
                System.currentTimeMillis(),
                indexVolumeDao.getVolumesOnNodeGrouped(nodeName));

        final VolumeMap currentList = currentVolumeMap.get();
        if (currentList == null || currentList.createTime < newMap.createTime) {
            currentVolumeMap.set(newMap);
        }
        return newMap;
    }

    private IndexVolume updateVolumeState(final IndexVolume volume) {
        final Path path = Paths.get(volume.getPath());

        // Ensure the path exists
        if (Files.isDirectory(path)) {
            LOGGER.debug(() -> LogUtil.message("updateVolumeState() path exists: {}", path));
            setSizes(path, volume);
        } else {
            try {
                Files.createDirectories(path);
                LOGGER.debug(() -> LogUtil.message("updateVolumeState() path created: {}", path));
                setSizes(path, volume);
            } catch (final IOException e) {
                LOGGER.error(() -> LogUtil.message("updateVolumeState() path not created: {}", path));
            }
        }

        LOGGER.debug(() -> LogUtil.message("updateVolumeState() exit {}", volume));
        return volume;
    }

    private void setSizes(final Path path, final IndexVolume indexVolume) {
        try {
            final FileStore fileStore = Files.getFileStore(path);
            final long osUsableSpace = fileStore.getUsableSpace();
            final long osFreeSpace = fileStore.getUnallocatedSpace();
            final long totalSpace = fileStore.getTotalSpace();
            final long usedSpace = totalSpace - osFreeSpace;
            // Calc free space based on the limit if one is set
            final long freeSpace = indexVolume.getCapacityLimitBytes()
                    .stream()
                    .map(limit -> Math.max(limit - usedSpace, 0))
                    .findAny()
                    .orElse(osUsableSpace);

            indexVolume.setUpdateTimeMs(System.currentTimeMillis());
            indexVolume.setBytesTotal(totalSpace);
            indexVolume.setBytesFree(freeSpace);
            indexVolume.setBytesUsed(usedSpace);

            indexVolumeDao.updateVolumeState(
                    indexVolume.getId(),
                    indexVolume.getUpdateTimeMs(),
                    indexVolume.getBytesUsed(),
                    indexVolume.getBytesFree(),
                    indexVolume.getBytesTotal());
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private void recordStats(final IndexVolume volume) {
        if (statisticsReceiver != null) {
            try {
                final long now = System.currentTimeMillis();
                final List<InternalStatisticEvent> events = new ArrayList<>();
                addStatisticEvent(events, now, volume, "Limit", volume.getBytesLimit());
                addStatisticEvent(events, now, volume, "Used", volume.getBytesUsed());
                addStatisticEvent(events, now, volume, "Free", volume.getBytesFree());
                addStatisticEvent(events, now, volume, "Total", volume.getBytesTotal());
                statisticsReceiver.putEvents(events);
            } catch (final RuntimeException e) {
                LOGGER.warn(e::getMessage);
                LOGGER.debug(e::getMessage, e);
            }
        }
    }

    private void addStatisticEvent(final List<InternalStatisticEvent> events,
                                   final long timeMs,
                                   final IndexVolume volume,
                                   final String type,
                                   final Long bytes) {
        if (bytes != null) {
            SortedMap<String, String> tags = ImmutableSortedMap.<String, String>naturalOrder()
                    .put("Id", String.valueOf(volume.getId()))
                    .put("Path", volume.getPath())
                    .put("Type", type)
                    .put("Node", nodeInfo.getThisNodeName())
                    .build();

            InternalStatisticEvent event = InternalStatisticEvent.createValueStat(
                    InternalStatisticKey.VOLUMES, timeMs, tags, bytes.doubleValue());
            events.add(event);
        }
    }

    @Override
    public void clear() {
        // Delete the contents of all index volumes.
        find(new ExpressionCriteria()).getValues()
                .forEach(volume -> {
                    // The parent will also pick up the index shard (as well as the
                    // store)
                    LOGGER.info("Clearing index volume {}", volume.getPath());
                    FileUtil.deleteContents(Paths.get(volume.getPath()));
                });
        currentVolumeMap.set(null);
    }

    @Override
    public IndexVolume selectVolume(final String groupName, final String nodeName) {
        List<IndexVolume> indexVolumes;

        // Make sure the default group and vols exist
        indexVolumeGroupService.ensureDefaultVolumes();

        if (nodeInfo.getThisNodeName().equals(nodeName)) {
            // we can check local vol map
            indexVolumes = getCurrentVolumeMap().getVolumes(groupName)
                    .orElseGet(() -> indexVolumeDao.getVolumesInGroupOnNode(groupName, nodeName));
        } else {
            // Not this node so have to read the DB
            indexVolumes = indexVolumeDao.getVolumesInGroupOnNode(groupName, nodeName);
        }

        indexVolumes = removeFullVolumes(indexVolumes);

        final HasCapacitySelector volumeSelector = getVolumeSelector(groupName);

        final IndexVolume indexVolume = volumeSelector.select(indexVolumes);

        if (indexVolume == null) {
            throw new IndexException(
                    "Unable to find any non-full index volumes for index volume group '"
                            + groupName + "' for node " + nodeName);
        }

        return indexVolume;
    }

    private static List<IndexVolume> removeFullVolumes(final List<IndexVolume> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        } else {
            return list.stream()
                    .filter(indexVolume -> !indexVolume.isFull())
                    .collect(Collectors.toList());
        }
    }

    private VolumeMap getCurrentVolumeMap() {
        VolumeMap volumeMap = currentVolumeMap.get();
        if (volumeMap == null) {
            synchronized (this) {
                volumeMap = currentVolumeMap.get();
                if (volumeMap == null) {
                    volumeMap = internalRescan();
                    currentVolumeMap.set(volumeMap);
                }
            }
        }
        return volumeMap;
    }

    @Override
    public void onChange(final EntityEvent event) {
        // Simpler to just clear it all out and reload
        LOGGER.debug("Clearing currentVolumeMap");
        currentVolumeMap.set(null);
    }

    private void fireChange(final EntityAction action) {
        currentVolumeMap.set(null);
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

    private HasCapacitySelector getVolumeSelector(final String groupName) {
        HasCapacitySelector currentSelector = volGroupToVolSelectorCache.get(groupName);

        String requiredSelectorName = HasCapacitySelectorFactory.DEFAULT_SELECTOR_NAME;

        try {
            requiredSelectorName = volumeConfigProvider.get().getVolumeSelector();
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage);
        }

        if (!currentSelector.getName().equals(requiredSelectorName)) {
            synchronized (this) {
                currentSelector = volGroupToVolSelectorCache.get(groupName);
                // Retest under lock
                if (!currentSelector.getName().equals(requiredSelectorName)) {
                    // Config has changed so replace the selector with the configured one
                    volGroupToVolSelectorCache.remove(groupName);
                    currentSelector = volGroupToVolSelectorCache.get(groupName);
                }
            }
        }
        return currentSelector;
    }

    @Override
    public SystemInfoResult getSystemInfo() {
        final VolumeMap volumeMap = getCurrentVolumeMap();

        // Need to wrap with optional as Map.ofEntries does not support null values.
        final var volInfoMap = volumeMap.getGroupNameToVolumesMap()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Entry::getKey,
                        entry -> entry.getValue()
                                .stream()
                                .map(vol -> Map.ofEntries(
                                        new SimpleEntry<>("path", Optional.ofNullable(vol.getPath())),
                                        new SimpleEntry<>("limit", Optional.ofNullable(vol.getBytesLimit())),
                                        new SimpleEntry<>("state", Optional.ofNullable(vol.getState())),
                                        new SimpleEntry<>("free", Optional.ofNullable(vol.getBytesFree())),
                                        new SimpleEntry<>("total", Optional.ofNullable(vol.getBytesTotal())),
                                        new SimpleEntry<>("used", Optional.ofNullable(vol.getBytesUsed())),
                                        new SimpleEntry<>("dbStateUpdateTime", Optional.ofNullable(NullSafe.get(
                                                vol,
                                                IndexVolume::getUpdateTimeMs,
                                                DateUtil::createNormalDateTimeString)))))
                                .collect(Collectors.toList())));

        return SystemInfoResult.builder(this)
                .addDetail("volumeSelector", volumeConfigProvider.get().getVolumeSelector())
                .addDetail("volumeListCreateTime", DateUtil.createNormalDateTimeString(volumeMap.getCreateTime()))
                .addDetail("volumeGroups", volInfoMap)
                .build();
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private static class VolumeMap {

        private final long createTime;
        private final Map<String, List<IndexVolume>> groupNameToVolumesMap;

        VolumeMap(final long createTime, final Map<String, List<IndexVolume>> groupNameToVolumesMap) {
            this.createTime = createTime;
            this.groupNameToVolumesMap = groupNameToVolumesMap;
        }

        public Map<String, List<IndexVolume>> getGroupNameToVolumesMap() {
            return groupNameToVolumesMap;
        }

        public Optional<List<IndexVolume>> getVolumes(final String groupName) {
            return Optional.ofNullable(groupNameToVolumesMap.get(groupName));
        }

        public long getCreateTime() {
            return createTime;
        }
    }
}
