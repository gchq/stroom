/*
 * Copyright 2016 Crown Copyright
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

package stroom.statistics.sql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;
import org.springframework.stereotype.Component;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.task.server.TaskCallbackAdaptor;
import stroom.task.server.TaskManager;
import stroom.util.shared.VoidResult;
import stroom.util.spring.StroomShutdown;
import stroom.util.spring.StroomSimpleCronSchedule;

import javax.annotation.Resource;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class SQLStatisticCacheImpl implements SQLStatisticCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(SQLStatisticCacheImpl.class);

    /**
     * By default we only want to hold a million values in memory before
     * flushing.
     */
    private static final int DEFAULT_MAX_SIZE = 1000000;

    @Resource
    private TaskManager taskManager;

    private volatile SQLStatisticAggregateMap map = new SQLStatisticAggregateMap();
    private final ReentrantLock mapLock = new ReentrantLock();
    // private final ReentrantLock flushLock = new ReentrantLock();
    private final LinkedBlockingDeque<SQLStatisticAggregateMap> flushQueue = new LinkedBlockingDeque<>(1);

    private final int maxSize;

    public SQLStatisticCacheImpl() {
        this(DEFAULT_MAX_SIZE);
    }

    public SQLStatisticCacheImpl(final int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public void add(final SQLStatisticAggregateMap aggregateMap) {
        mapLock.lock();
        try {
            // If we need to flush then switch out the map.
            if (this.map.size() > maxSize) {
                final SQLStatisticAggregateMap flushMap = this.map;
                // Switch out the current map under lock.
                LOGGER.debug("add() - Switch out the current map under lock. {}", flushMap);

                this.map = aggregateMap;

                // Try a non-blocking flush
                doFlush(false, flushMap);
            } else {
                this.map.add(aggregateMap);
            }
        } finally {
            mapLock.unlock();
        }
    }

    @Override
    public void flush() {
        flush(false);
    }

    public void flush(final boolean block) {
        SQLStatisticAggregateMap flushMap = null;

        mapLock.lock();
        try {
            // Switch out the current map under lock.
            flushMap = this.map;
            this.map = new SQLStatisticAggregateMap();
        } finally {
            mapLock.unlock();
        }

        if (flushMap.size() > 0) {
            // Flush the original map.
            doFlush(block, flushMap);
        }
    }

    private void doFlush(final boolean block, final SQLStatisticAggregateMap flushMap) {
        try {
            LOGGER.debug("doFlush() - Locking {}", flushMap);

            flushQueue.putLast(flushMap);
            if (block) {
                try {
                    taskManager.exec(new SQLStatisticFlushTask(flushMap));
                } finally {
                    flushQueue.pollLast();
                }

            } else {
                // Flush the original map.
                taskManager.execAsync(new SQLStatisticFlushTask(flushMap), new TaskCallbackAdaptor<VoidResult>() {
                    @Override
                    public void onSuccess(final VoidResult result) {
                        LOGGER.debug("doFlush() - Unlocking");
                        flushQueue.pollLast();
                    }

                    @Override
                    public void onFailure(final Throwable t) {
                        LOGGER.error("doFlush() - Unlocking", t);
                        flushQueue.pollLast();
                    }
                });
            }

        } catch (final InterruptedException e) {
            LOGGER.error(MarkerFactory.getMarker("FATAL"), "doFlush() - No expecting InterruptedException", e);
        }
    }

    @StroomShutdown
    public void shutdown() {
        // Do a final blocking flush.
        flush(true);
    }

    @StroomSimpleCronSchedule(cron = "0,10,20,30,40,50 * *")
    @JobTrackedSchedule(jobName = "SQL Stats In Memory Flush", description = "SQL Stats In Memory Flush (Cache to DB)")
    public void execute() {
        // Kick off a flush
        flush(false);
    }
}
