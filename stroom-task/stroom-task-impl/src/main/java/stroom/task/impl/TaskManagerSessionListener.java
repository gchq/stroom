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
 */

package stroom.task.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.security.api.UserTokenUtil;
import stroom.task.api.TaskIdFactory;
import stroom.task.api.TaskManager;
import stroom.task.shared.FindTaskCriteria;
import stroom.task.shared.TerminateTaskProgressAction;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

@Singleton
class TaskManagerSessionListener implements HttpSessionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskManagerSessionListener.class);

    private final Provider<TaskManager> taskManagerProvider;

    @Inject
    TaskManagerSessionListener(final Provider<TaskManager> taskManagerProvider) {
        this.taskManagerProvider = taskManagerProvider;
    }

    @Override
    public void sessionCreated(final HttpSessionEvent event) {
    }

    @Override
    public void sessionDestroyed(final HttpSessionEvent event) {
        try {
            final TaskManager taskManager = getTaskManager();
            if (taskManager != null) {
                // Manually set the id as we are invoking a UI Action Task
                // directly
                final String sessionId = event.getSession().getId();
                final FindTaskCriteria criteria = new FindTaskCriteria();
                criteria.setSessionId(sessionId);
                final TerminateTaskProgressAction action = new TerminateTaskProgressAction(
                        "Terminate session: " + sessionId, criteria, false);
                action.setUserToken(UserTokenUtil.processingUser());
                action.setId(TaskIdFactory.create());
                taskManager.exec(action);
            }
        } catch (final RuntimeException e) {
            LOGGER.error("sessionDestroyed()", e);
        }
    }

    private TaskManager getTaskManager() {
        if (taskManagerProvider != null) {
            return taskManagerProvider.get();
        }

        return null;
    }
}
