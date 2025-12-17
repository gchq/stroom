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

package stroom.task.impl;

import stroom.security.api.SecurityContext;
import stroom.task.shared.FindTaskCriteria;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class TaskManagerSessionListener implements HttpSessionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskManagerSessionListener.class);

    private final Provider<TaskManagerImpl> taskManagerProvider;
    private final SecurityContext securityContext;

    @Inject
    TaskManagerSessionListener(final Provider<TaskManagerImpl> taskManagerProvider,
                               final SecurityContext securityContext) {
        this.taskManagerProvider = taskManagerProvider;
        this.securityContext = securityContext;
    }

    @Override
    public void sessionCreated(final HttpSessionEvent event) {
    }

    @Override
    public void sessionDestroyed(final HttpSessionEvent event) {
        try {
            final TaskManagerImpl taskManager = getTaskManager();
            if (taskManager != null) {
                securityContext.asProcessingUser(() -> {
                    // Manually set the id as we are invoking a UI Action Task
                    // directly
                    final String sessionId = event.getSession().getId();
                    final FindTaskCriteria criteria = new FindTaskCriteria();
                    criteria.setSessionId(sessionId);
                    taskManager.terminate(criteria);
                });
            }
        } catch (final RuntimeException e) {
            LOGGER.error("sessionDestroyed()", e);
        }
    }

    private TaskManagerImpl getTaskManager() {
        if (taskManagerProvider != null) {
            return taskManagerProvider.get();
        }

        return null;
    }
}
