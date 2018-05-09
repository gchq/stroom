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

package stroom.util.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.shared.Task;
import stroom.util.thread.Link;

/**
 * Class to control access to the thread scope context.
 */
public class TaskScopeContextHolder {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskScopeContextHolder.class);

    private static final ThreadLocal<Link<TaskScopeContext>> THREAD_LOCAL_LINK = new InheritableThreadLocal<>();

    /**
     * Get the current context if there is one or throws an illegal state
     * exception. This should be used when a context is expected to already
     * exist.
     */
    public static TaskScopeContext getContext() throws IllegalStateException {
        final Link<TaskScopeContext> link = currentLink();
        if (link == null) {
            throw new IllegalStateException("No task scope context active");
        }
        return link.getObject();
    }

    /**
     * Gets the current context if there is one or returns null if one isn't
     * currently in use.
     */
    private static Link<TaskScopeContext> currentLink() {
        return THREAD_LOCAL_LINK.get();
    }

    public static boolean contextExists() {
        return currentLink() != null;
    }

    /**
     * Called to add a task scope context.
     */
    public static void addContext() {
        final Link<TaskScopeContext> link = currentLink();
        if (link != null) {
            addContext(link.getObject().getTask(), link);
        } else {
            addContext(null, null);
        }
    }

    /**
     * Called to add a task scope context.
     */
    public static void addContext(final Task<?> task) {
        addContext(task, currentLink());
    }

    private static void addContext(final Task<?> task, final Link<TaskScopeContext> parentLink) {
        THREAD_LOCAL_LINK.set(new Link<>(new TaskScopeContext(task), parentLink));
    }

    /**
     * Called to remove the task scope context.
     */
    public static void removeContext() throws IllegalStateException {
        try {
            final Link<TaskScopeContext> link = currentLink();
            if (link == null) {
                throw new IllegalStateException("No task scope context active");
            }

            // Switch the context to the parent context.
            THREAD_LOCAL_LINK.set(link.getParent());

            // Destroy previous context.
            link.getObject().clear();
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }
    }
}
