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

/**
 * Class to control access to the thread scope context.
 */
public class TaskScopeContextHolder {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskScopeContextHolder.class);


    private static final ThreadLocal<TaskScopeContext> THREAD_LOCAL_CONTEXT = new InheritableThreadLocal<TaskScopeContext>();

    private static void setContext(final TaskScopeContext context) {
        THREAD_LOCAL_CONTEXT.set(context);
    }

    /**
     * Get the current context if there is one or throws an illegal state
     * exception. This should be used when a context is expected to already
     * exist.
     */
    public static TaskScopeContext getContext() throws IllegalStateException {
        final TaskScopeContext context = THREAD_LOCAL_CONTEXT.get();
        if (context == null) {
            throw new IllegalStateException("No task scope context active");
        }
        return context;
    }

    /**
     * Gets the current context if there is one or returns null if one isn't
     * currently in use.
     */
    private static TaskScopeContext currentContext() {
        return THREAD_LOCAL_CONTEXT.get();
    }

    public static boolean contextExists() {
        return currentContext() != null;
    }

    /**
     * Called to add a task scope context.
     */
    public static void addContext() {
        final TaskScopeContext context = currentContext();
        if (context != null) {
            addContext(context.getTask());
        } else {
            addContext(null);
        }
    }

    /**
     * Called to add a task scope context.
     */
    public static void addContext(final Task<?> task) {
        final TaskScopeContext context = currentContext();
        final TaskScopeContext taskScopeContext = new TaskScopeContext(context, task);
        setContext(taskScopeContext);
    }

    /**
     * Called to remove the task scope context.
     */
    public static void removeContext() throws IllegalStateException {
        try {
            final TaskScopeContext context = getContext();
            // Switch the context to the parent context.
            final TaskScopeContext parentContext = context.getParent();
            setContext(parentContext);

            // Destroy previous context.
            context.clear();
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
            throw e;
        }
    }
}
