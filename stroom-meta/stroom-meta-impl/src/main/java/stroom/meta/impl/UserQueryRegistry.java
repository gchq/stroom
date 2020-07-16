package stroom.meta.impl;

import stroom.task.api.TaskManager;
import stroom.task.shared.TaskId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Singleton
class UserQueryRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserQueryRegistry.class);

    private final ConcurrentMap<Key, TaskId> userQueryToTaskMap = new ConcurrentHashMap<>();

    boolean terminateQuery(final String userId, final String queryId, final TaskManager taskManager) {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(queryId);
        final Key key = new Key(userId, queryId);

        return Optional.ofNullable(userQueryToTaskMap.get(key))
                .map(taskId -> {
                    LOGGER.debug("Cancelling query {} for user {}", queryId, userId);
                    taskManager.terminate(taskId);
                    userQueryToTaskMap.remove(key);
                    return true;
                })
                .orElseGet(() -> {
                    LOGGER.debug("Future not found for queryId {}, userId {}", queryId, userId);
                    return false;
                });
    }

    /**
     * Marks a query as having been completed
     */
    void deRegisterQuery(final String userId, final String queryId) {
        Objects.requireNonNull(userId);
        Objects.requireNonNull(queryId);
        userQueryToTaskMap.remove(new Key(userId, queryId));
    }

    /**
     * Registers a query as in progress and holds its taskid
     */
    void registerQuery(final String userId,
                       final String queryId,
                       final TaskId taskId) {

        Objects.requireNonNull(userId);
        Objects.requireNonNull(queryId);
        Objects.requireNonNull(taskId);

        TaskId previousTaskId = userQueryToTaskMap.putIfAbsent(new Key(userId, queryId), taskId);

        if (previousTaskId != null) {
            LOGGER.debug("Query {} already registered for user {}", queryId, userId);
        } else {
            // In case the query finishes very quickly
            LOGGER.debug("Registering taskId {}, queryId {}, userId {}", taskId, queryId, userId);
        }
    }

    private static class Key {
        private final String userId;
        private final String queryId;

        public Key(final String userId, final String queryId) {
            this.userId = userId;
            this.queryId = queryId;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Key key = (Key) o;
            return Objects.equals(userId, key.userId) &&
                    Objects.equals(queryId, key.queryId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, queryId);
        }

        @Override
        public String toString() {
            return "Key{" +
                    "userId='" + userId + '\'' +
                    ", queryId='" + queryId + '\'' +
                    '}';
        }
    }
}
