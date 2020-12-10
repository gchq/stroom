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

package stroom.storedquery.impl;

import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Supplier;

/**
 * Task to clean out old query history items.
 */
public class StoredQueryHistoryCleanExecutor {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StoredQueryHistoryCleanExecutor.class);

    private final StoredQueryDao storedQueryDao;
    private final StoredQueryConfig storedQueryConfig;
    private final TaskContextFactory taskContextFactory;

    @Inject
    public StoredQueryHistoryCleanExecutor(final StoredQueryDao storedQueryDao,
                                           final StoredQueryConfig storedQueryConfig,
                                           final TaskContextFactory taskContextFactory) {
        this.storedQueryDao = storedQueryDao;
        this.storedQueryConfig = storedQueryConfig;
        this.taskContextFactory = taskContextFactory;
    }

    public void exec() {
        taskContextFactory.context("Clean Stored History", taskContext -> clean(taskContext, false)).run();
    }

    private void clean(final TaskContext taskContext, final boolean favourite) {
        info(taskContext, () -> "Starting history clean task");

        final int historyItemsRetention = storedQueryConfig.getItemsRetention();
        final int historyDaysRetention = storedQueryConfig.getDaysRetention();

        final long oldestCrtMs = Instant.now().minus(historyDaysRetention, ChronoUnit.DAYS).toEpochMilli();

        final List<String> users = storedQueryDao.getUsers(favourite);
        users.forEach(user -> {
            info(taskContext, () -> "Cleaning query history for '" + user + "'");

            final Integer oldestId = storedQueryDao.getOldestId(user, favourite, historyItemsRetention);
            storedQueryDao.clean(user, favourite, oldestId, oldestCrtMs);
        });

        info(taskContext, () -> "Finished history clean task");
    }

    private void info(final TaskContext taskContext, final Supplier<String> message) {
        LOGGER.debug(message);
        taskContext.info(message);
    }
}
