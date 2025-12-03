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

import stroom.meta.shared.SimpleMeta;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.date.DateUtil;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

class FsOrphanMetaFinderExecutor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FsOrphanMetaFinderExecutor.class);
    private static final Logger ORPHAN_META_LOGGER = LoggerFactory.getLogger("orphan_meta");
    public static final String TASK_NAME = "Orphan Meta Finder";

    private final Provider<FsOrphanMetaFinder> orphanFileFinderProvider;
    private final TaskContextFactory taskContextFactory;

    @Inject
    FsOrphanMetaFinderExecutor(final Provider<FsOrphanMetaFinder> orphanFileFinderProvider,
                               final TaskContextFactory taskContextFactory) {
        this.orphanFileFinderProvider = orphanFileFinderProvider;
        this.taskContextFactory = taskContextFactory;
    }

    public void scan() {
        final TaskContext taskContext = taskContextFactory.current();
        taskContext.info(() -> "Starting orphan meta finder");
        LOGGER.info("{} - Starting, using logger name: '{}'", TASK_NAME, ORPHAN_META_LOGGER.getName());
        ORPHAN_META_LOGGER.info("Starting {} at {}", TASK_NAME, DateUtil.createNormalDateTimeString());
        final DurationTimer durationTimer = DurationTimer.start();

        final AtomicLong counter = new AtomicLong();
        final Consumer<SimpleMeta> orphanConsumer = meta -> {
            LOGGER.debug(() -> "Orphan meta: " + meta.toString());
            ORPHAN_META_LOGGER.info(String.valueOf(meta.getId()));
            counter.incrementAndGet();
        };

        try {
            // Do the scan
            final FsOrphanMetaFinderProgress progress = orphanFileFinderProvider.get()
                    .scan(orphanConsumer, taskContext);

            if (Thread.currentThread().isInterrupted() || taskContext.isTerminated()) {
                final String state = Thread.currentThread().isInterrupted()
                        ? "interrupted"
                        : "terminated";
                ORPHAN_META_LOGGER.info("--- {} task {} at {} ---",
                        TASK_NAME, state, DateUtil.createNormalDateTimeString());
                LOGGER.info(LogUtil.message("{} - {} in {}, metas checked: {}",
                        TASK_NAME, state, durationTimer, progress.getMetaCount()));
            } else {
                ORPHAN_META_LOGGER.info("Completed {} at {} in {}, found {} orphaned meta records out of {} checked",
                        TASK_NAME,
                        DateUtil.createNormalDateTimeString(),
                        durationTimer,
                        counter.get(),
                        progress.getMetaCount());

                LOGGER.info(LogUtil.message("{} - Finished, in {}. " +
                                "Orphan count: {}, metas checked: {}, cacheMissCount: {}",
                        TASK_NAME,
                        durationTimer,
                        progress.getOrphanCount(),
                        progress.getMetaCount(),
                        progress.getCacheMissCount()));
            }
        } catch (final Exception e) {
            ORPHAN_META_LOGGER.info("--- {} task failed at {} due to: {} ---",
                    TASK_NAME,
                    DateUtil.createNormalDateTimeString(),
                    e.getMessage());
            throw e;
        }
    }
}
