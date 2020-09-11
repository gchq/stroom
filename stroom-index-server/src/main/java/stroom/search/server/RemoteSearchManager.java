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

package stroom.search.server;

import org.springframework.stereotype.Component;
import stroom.query.api.v2.QueryKey;
import stroom.search.resultsender.NodeResult;
import stroom.security.SecurityContext;
import stroom.security.SecurityHelper;
import stroom.security.shared.UserIdentity;
import stroom.task.server.TaskManager;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.TaskId;

import javax.inject.Inject;

@Component(RemoteSearchManager.BEAN_NAME)
public class RemoteSearchManager {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RemoteSearchManager.class);

    public static final String BEAN_NAME = "remoteSearchManager";
    public static final String START_SEARCH = "startSearch";
    public static final String POLL = "poll";
    public static final String DESTROY = "destroy";

    private final RemoteSearchResults remoteSearchResults;
    private final SecurityContext securityContext;
    private final TaskManager taskManager;

    @Inject
    RemoteSearchManager(final RemoteSearchResults remoteSearchResults,
                        final SecurityContext securityContext,
                        final TaskManager taskManager) {
        this.remoteSearchResults = remoteSearchResults;
        this.securityContext = securityContext;
        this.taskManager = taskManager;
    }

    public Boolean startSearch(final UserIdentity userIdentity, final TaskId sourceTaskId, final ClusterSearchTask clusterSearchTask) {
        // Give the cluster search task an id that is a child of the source task.
        clusterSearchTask.assignId(sourceTaskId);

        LOGGER.debug(() -> "startSearch " + clusterSearchTask);
        try (final SecurityHelper securityHelper = SecurityHelper.asUser(securityContext, userIdentity)) {
            remoteSearchResults.put(clusterSearchTask.getKey(), new RemoteSearchResultFactory());
            taskManager.execAsync(clusterSearchTask);
            return true;
        }
    }

    public NodeResult poll(final UserIdentity userIdentity, final QueryKey key) {
        LOGGER.debug(() -> "poll " + key);
        try (final SecurityHelper securityHelper = SecurityHelper.asUser(securityContext, userIdentity)) {
            final RemoteSearchResultFactory factory = remoteSearchResults.get(key);
            if (factory != null) {
                return factory.create();
            }
            return null;
        }
    }

    public Boolean destroy(final UserIdentity userIdentity, final QueryKey key) {
        LOGGER.debug(() -> "destroy " + key);
        try (final SecurityHelper securityHelper = SecurityHelper.asUser(securityContext, userIdentity)) {
            remoteSearchResults.invalidate(key);
            return true;
        }
    }
}
