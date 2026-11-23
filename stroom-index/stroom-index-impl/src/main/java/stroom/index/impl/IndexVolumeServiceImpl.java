/*
 * Copyright 2020 Crown Copyright
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

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.cluster.task.api.NodeNotFoundException;
import stroom.cluster.task.api.NullClusterStateException;
import stroom.cluster.task.api.TargetNodeSetFactory;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.api.IndexVolumeGroupService;
import stroom.index.impl.IndexVolumeGroupServiceImpl.IndexVolumeGroupEntityEventData;
import stroom.index.impl.selection.VolumeConfig;
import stroom.index.shared.IndexException;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.IndexVolume.VolumeUseState;
import stroom.index.shared.IndexVolumeGroup;
import stroom.index.shared.ValidationResult;
import stroom.node.api.NodeInfo;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.statistics.api.InternalStatisticEvent;
import stroom.statistics.api.InternalStatisticKey;
import stroom.statistics.api.InternalStatisticsReceiver;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.NextNameGenerator;
import stroom.util.date.DateUtil;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.io.capacity.HasCapacitySelector;
import stroom.util.io.capacity.HasCapacitySelectorFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.StringUtil;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSortedMap;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton // Because of currentVolumeMap
// We hold a cache of volumes by volGroup so we have to handle both
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
    private static final DocRef EVENT_DOCREF = new DocRef(ENTITY_TYPE, ENTITY_TYPE, ENTITY_TYPE);
    private static final String CACHE_NAME = "Index Volume Selector Cache";
    protected static final String TEMP_FILE_PREFIX = "stroomIdxVolVal";
    private static final String SHARED_TO_LOCAL_MESSAGE =
            "A shared index volume cannot be made local, because its shards may have been written by any node. " +
            "Add a new local volume instead.";

    private final IndexVolumeDao indexVolumeDao;
    private final SecurityContext securityContext;
    private final NodeInfo nodeInfo;
    private final InternalStatisticsReceiver statisticsReceiver;
    private final TaskContextFactory taskContextFactory;
    private final Provider<EntityEventBus> entityEventBusProvider;
    private final Provider<VolumeConfig> volumeConfigProvider;
    private final IndexVolumeGroupService indexVolumeGroupService;
    private final LoadingStroomCache<VolGroupNode, HasCapacitySelector> volGroupNodeToVolSelectorCache;
    private final HasCapacitySelectorFactory hasCapacitySelectorFactory;
    private final PathCreator pathCreator;
    private final Provider<TargetNodeSetFactory> targetNodeSetFactoryProvider;
    private final SharedVolumeChecker sharedVolumeChecker = new SharedVolumeChecker();

    // Holds map of groupName|nodeName => index vols but only entries for this node
    private final AtomicReference<VolumeMap> currentVolumeMap = new AtomicReference<>();

    @Inject
    IndexVolumeServiceImpl(final IndexVolumeDao indexVolumeDao,
                           final SecurityContext securityContext,
                           final NodeInfo nodeInfo,
                           final InternalStatisticsReceiver statisticsReceiver,
                           final TaskContextFactory taskContextFactory,
                           final Provider<EntityEventBus> entityEventBusProvider,
                           final Provider<VolumeConfig> volumeConfigProvider,
                           final IndexVolumeGroupService indexVolumeGroupService,
                           final CacheManager cacheManager,
                           final HasCapacitySelectorFactory hasCapacitySelectorFactory,
                           final PathCreator pathCreator,
                           final Provider<TargetNodeSetFactory> targetNodeSetFactoryProvider) {
        this.indexVolumeDao = indexVolumeDao;
        this.securityContext = securityContext;
        this.nodeInfo = nodeInfo;
        this.statisticsReceiver = statisticsReceiver;
        this.taskContextFactory = taskContextFactory;
        this.entityEventBusProvider = entityEventBusProvider;
        this.volumeConfigProvider = volumeConfigProvider;
        this.indexVolumeGroupService = indexVolumeGroupService;
        this.hasCapacitySelectorFactory = hasCapacitySelectorFactory;
        this.pathCreator = pathCreator;
        this.targetNodeSetFactoryProvider = targetNodeSetFactoryProvider;

        // Most selectors are stateful, and we need one per vol grp so the round-robin works.
        this.volGroupNodeToVolSelectorCache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> volumeConfigProvider.get().getVolumeSelectorCache(),
                this::createVolumeSelector);
    }

    private HasCapacitySelector createVolumeSelector(final VolGroupNode volGroupNode) {
        String requiredSelectorName = null;

        try {
            requiredSelectorName = volumeConfigProvider.get().getVolumeSelector();
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage);
        }
        LOGGER.debug("createVolumeSelector() - volGroupNode: {}, requiredSelectorName: {}",
                volGroupNode, requiredSelectorName);
        return hasCapacitySelectorFactory.createSelectorOrDefault(requiredSelectorName);
    }

    @Override
    public ResultPage<IndexVolume> find(final ExpressionCriteria criteria) {
        // Enumerating index volumes (server paths, capacity, usage, state) is a volume-management read, gated
        // like create/update/delete.
        return securityContext.secureResult(
                AppPermission.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeDao.find(criteria));
    }

    @Override
    public ValidationResult validate(final IndexVolume indexVolume) {

        ValidationResult validationResult = ValidationResult.ok();
        // A shared volume has no node, every node uses it.
        if (!indexVolume.isShared() && NullSafe.isBlankString(indexVolume, IndexVolume::getNodeName)) {
            validationResult = ValidationResult.error("You must select a node for a local volume.");
        }
        if (validationResult.isOk()
            && NullSafe.isBlankString(indexVolume, IndexVolume::getPath)) {
            validationResult = ValidationResult.error("You must provide a path for the volume.");
        }

        // Don't need to make absolute here as comparing like with like
        final Optional<IndexVolume> existingIndexVolume = indexVolume.getId() != null
                ? securityContext.secureResult(() -> indexVolumeDao.fetch(indexVolume.getId()))
                : Optional.empty();
        if (validationResult.isOk()
            && !indexVolume.isShared()
            && existingIndexVolume.filter(IndexVolume::isShared).isPresent()) {
            validationResult = ValidationResult.error(SHARED_TO_LOCAL_MESSAGE);
        }

        final boolean hasPathOrTypeChanged = existingIndexVolume.filter(existingVol ->
                        Objects.equals(existingVol.getPath(), indexVolume.getPath())
                        && existingVol.getVolumeType() == indexVolume.getVolumeType())
                .isEmpty();

        if (hasPathOrTypeChanged) {
            if (validationResult.isOk()) {
                validationResult = validateForDupPathInThisGroup(indexVolume);
            }
            if (validationResult.isOk()) {
                validationResult = validateVolumePath(indexVolume);
            }
            // Warnings come last so that they do not hide an error.
            if (validationResult.isOk()) {
                validationResult = combineWarnings(
                        validateForMerge(indexVolume),
                        validateForDupPathInOtherGroups(indexVolume));
            }
        }
        return validationResult;
    }

    // Only one result goes back to the user, so two warnings are shown together and confirmed once.
    private static ValidationResult combineWarnings(final ValidationResult first, final ValidationResult second) {
        if (first.isOk()) {
            return second;
        } else if (second.isOk()) {
            return first;
        } else {
            return ValidationResult.warning(first.getMessage() + "\n\n" + second.getMessage());
        }
    }

    // Validation is a separate call that a client may skip, and the database's unique key does not cover
    // volumes that have no node, so the rules that keep one volume per path are checked again on saving.
    private void requireNoDupPathInThisGroup(final IndexVolume indexVolume) {
        if (!NullSafe.isBlankString(indexVolume, IndexVolume::getPath)) {
            final ValidationResult validationResult = validateForDupPathInThisGroup(indexVolume);
            if (!validationResult.isOk()) {
                throw new IndexException(validationResult.getMessage());
            }
        }
    }

    @Override
    public ValidationResult validatePath(final IndexVolume indexVolume) {
        // The check creates the directory and a test file, so it needs the permission to manage volumes.
        return securityContext.secureResult(AppPermission.MANAGE_VOLUMES_PERMISSION, () ->
                NullSafe.isBlankString(indexVolume, IndexVolume::getPath)
                        ? ValidationResult.error("You must provide a path for the volume.")
                        : validateVolumePath(indexVolume));
    }

    private ValidationResult validateForDupPathInThisGroup(final IndexVolume indexVolume) {
        final String path = getAbsVolumePath(indexVolume);
        final List<IndexVolume> volumesWithSamePath = securityContext.secureResult(() ->
                        indexVolumeDao.getVolumesInGroup(indexVolume.getIndexVolumeGroupId()))
                .stream()
                .filter(dbVol -> !Objects.equals(dbVol.getId(), indexVolume.getId()))
                .filter(dbVol -> Objects.equals(getAbsVolumePath(dbVol), path))
                .toList();

        if (volumesWithSamePath.stream().anyMatch(IndexVolume::isShared)) {
            // A shared volume is the only volume in its group with its path, as it stands for every node.
            return ValidationResult.error(LogUtil.message(
                    "A shared index volume already exists in this group with path '{}'.",
                    indexVolume.getPath()));
        } else if (indexVolume.isShared()) {
            if (indexVolume.getId() == null && !volumesWithSamePath.isEmpty()) {
                return ValidationResult.error(LogUtil.message(
                        "A local index volume already exists in this group with path '{}'. " +
                        "Edit that volume and mark it as shared instead of adding a new one.",
                        indexVolume.getPath()));
            }
        } else if (volumesWithSamePath.stream().anyMatch(dbVol ->
                Objects.equals(dbVol.getNodeName(), indexVolume.getNodeName()))) {
            return ValidationResult.error(
                    LogUtil.message("An index volume already exists in this group with node '{}' and path '{}'.",
                            indexVolume.getNodeName(),
                            indexVolume.getPath()));
        }
        return ValidationResult.ok();
    }

    // Marking a volume as shared merges the other nodes' volumes for the same path into it, so say so first.
    private ValidationResult validateForMerge(final IndexVolume indexVolume) {
        if (indexVolume.isShared() && indexVolume.getId() != null) {
            final List<IndexVolume> volumesToMerge = securityContext.secureResult(() ->
                    getLocalVolumesWithSamePath(indexVolume));
            if (!volumesToMerge.isEmpty()) {
                return ValidationResult.warning(LogUtil.message("""
                                Marking this volume as shared will merge the local volume{} for path '{}' on node{} \
                                [{}] into it. Their shards will belong to this volume and the local volume{} will be \
                                removed.
                                Only do this if the path is the same shared mount on every node.
                                Click OK to mark the volume as shared.""",
                        StringUtil.pluralSuffix(volumesToMerge.size()),
                        indexVolume.getPath(),
                        StringUtil.pluralSuffix(volumesToMerge.size()),
                        volumesToMerge.stream()
                                .map(dbVol -> "'" + dbVol.getNodeName() + "'")
                                .sorted()
                                .collect(Collectors.joining(", ")),
                        StringUtil.pluralSuffix(volumesToMerge.size())));
            }
        }
        return ValidationResult.ok();
    }

    // The local volumes in the same group that have the same path as a shared volume and so are the same
    // storage. The shared volume replaces them.
    private List<IndexVolume> getLocalVolumesWithSamePath(final IndexVolume sharedVolume) {
        final String path = getAbsVolumePath(sharedVolume);
        return indexVolumeDao.getVolumesInGroup(sharedVolume.getIndexVolumeGroupId())
                .stream()
                .filter(dbVol -> !Objects.equals(dbVol.getId(), sharedVolume.getId()))
                .filter(dbVol -> !dbVol.isShared())
                .filter(dbVol -> Objects.equals(getAbsVolumePath(dbVol), path))
                .toList();
    }

    private String getAbsVolumePath(final IndexVolume volume) {
        return pathCreator.toAppPath(volume.getPath()).toString();
    }

    private ValidationResult validateForDupPathInOtherGroups(final IndexVolume indexVolume) {
        final String path = getAbsVolumePath(indexVolume);
        final List<IndexVolume> volumes = securityContext.secureResult(indexVolumeDao::getAll);

        // Need to turn both our path and all db paths into consistent absolute paths, e.g.
        // one may be /tmp/x/../y and the other /tmp/y, both are the same
        final Set<String> dupGroupNames = volumes.stream()
                .filter(dbVol ->
                        !Objects.equals(dbVol.getIndexVolumeGroupId(), indexVolume.getIndexVolumeGroupId())
                        // A shared volume is on every node, so it clashes with the path on any node.
                        && (dbVol.isShared()
                            || indexVolume.isShared()
                            || Objects.equals(dbVol.getNodeName(), indexVolume.getNodeName()))
                        && Objects.equals(getAbsVolumePath(dbVol), path))
                .map(dbVol -> NullSafe.get(
                        indexVolumeGroupService.get(dbVol.getIndexVolumeGroupId()),
                        IndexVolumeGroup::getName))
                .collect(Collectors.toSet());

        if (!dupGroupNames.isEmpty()) {
            return ValidationResult.warning(LogUtil.message("""
                            Path '{}' {} is already a member of index volume group{} [{}].
                            It is NOT recommended to have the same index volume node and path belonging to \
                            multiple volume groups.
                            Click OK to ignore this and set it anyway.""",
                    indexVolume.getPath(),
                    (indexVolume.isShared()
                            ? "on a shared volume"
                            : "on node '" + indexVolume.getNodeName() + "'"),
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

    private ValidationResult validateVolumePath(final IndexVolume volume) {
        final Path path = Paths.get(getAbsVolumePath(volume));
        LOGGER.debug("path: {}", path);

        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (final IOException e) {
                return ValidationResult.error(LogUtil.message(
                        "Error creating index volume path '{}': {}",
                        path,
                        getExceptionMessage(e, path)));
            }
        } else if (!Files.isDirectory(path)) {
            return ValidationResult.error(LogUtil.message(
                    "Error creating index volume path '{}': The path exists but is not a directory.",
                    path));
        }

        // Can't seem to find a good way of checking if we have write perms on the dir so create a file
        // then delete it, after a small delay
        try {
            final Path tempFile = Files.createTempFile(path, TEMP_FILE_PREFIX, null);

            // Wait a few secs before we delete the file in case some file systems prevent deletion
            // immediately after creation
            final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
            executorService.schedule(() -> {
                LOGGER.debug("About to delete file {}", tempFile);
                try {
                    Files.deleteIfExists(tempFile);
                } catch (final IOException e) {
                    LOGGER.error("Unable to delete temporary file {}. You can manually delete this file.",
                            tempFile, e);
                }
            }, 5, TimeUnit.SECONDS);

        } catch (final IOException e) {
            return ValidationResult.error(LogUtil.message(
                    "Error creating test file in directory {}. " +
                    "Does Stroom have the right permissions on this directory? " +
                    "Error message: {} {}",
                    path,
                    e.getClass().getSimpleName(),
                    e.getMessage()));
        }

        return ValidationResult.ok();
    }

    private String getExceptionMessage(final IOException ioException, final Path path) {

        final String msg;
        if (path.toString().equals(ioException.getMessage())) {
            // Some java IO exceptions just have the path as the message, helpful.
            msg = ioException.getClass().getSimpleName();
        } else {
            msg = ioException.getMessage();
        }
        return msg;
    }

    @Override
    public IndexVolume create(final IndexVolume indexVolume) {
        final List<String> names = indexVolumeDao.getAll().stream().map(i -> Strings.isNullOrEmpty(i.getNodeName())
                        ? ""
                        : i.getNodeName())
                .toList();

        final String nodeName;
        if (indexVolume.isShared()) {
            // A shared volume has no node.
            nodeName = null;
        } else if (Strings.isNullOrEmpty(indexVolume.getNodeName())) {
            nodeName = NextNameGenerator.getNextName(names, "New index volume");
        } else {
            nodeName = indexVolume.getNodeName();
        }

        final IndexVolume updated = indexVolume
                .copy()
                .nodeName(nodeName)
                .path(Strings.isNullOrEmpty(indexVolume.getPath())
                        ? null
                        : indexVolume.getPath())
                .indexVolumeGroupId(indexVolume.getIndexVolumeGroupId())
                .stampAudit(securityContext)
                .build();

        final IndexVolume result = securityContext.secureResult(AppPermission.MANAGE_VOLUMES_PERMISSION, () -> {
            requireNoDupPathInThisGroup(updated);
            return indexVolumeDao.create(updated);
        });
        fireChange(EntityAction.CREATE);
        return result;
    }

    @Override
    public IndexVolume read(final int id) {
        return securityContext.secureResult(
                AppPermission.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeDao.fetch(id).orElse(null));
    }

    @Override
    public IndexVolume update(final IndexVolume indexVolume) {
        final IndexVolume loadedIndexVolume = securityContext.secureResult(() ->
                indexVolumeDao.fetch(indexVolume.getId()).orElse(
                        null));

        final boolean shared = indexVolume.isShared();
        if (loadedIndexVolume.isShared() && !shared) {
            throw new IndexException(SHARED_TO_LOCAL_MESSAGE);
        }

        final IndexVolume updated = loadedIndexVolume
                .copy()
                .indexVolumeGroupId(indexVolume.getIndexVolumeGroupId())
                .path((indexVolume.getPath()))
                // A shared volume has no node.
                .nodeName(shared
                        ? null
                        : indexVolume.getNodeName())
                .volumeType(indexVolume.getVolumeType())
                .bytesLimit(indexVolume.getBytesLimit())
                .state(indexVolume.getState())
                .stampAudit(securityContext)
                .build();

        final IndexVolume result = securityContext.secureResult(AppPermission.MANAGE_VOLUMES_PERMISSION, () -> {
            if (shared) {
                requireNoDupPathInThisGroup(updated);
            }
            // Only the change from local to shared merges volumes, which the user has been warned about.
            // Later edits of a shared volume must not remove other volumes.
            if (shared && !loadedIndexVolume.isShared()) {
                // The other nodes' volumes for the same path are the same storage, so the shared volume
                // replaces them and takes their shards.
                final List<Integer> mergedVolumeIds = getLocalVolumesWithSamePath(updated)
                        .stream()
                        .map(IndexVolume::getId)
                        .toList();
                if (!mergedVolumeIds.isEmpty()) {
                    LOGGER.info("Merging index volumes {} into shared index volume {} with path '{}'",
                            mergedVolumeIds, updated.getId(), updated.getPath());
                }
                return indexVolumeDao.mergeIntoSharedVolume(updated, mergedVolumeIds);
            } else {
                return indexVolumeDao.update(updated);
            }
        });
        fireChange(EntityAction.UPDATE);
        return result;
    }

    @Override
    public Boolean delete(final int id) {
        final Boolean result = securityContext.secureResult(AppPermission.MANAGE_VOLUMES_PERMISSION,
                () -> indexVolumeDao.delete(id));
        fireChange(EntityAction.DELETE);
        return result;
    }

    @Override
    public void rescan() {
        internalRescan();
    }

    private synchronized VolumeMap internalRescan() {
        final TaskContext taskContext = taskContextFactory.current();
        // Update the index stats for all indexes belonging to this node so all
        // nodes can pile in and do this at the same time
        final String nodeName = nodeInfo.getThisNodeName();
        taskContext.info(() -> "Updating index volume status for node " + nodeName);
        // The volumes this node uses are its own local volumes and every shared volume.
        // Rescanning writes volume state, so it needs the same permission as other volume management.
        final List<IndexVolume> volumes = securityContext.secureResult(
                        AppPermission.MANAGE_VOLUMES_PERMISSION,
                        () -> indexVolumeDao.getVolumesOnNodeGrouped(nodeName))
                .values()
                .stream()
                .flatMap(List::stream)
                .toList();
        for (final IndexVolume volume : volumes) {
            taskContext.info(() -> "Updating index volume status for '" + volume.getPath() + "' and node " + nodeName);
            updateVolumeState(volume);

            // Record some statistics for the use of this volume.
            recordStats(volume);
        }
        checkSharedVolumes(volumes, nodeName);

        taskContext.info(() -> "Caching index volumes for node " + nodeName);
        // Now cache the index vols for THIS node only, grouped by vol group name
        final VolumeMap newMap = new VolumeMap(
                System.currentTimeMillis(),
                nodeName,
                indexVolumeDao.getVolumesOnNodeGrouped(nodeName));

        final VolumeMap currentList = currentVolumeMap.get();
        if (currentList == null || currentList.createTime < newMap.createTime) {
            currentVolumeMap.set(newMap);
        }
        return newMap;
    }

    // Look for signs that a volume marked as shared is in fact a disk that only this node can see.
    private void checkSharedVolumes(final List<IndexVolume> volumes, final String nodeName) {
        try {
            final Set<Integer> sharedVolumeIds = volumes.stream()
                    .filter(IndexVolume::isShared)
                    .map(IndexVolume::getId)
                    .collect(Collectors.toSet());
            sharedVolumeChecker.retain(sharedVolumeIds);

            if (!sharedVolumeIds.isEmpty()) {
                // Only nodes that are running are expected to keep a marker fresh, so a node that is down
                // does not look like a volume that is not shared.
                final List<String> otherNodes = targetNodeSetFactoryProvider.get().getEnabledActiveTargetNodeSet()
                        .stream()
                        .filter(otherNode -> !Objects.equals(otherNode, nodeName))
                        .toList();
                final long nowMs = System.currentTimeMillis();
                for (final IndexVolume volume : volumes) {
                    if (volume.isShared()) {
                        final Path path = Paths.get(getAbsVolumePath(volume));
                        if (Files.isDirectory(path)) {
                            sharedVolumeChecker.check(volume.getId(), path, nodeName, otherNodes, nowMs);
                        } else {
                            // Nothing can be said about a volume this node cannot reach at all.
                            sharedVolumeChecker.forget(volume.getId());
                        }
                    }
                }
            }
        } catch (final NullClusterStateException | NodeNotFoundException e) {
            // The cluster state is not known yet, e.g. just after startup, so check on the next run.
            LOGGER.debug(() -> "Unable to check shared index volumes: " + LogUtil.exceptionMessage(e), e);
        } catch (final RuntimeException e) {
            LOGGER.error(() -> "Error checking shared index volumes: " + LogUtil.exceptionMessage(e), e);
        }
    }

    private IndexVolume updateVolumeState(final IndexVolume volume) {
        // A relative path is relative to stroom home, which is where the volume's shards are written.
        final Path path = Paths.get(getAbsVolumePath(volume));
        final IndexVolume.Builder builder = volume.copy();

        // Ensure the path exists
        if (Files.isDirectory(path)) {
            LOGGER.debug(() -> LogUtil.message("updateVolumeState() path exists: {}", path));
            setSizes(path, volume, builder);
        } else {
            try {
                Files.createDirectories(path);
                LOGGER.debug(() -> LogUtil.message("updateVolumeState() path created: {}", path));
                setSizes(path, volume, builder);
            } catch (final IOException e) {
                LOGGER.error(() -> LogUtil.message("updateVolumeState() path not created: {}", path));
            }
        }

        final IndexVolume updated = builder.build();
        LOGGER.debug(() -> LogUtil.message("updateVolumeState() exit {}", updated));
        return updated;
    }

    private void setSizes(final Path path,
                          final IndexVolume indexVolume,
                          final IndexVolume.Builder builder) {
        try {
            final FileStore fileStore = Files.getFileStore(path);
            final long osUsableSpace = fileStore.getUsableSpace();
            final long osFreeSpace = fileStore.getUnallocatedSpace();
            final long totalSpace = fileStore.getTotalSpace();
            final long usedSpace = totalSpace - osFreeSpace;
            // Calc free space based on the limit if one is set
            final long freeSpace = indexVolume.getCapacityInfo().getCapacityLimitBytes()
                    .stream()
                    .map(limit -> Math.max(limit - usedSpace, 0))
                    .findAny()
                    .orElse(osUsableSpace);

            builder.updateTimeMs(System.currentTimeMillis());
            builder.bytesTotal(totalSpace);
            builder.bytesFree(freeSpace);
            builder.bytesUsed(usedSpace);
            final IndexVolume updated = builder.build();

            indexVolumeDao.updateVolumeState(
                    updated.getId(),
                    updated.getUpdateTimeMs(),
                    updated.getBytesUsed(),
                    updated.getBytesFree(),
                    updated.getBytesTotal());
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
            final SortedMap<String, String> tags = ImmutableSortedMap.<String, String>naturalOrder()
                    .put("Id", String.valueOf(volume.getId()))
                    .put("Path", volume.getPath())
                    .put("Type", type)
                    .put("Node", nodeInfo.getThisNodeName())
                    .build();

            final InternalStatisticEvent event = InternalStatisticEvent.createValueStat(
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
                    FileUtil.deleteContents(Paths.get(getAbsVolumePath(volume)));
                });
        clearCurrentVolumeMap();
    }

    @Override
    public IndexVolume selectVolume(final String groupName, final String nodeName) {
        List<IndexVolume> indexVolumes;

        // Make sure the default group and vols exist
        indexVolumeGroupService.ensureDefaultVolumes();

        if (nodeInfo.getThisNodeName().equals(nodeName)) {
            // we can check local vol map
            indexVolumes = getCurrentVolumeMap().getVolumes(groupName, nodeName)
                    .orElseGet(() -> indexVolumeDao.getVolumesInGroupOnNode(groupName, nodeName));
        } else {
            // Not this node so have to read the DB
            indexVolumes = indexVolumeDao.getVolumesInGroupOnNode(groupName, nodeName);
        }

        indexVolumes = preferSharedVolumes(removeIneligibleVolumes(indexVolumes));

        if (!indexVolumes.isEmpty()) {
            indexVolumes.sort(Comparator.nullsFirst(Comparator.comparing(IndexVolume::getPath)));

            final HasCapacitySelector volumeSelector = getVolumeSelector(groupName, nodeName);

            final IndexVolume selectedIndexVolume = volumeSelector.select(indexVolumes);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Selected volume {} ({}) out of {} eligible volumes.",
                        selectedIndexVolume.getPath(),
                        selectedIndexVolume.getCapacityInfo(),
                        indexVolumes.size());
            }
            if (selectedIndexVolume == null) {
                throw new IndexException(
                        LogUtil.message("Selector {} returned null for group {}, node {} and volumes [{}]. " +
                                        "Selectors should return something for a non-empty list.",
                                volumeSelector.getClass().getSimpleName(),
                                groupName,
                                nodeName,
                                indexVolumes));
            }
            return selectedIndexVolume;
        } else {
            throw new IndexException(
                    "Unable to find any non-full index volumes for index volume group '"
                    + groupName + "' for node " + nodeName);
        }
    }

    private static List<IndexVolume> removeIneligibleVolumes(final List<IndexVolume> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        } else {
            return list.stream()
                    .filter(indexVolume -> !indexVolume.getCapacityInfo().isFull())
                    .filter(indexVolume -> VolumeUseState.ACTIVE.equals(indexVolume.getState()))
                    .collect(Collectors.toList());
        }
    }

    // A group may hold shared and local volumes. New shards go to its shared volumes when it has any that can
    // take them, so a group that is being moved to shared storage stops adding shards to its local volumes.
    private static List<IndexVolume> preferSharedVolumes(final List<IndexVolume> list) {
        final List<IndexVolume> sharedVolumes = list.stream()
                .filter(IndexVolume::isShared)
                .collect(Collectors.toList());
        return sharedVolumes.isEmpty()
                ? list
                : sharedVolumes;
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
        LOGGER.debug("onChange() - event: {}", event);
        if (event != null) {
            // Simpler to just clear it all out and reload.
            // Index volume changes are pretty rare.
            // This map needs to be cleared for index or group changes.
            clearCurrentVolumeMap();
            final String type = NullSafe.get(event, EntityEvent::getDocRef, DocRef::getType);

            final EntityAction action = event.getAction();
            if (IndexVolumeGroupService.ENTITY_TYPE.equals(type)
                && (action == EntityAction.UPDATE || action == EntityAction.DELETE)) {

                final IndexVolumeGroupEntityEventData eventData = event.getDataObject(
                        IndexVolumeGroupEntityEventData.class);

                // May or may not have an oldGroupName
                Stream.of(eventData.getOldGroupName(), eventData.getGroupName())
                        .filter(NullSafe::isNonBlankString)
                        .forEach(groupName -> {
                            LOGGER.debug("onChange() - Invalidating entries for groupName: {}", groupName);
                            volGroupNodeToVolSelectorCache.invalidateEntries(
                                    (volGroupNode, ignored) ->
                                            Objects.equals(groupName, volGroupNode.groupName));
                        });
            }
        }
    }

    private synchronized void clearCurrentVolumeMap() {
        LOGGER.debug("Clearing currentVolumeMap");
        currentVolumeMap.set(null);
    }

    private void fireChange(final EntityAction action) {
        clearCurrentVolumeMap();
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

    private HasCapacitySelector getVolumeSelector(final String groupName,
                                                  final String nodeName) {
        final VolGroupNode volGroupNode = new VolGroupNode(groupName, nodeName);
        HasCapacitySelector currentSelector = volGroupNodeToVolSelectorCache.get(volGroupNode);

        String requiredSelectorName = HasCapacitySelectorFactory.DEFAULT_SELECTOR_NAME;

        try {
            requiredSelectorName = volumeConfigProvider.get().getVolumeSelector();
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage);
        }

        if (!currentSelector.getName().equals(requiredSelectorName)) {
            synchronized (this) {
                currentSelector = volGroupNodeToVolSelectorCache.get(volGroupNode);
                // Retest under lock
                if (!currentSelector.getName().equals(requiredSelectorName)) {
                    // Config has changed so replace the selector with the configured one
                    volGroupNodeToVolSelectorCache.remove(volGroupNode);
                    currentSelector = volGroupNodeToVolSelectorCache.get(volGroupNode);
                }
            }
        }
        return currentSelector;
    }

    @Override
    public SystemInfoResult getSystemInfo() {
        final VolumeMap volumeMap = securityContext.asProcessingUserResult(this::getCurrentVolumeMap);

        final Map<VolGroupNode, List<Map<String, Object>>> volInfoMap = volumeMap.getGroupNameToVolumesMap()
                .entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Entry::getKey,
                        entry -> entry.getValue()
                                .stream()
                                .map(vol -> {
                                    // Use HashMap so we can cope with null values
                                    final Map<String, Object> map = new HashMap<>();
                                    map.put("path", vol.getPath());
                                    map.put("type", vol.getVolumeType());
                                    map.put("limit", vol.getBytesLimit());
                                    map.put("state", vol.getState());
                                    map.put("free", vol.getBytesFree());
                                    map.put("total", vol.getBytesTotal());
                                    map.put("used", vol.getBytesUsed());
                                    map.put("dbStateUpdateTime", NullSafe.get(
                                            vol,
                                            IndexVolume::getUpdateTimeMs,
                                            DateUtil::createNormalDateTimeString));
                                    return map;
                                })
                                .collect(Collectors.toList())));

        return SystemInfoResult.builder(this)
                .addDetail("volumeSelector", volumeConfigProvider.get().getVolumeSelector())
                .addDetail("volumeListCreateTime", DateUtil.createNormalDateTimeString(volumeMap.getCreateTime()))
                .addDetail("volumeGroups", volInfoMap)
                .addDetail("sharedVolumesNotSeenByNodes", sharedVolumeChecker.getProblems())
                .build();
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private static class VolumeMap {

        private final long createTime;
        private final Map<VolGroupNode, List<IndexVolume>> groupNameToVolumesMap;

        VolumeMap(final long createTime,
                  final String nodeName,
                  final Map<String, List<IndexVolume>> groupNameToVolumesMap) {
            this.createTime = createTime;
            this.groupNameToVolumesMap = groupNameToVolumesMap.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            entry -> new VolGroupNode(entry.getKey(), nodeName),
                            Entry::getValue));
        }

        public Map<VolGroupNode, List<IndexVolume>> getGroupNameToVolumesMap() {
            return groupNameToVolumesMap;
        }

        public Optional<List<IndexVolume>> getVolumes(final String groupName, final String nodeName) {
            return Optional.ofNullable(groupNameToVolumesMap.get(new VolGroupNode(groupName, nodeName)));
        }

        public long getCreateTime() {
            return createTime;
        }

        @Override
        public String toString() {
            return "VolumeMap{" +
                   "createTime=" + Instant.ofEpochMilli(createTime) +
                   "groupNameToVolumesMap(size)=" + groupNameToVolumesMap.size() +
                   '}';
        }
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private static class VolGroupNode {

        private final String groupName;
        private final String nodeName;
        private final int hashcode;

        private VolGroupNode(final String groupName, final String nodeName) {
            this.groupName = Objects.requireNonNull(groupName, "Index volume group name must be specified");
            this.nodeName = Objects.requireNonNull(nodeName, "Index volume node name must be specified");
            this.hashcode = Objects.hash(groupName, nodeName);
        }

        public String getGroupName() {
            return groupName;
        }

        public String getNodeName() {
            return nodeName;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final VolGroupNode that = (VolGroupNode) o;
            return groupName.equals(that.groupName) && nodeName.equals(that.nodeName);
        }

        @Override
        public int hashCode() {
            return hashcode;
        }

        @Override
        public String toString() {
            return "VolGroupNode{" +
                   "groupName='" + groupName + '\'' +
                   ", nodeName='" + nodeName + '\'' +
                   '}';
        }
    }
}
