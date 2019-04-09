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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.task.api.TaskContext;

import javax.inject.Inject;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Task to clean out old query history items.
 */
public class StoredQueryHistoryCleanExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoredQueryHistoryCleanExecutor.class);

    private final TaskContext taskContext;
    private final StoredQueryDao storedQueryDao;
    private final StoredQueryHistoryConfig queryHistoryConfig;

    @Inject
    public StoredQueryHistoryCleanExecutor(final TaskContext taskContext, final StoredQueryDao storedQueryDao, final StoredQueryHistoryConfig queryHistoryConfig) {
        this.taskContext = taskContext;
        this.storedQueryDao = storedQueryDao;
        this.queryHistoryConfig = queryHistoryConfig;
    }

    public void exec() {
        clean(false);
    }

    public void clean(final boolean favourite) {
        info("Starting history clean task");

        final int historyItemsRetention = queryHistoryConfig.getItemsRetention();
        final int historyDaysRetention = queryHistoryConfig.getDaysRetention();

        final long oldestCrtMs = ZonedDateTime.now().minusDays(historyDaysRetention).toInstant().toEpochMilli();

        final List<String> users = storedQueryDao.getUsers(favourite);
        users.forEach(user -> {
            info("Cleaning query history for '" + user + "'");

            final Integer oldestId = storedQueryDao.getOldestId(user, favourite, historyItemsRetention);
            storedQueryDao.clean(user, favourite, oldestId, oldestCrtMs);
        });

        info("Finished history clean task");
    }

    private void info(final String message) {
        LOGGER.debug(message);
        taskContext.info(message);
    }
}
