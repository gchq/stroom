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

package stroom.meta.impl;

import stroom.task.api.TaskManager;
import stroom.task.shared.TaskId;
import stroom.util.shared.UserRef;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Singleton
class UserQueryRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserQueryRegistry.class);

    private final ConcurrentMap<Key, TaskId> userQueryToTaskMap = new ConcurrentHashMap<>();

    boolean terminateQuery(final UserRef userRef, final String queryId, final TaskManager taskManager) {
        Objects.requireNonNull(userRef);
        Objects.requireNonNull(queryId);
        final Key key = new Key(userRef, queryId);

        return Optional.ofNullable(userQueryToTaskMap.get(key))
                .map(taskId -> {
                    LOGGER.debug("Cancelling query {} for user {}", queryId, userRef);
                    taskManager.terminate(taskId);
                    userQueryToTaskMap.remove(key);
                    return true;
                })
                .orElseGet(() -> {
                    LOGGER.debug("Future not found for queryId {}, userId {}", queryId, userRef);
                    return false;
                });
    }

    /**
     * Marks a query as having been completed
     */
    void deRegisterQuery(final UserRef userRef, final String queryId) {
        Objects.requireNonNull(userRef);
        Objects.requireNonNull(queryId);
        userQueryToTaskMap.remove(new Key(userRef, queryId));
    }

    /**
     * Registers a query as in progress and holds its taskid
     */
    void registerQuery(final UserRef userRef,
                       final String queryId,
                       final TaskId taskId) {

        Objects.requireNonNull(userRef);
        Objects.requireNonNull(queryId);
        Objects.requireNonNull(taskId);

        final TaskId previousTaskId = userQueryToTaskMap.putIfAbsent(new Key(userRef, queryId), taskId);

        if (previousTaskId != null) {
            LOGGER.debug("Query {} already registered for user {}", queryId, userRef);
        } else {
            // In case the query finishes very quickly
            LOGGER.debug("Registering taskId {}, queryId {}, userId {}", taskId, queryId, userRef);
        }
    }

    private static class Key {

        private final UserRef userRef;
        private final String queryId;

        public Key(final UserRef userRef, final String queryId) {
            this.userRef = userRef;
            this.queryId = queryId;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Key key = (Key) o;
            return Objects.equals(userRef, key.userRef) &&
                    Objects.equals(queryId, key.queryId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userRef, queryId);
        }

        @Override
        public String toString() {
            return "Key{" +
                    "userRef='" + userRef + '\'' +
                    ", queryId='" + queryId + '\'' +
                    '}';
        }
    }
}
