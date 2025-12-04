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

import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.data.store.impl.fs.shared.FsVolumeType;
import stroom.meta.api.MetaService;
import stroom.meta.shared.SimpleMeta;
import stroom.task.api.TaskContext;
import stroom.util.io.PathCreator;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.DurationTimer.IterationTimer;
import stroom.util.logging.DurationTimer.TimedResult;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * API used by the tasks to interface to the stream store under the bonnet.
 */
class FsOrphanMetaFinder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsOrphanMetaFinder.class);

    private final FsPathHelper fsPathHelper;
    private final MetaService metaService;
    private final DataVolumeService dataVolumeService;
    private final Provider<FsVolumeConfig> fsVolumeConfigProvider;
    private final PathCreator pathCreator;

    @Inject
    public FsOrphanMetaFinder(final FsPathHelper fsPathHelper,
                              final MetaService metaService,
                              final DataVolumeService dataVolumeService,
                              final Provider<FsVolumeConfig> fsVolumeConfigProvider,
                              final PathCreator pathCreator) {
        this.fsPathHelper = fsPathHelper;
        this.metaService = metaService;
        this.dataVolumeService = dataVolumeService;
        this.fsVolumeConfigProvider = fsVolumeConfigProvider;
        this.pathCreator = pathCreator;
    }

    public FsOrphanMetaFinderProgress scan(final Consumer<SimpleMeta> orphanConsumer,
                                           final TaskContext taskContext) {
        final long maxId = metaService.getMaxId();

        final int batchSize = fsVolumeConfigProvider.get().getFindOrphanedMetaBatchSize();
        final FsOrphanMetaFinderProgress progress = new FsOrphanMetaFinderProgress(
                taskContext,
                maxId,
                batchSize);

        long minId = dataVolumeService.getOrphanedMetaTrackerValue();
        LOGGER.info("Starting orphaned meta finder scan with min ID {}, max ID {}, batch size {}",
                minId, maxId, batchSize);
        // Log initial position
        progress.log();

        while (minId != -1
                && !Thread.currentThread().isInterrupted()
                && !taskContext.isTerminated()) {
            minId = scanBatch(minId, maxId, orphanConsumer, progress, taskContext);
        }
        if (Thread.currentThread().isInterrupted() || taskContext.isTerminated()) {
            LOGGER.info("Aborted orphaned meta finder scan at meta ID {}, max ID {}, batch size {}",
                    progress.getId(), maxId, batchSize);
        }

        LOGGER.debug(LogUtil.message("Total file list duration: {}, avg: {}",
                progress.getTotalFileListDuration(),
                progress.getAverageFileListDuration()));

        return progress;
    }

    private long scanBatch(final long minId,
                           final long maxId,
                           final Consumer<SimpleMeta> orphanConsumer,
                           final FsOrphanMetaFinderProgress progress,
                           final TaskContext taskContext) {
        progress.setMinId(minId);
        progress.log();

        final List<SimpleMeta> metaList = LOGGER.logDurationIfDebugEnabled(
                () -> metaService.findBatch(minId, maxId, progress.getBatchSize()),
                list -> LogUtil.message("Found {} meta records, minId: {}, maxId: {}", list.size(), minId, maxId));

        if (LOGGER.isDebugEnabled()) {
            logBatchToDebug(metaList);
        }

        final long result;
        if (metaList.size() > 0) {

            final Map<Long, SimpleMeta> metaIdToMetaMap = metaList.stream()
                    .collect(Collectors.toMap(SimpleMeta::getId, Function.identity()));

            // No PageRequest as we are limiting the results by the meta IDs.
            final FindDataVolumeCriteria volumeCriteria = new FindDataVolumeCriteria(
                    null,
                    null,
                    Selection.selectAll(),
                    new Selection<>(false, metaIdToMetaMap.keySet()));

            // Now get all the volume paths for our batch of meta IDs to save hitting the
            // DB for each one. Can't do all this in one sql with a join a meta is in a logically
            // different db to meta_volume.
            final ResultPage<DataVolume> volumeResultPage = LOGGER.logDurationIfDebugEnabled(
                    () -> dataVolumeService.find(volumeCriteria),
                    resultPage -> LogUtil.message("Found {} dataVolumes", resultPage.size()));

            final List<DataVolume> dataVolumes = volumeResultPage.getValues();
            final Map<String, Map<Path, Set<Path>>> localDirListingMap = new HashMap<>();
            final IterationTimer getRootPathIterationTimer = DurationTimer.newIterationTimer();

            // DataVolume is 1:1 with SimpleMeta
            // First pass to build a picture of the file contents of all the parent dirs as we are
            // expecting/hoping for lots of the metas to share common parent dirs.
            // The hope is that it is cheaper to list the root files in N parent dirs, where N < batch size,
            // then see if that list contains each meta root file, rather than hitting the FS for each meta
            // root file to check existence.
            for (final DataVolume dataVolume : dataVolumes) {
                if (!isTerminated(taskContext) &&
                        FsVolumeType.STANDARD.equals(dataVolume.getVolume().getVolumeType())) {
                    final long metaId = dataVolume.getMetaId();
                    // Should never be null as we used the metaMap keys to find the data vols
                    final SimpleMeta meta = metaIdToMetaMap.get(metaId);
                    final String streamTypeName = meta.getTypeName();

                    // Each meta can have 1-* files, so get only the root ones, e.g. ...042.evt.bgz in
                    // ...042.evt.bgz
                    // ...042.evt.bgz.seg.dat
                    // ...042.evt.bgz.mf.dat

                    final Path volumePath = pathCreator.toAppPath(dataVolume.getVolume().getPath());
                    final TimedResult<Path> rootFileResult = getRootPathIterationTimer.measureIf(
                            LOGGER.isDebugEnabled(),
                            () -> fsPathHelper.getRootPath(
                                    volumePath,
                                    meta,
                                    streamTypeName));
                    final Path rootFile = rootFileResult.getResult();

                    // Never more than 1000 root files per dir, so set capacity to that
                    final Map<Path, Set<Path>> parentPathToRootFilesMap = localDirListingMap.computeIfAbsent(
                            streamTypeName, streamTypeName2 -> new HashMap<>(1_000));

                    final Path parent = rootFile.getParent();
                    if (parent != null) {
                        // If not already found then find the root files under this parent
                        // and cache them. First check the local cache and if not in there try
                        // loading from the progress cache
                        final Set<Path> rootFilePaths = parentPathToRootFilesMap.computeIfAbsent(parent, parent2 -> {
                            // See if we already have it from the last batch, else hit the FS
                            return progress.getCachedRootFiles(streamTypeName, parent2)
                                    .orElseGet(() -> {
                                        progress.recordCacheMiss();
                                        final TimedResult<Set<Path>> rootFilesResult = DurationTimer.measureIf(
                                                LOGGER.isDebugEnabled(),
                                                () -> fsPathHelper.findRootStreamFiles(
                                                        meta.getTypeName(), parent));

                                        if (LOGGER.isDebugEnabled()) {
                                            progress.recordFileListDuration(rootFilesResult.getDuration());
                                        }
                                        return rootFilesResult.getResult();
                                    });
                        });

                        if (!rootFilePaths.contains(rootFile)) {
                            // Can't find the root file for this meta, so record it
                            LOGGER.trace("rootFilePath '{}' not found in parent '{}'", rootFile, parent);
                            progress.foundOrphan();
                            progress.log();
                            orphanConsumer.accept(meta);
                        }
                    } else {
                        // Should never be missing parent
                        LOGGER.error("Root stream file '{}' for meta ID {} has no parent", rootFile, metaId);
                    }
                    progress.setId(metaId);
                }
            }

            LOGGER.debug("getRootPath timings: {}", getRootPathIterationTimer);

            // Finished our batch so update the passed in map to match what we found on this run.
            // Can't hold for every batch as the number of paths involved is vast.
            // At most, we are holding two batches worth in the hope that parent paths from the prev
            // batch are useful in the next.
            progress.updateCachedRootFiles(localDirListingMap);

            // Only log at the end of each batch (or when orphan found) to reduce overhead
            progress.log();

            // See if we have reached the end
            if (progress.getId() < maxId) {
                // Update the tracker so if the job is cancelled or stroom is shutdown we can resume
                // from where we got to on next run
                final long nextMinId = progress.getId() + 1;
                dataVolumeService.updateOrphanedMetaTracker(nextMinId);
                result = nextMinId;
            } else {
                // Next run we start from the beginning
                dataVolumeService.updateOrphanedMetaTracker(0);
                // Indicate completion to the caller
                result = -1;
            }
        } else {
            // No rows means we have reached the end so reset the tracker for the next run.
            dataVolumeService.updateOrphanedMetaTracker(0);
            // Indicate completion
            result = -1;
            LOGGER.info("Completed orphaned meta finder scan at ID {}", progress.getId());
        }
        return result;
    }

    private static void logBatchToDebug(final List<SimpleMeta> metaList) {
        LOGGER.debug("Metas in batch: {}, IDs: {} => {}",
                metaList.size(),
                metaList.stream()
                        .mapToLong(SimpleMeta::getId)
                        .min()
                        .orElse(-1),
                metaList.stream()
                        .mapToLong(SimpleMeta::getId)
                        .max()
                        .orElse(-1));
    }

    private boolean isTerminated(final TaskContext taskContext) {
        return Thread.currentThread().isInterrupted() || taskContext.isTerminated();
    }
}
