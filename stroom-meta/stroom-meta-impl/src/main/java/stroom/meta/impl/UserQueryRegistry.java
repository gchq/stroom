package stroom.meta.impl;

import stroom.task.api.TaskManager;
import stroom.task.shared.TaskId;
import stroom.util.logging.LogUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Singleton
class UserQueryRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserQueryRegistry.class);

    private final ConcurrentMap<String, ConcurrentMap<String, TaskId>> userToQueryIdsMap = new ConcurrentHashMap<>();

    private final TaskManager taskManager;

    @Inject
    public UserQueryRegistry(final TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    boolean cancelQuery(final String userId, final String queryId) {
        return Optional.ofNullable(userToQueryIdsMap.get(userId))
                .map(queryIdToTaskIdMap -> {

                    final TaskId taskId = queryIdToTaskIdMap.get(queryId);

                    boolean result;
                    if (taskId != null) {
                        LOGGER.debug("Cancelling query {} for user {}", queryId, userId);
                        taskManager.terminate(taskId);
                        result = true;
                    } else {
                        LOGGER.debug("Future not found for queryId {}, userId {}", queryId, userId);
                        result = false;
                    }
                    return result;
                })
                .orElse(false);
    }

    void registerQuery(final String userId,
                       final String queryId,
                       final TaskId taskId) {

        final ConcurrentMap<String, TaskId> queryIdToTaskIdMap = userToQueryIdsMap.computeIfAbsent(
                userId,
                k -> new ConcurrentHashMap<>());

        if (queryIdToTaskIdMap.containsKey(queryId)) {
            throw new RuntimeException(LogUtil.message("Query {} already registered for user {}",
                    queryId, userId));
        } else {
            // In case the query finishes very quickly
            LOGGER.debug("Registering taskId {}, queryId {}, userId {}", taskId, queryId, userId);
            queryIdToTaskIdMap.put(queryId, taskId);
        }
    }
}
