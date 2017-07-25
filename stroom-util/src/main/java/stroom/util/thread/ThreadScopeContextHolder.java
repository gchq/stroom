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

package stroom.util.thread;

/**
 * Class to control access to the thread scope context.
 */
public class ThreadScopeContextHolder {
    private static final ThreadLocal<ThreadScopeContext> THREAD_LOCAL = new ThreadLocal<>();

    /**
     * Get the current context if there is one or throws an illegal state
     * exception. This should be used when a context is expected to already
     * exist.
     */
    public static ThreadScopeContext getContext() throws IllegalStateException {
        final ThreadScopeContext context = THREAD_LOCAL.get();
        if (context == null) {
            throw new IllegalStateException("No thread scope context active");
        }
        return context;
    }

    public static void setContext(final ThreadScopeContext context) {
        THREAD_LOCAL.set(context);
    }

    /**
     * Gets the current context if there is one or returns null if one isn't
     * currently in use.
     */
    static ThreadScopeContext currentContext() {
        return THREAD_LOCAL.get();
    }

    public static boolean contextExists() {
        return currentContext() != null;
    }

    /**
     * Called to create a thread scope context.
     */
    public static void createContext() {
        setContext(new ThreadScopeContext());
    }

    /**
     * Called to destroy the thread scope context.
     */
    public static void destroyContext() throws IllegalStateException {
        final ThreadScopeContext context = getContext();
        context.clear();
        setContext(null);
    }
}
