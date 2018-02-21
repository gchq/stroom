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

package stroom.dashboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.jobsystem.JobTrackedSchedule;
import stroom.properties.StroomPropertyService;
import stroom.util.shared.Task;
import stroom.util.spring.StroomSimpleCronSchedule;
import stroom.util.task.TaskMonitor;

import javax.inject.Inject;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Task to clean out old query history items.
 */
public class QueryHistoryCleanExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(QueryHistoryCleanExecutor.class);

    private final TaskMonitor taskMonitor;
    private final QueryService queryService;
    private final StroomPropertyService propertyService;

    @Inject
    public QueryHistoryCleanExecutor(final TaskMonitor taskMonitor, final QueryService queryService, final StroomPropertyService propertyService) {
        this.taskMonitor = taskMonitor;
        this.queryService = queryService;
        this.propertyService = propertyService;
    }

    @StroomSimpleCronSchedule(cron = "0 0 *")
    @JobTrackedSchedule(jobName = "Query History Clean", advanced = false, description = "Job to clean up old query history items")
    public void exec(final Task<?> task) {
        clean(task, false);
    }

    public void clean(final Task<?> task, final boolean favourite) {
        info("Starting history clean task");

        final int historyItemsRetention = propertyService.getIntProperty("stroom.query.history.itemsRetention", 100);
        final int historyDaysRetention = propertyService.getIntProperty("stroom.query.history.daysRetention", 365);

        final long oldestCrtMs = ZonedDateTime.now().minusDays(historyDaysRetention).toInstant().toEpochMilli();

        final List<String> users = queryService.getUsers(favourite);
        users.forEach(user -> {
            info("Cleaning query history for '" + user + "'");

            final Integer oldestId = queryService.getOldestId(user, favourite, historyItemsRetention);
            queryService.clean(user, favourite, oldestId, oldestCrtMs);
        });

        info("Finished history clean task");
    }

    private void info(final String message) {
        LOGGER.debug(message);
        taskMonitor.info(message);
    }
}
