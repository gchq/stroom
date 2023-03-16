/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.data.store.impl.fs;

import stroom.data.store.impl.fs.DataVolumeDao.DataVolume;
import stroom.meta.api.MetaService;
import stroom.meta.shared.SimpleMeta;
import stroom.task.api.TaskContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.Selection;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;

/**
 * API used by the tasks to interface to the stream store under the bonnet.
 */
class FsOrphanMetaFinder {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsOrphanMetaFinder.class);

    private static final int BATCH_SIZE = 1000;

    private final FsFileFinder fileFinder;
    private final MetaService metaService;
    private final DataVolumeService dataVolumeService;
    private final Provider<FsVolumeConfig> fsVolumeConfigProvider;

    @Inject
    public FsOrphanMetaFinder(final FsFileFinder fileFinder,
                              final MetaService metaService,
                              final DataVolumeService dataVolumeService,
                              final Provider<FsVolumeConfig> fsVolumeConfigProvider) {
        this.fileFinder = fileFinder;
        this.metaService = metaService;
        this.dataVolumeService = dataVolumeService;
        this.fsVolumeConfigProvider = fsVolumeConfigProvider;
    }

    public void scan(final Consumer<SimpleMeta> orphanConsumer,
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
    }

    private long scanBatch(final long minId,
                           final long maxId,
                           final Consumer<SimpleMeta> orphanConsumer,
                           final FsOrphanMetaFinderProgress progress,
                           final TaskContext taskContext) {
        progress.setMinId(minId);
        progress.log();

        final List<SimpleMeta> metaList = metaService.findBatch(minId, maxId, progress.getBatchSize());
        LOGGER.debug(() -> LogUtil.message("Found {} meta records", metaList.size()));

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
            final ResultPage<DataVolume> volumeResultPage = dataVolumeService.find(volumeCriteria);
            final List<DataVolume> dataVolumes = volumeResultPage.getValues();

            LOGGER.debug(() -> LogUtil.message("Found {} dataVolume records", dataVolumes.size()));

            final Iterator<DataVolume> iterator = dataVolumes.iterator();
            while (iterator.hasNext()
                    && !Thread.currentThread().isInterrupted()
                    && !taskContext.isTerminated()) {
                final DataVolume dataVolume = iterator.next();
                final long metaId = dataVolume.getMetaId();
                LOGGER.trace("metaId: {}", metaId);
                // Should never be null as we used the metaMap keys to find the data vols
                final SimpleMeta meta = metaIdToMetaMap.get(metaId);
                progress.setId(metaId);

                final Optional<Path> optional = fileFinder.findRootStreamFile(meta, dataVolume.getVolumePath());
                if (optional.isEmpty()) {
                    progress.foundOrphan();
                    progress.log();
                    orphanConsumer.accept(meta);
                }
            }
            // Only log at the end of each batch (or when orphan found) to reduce overhead
            progress.log();

            // See if we have reached the end
            if (progress.getId() < maxId) {
                // Update the tracker so if the job is cancelled or stroom is shutdown we can resume
                // from where we got to on next run
                dataVolumeService.updateOrphanedMetaTracker(progress.getId() + 1);
                result = progress.getId();
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
}
