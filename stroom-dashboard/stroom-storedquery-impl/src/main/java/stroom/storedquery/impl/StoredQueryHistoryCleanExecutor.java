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

import stroom.security.user.api.UserRefLookup;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;

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
    private final UserRefLookup userRefLookup;

    @Inject
    public StoredQueryHistoryCleanExecutor(final StoredQueryDao storedQueryDao,
                                           final StoredQueryConfig storedQueryConfig,
                                           final TaskContextFactory taskContextFactory,
                                           final UserRefLookup userRefLookup) {
        this.storedQueryDao = storedQueryDao;
        this.storedQueryConfig = storedQueryConfig;
        this.taskContextFactory = taskContextFactory;
        this.userRefLookup = userRefLookup;
    }

    public void exec() {
        clean(false);
    }

    private void clean(final boolean favourite) {
        info(() -> "Starting history clean task");

        final int historyItemsRetention = storedQueryConfig.getItemsRetention();
        final int historyDaysRetention = storedQueryConfig.getDaysRetention();

        final long oldestCrtMs = Instant.now()
                .minus(historyDaysRetention, ChronoUnit.DAYS)
                .toEpochMilli();

        final List<String> users = storedQueryDao.getUsers(favourite);
        users.forEach(ownerUuid -> {
            final String userDisplayName = userRefLookup.getByUuid(ownerUuid)
                    .map(UserRef::toInfoString)
                    .orElse("?");
            info(() -> "Cleaning query history for user '" + userDisplayName
                    + "' with ownerUuid '" + ownerUuid + "'");

            final Integer oldestId = storedQueryDao.getOldestId(
                    ownerUuid, favourite, historyItemsRetention);
            storedQueryDao.clean(ownerUuid, favourite, oldestId, oldestCrtMs);
        });

        info(() -> "Finished history clean task");
    }

    private void info(final Supplier<String> message) {
        LOGGER.debug(message);
        taskContextFactory.current().info(message);
    }
}
