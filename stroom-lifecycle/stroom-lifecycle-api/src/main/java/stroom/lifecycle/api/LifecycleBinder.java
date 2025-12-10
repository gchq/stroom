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

package stroom.lifecycle.api;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

public class LifecycleBinder {

    private static final int DEFAULT_PRIORITY = 5;

    private final MapBinder<StartupTask, Runnable> startupTaskMapBinder;
    private final MapBinder<ShutdownTask, Runnable> shutdownTaskMapBinder;

    private LifecycleBinder(final Binder binder) {
        startupTaskMapBinder = MapBinder.newMapBinder(binder, StartupTask.class, Runnable.class);
        shutdownTaskMapBinder = MapBinder.newMapBinder(binder, ShutdownTask.class, Runnable.class);
    }

    public static LifecycleBinder create(final Binder binder) {
        return new LifecycleBinder(binder);
    }

    /**
     * Bind the startup task with the default priority
     */
    public <T extends Runnable> LifecycleBinder bindStartupTaskTo(final Class<T> runnableClass) {
        return bindStartupTaskTo(runnableClass, DEFAULT_PRIORITY);
    }

    /**
     * Bind the startup task with the supplied priority
     *
     * @param priority Higher value will start earlier
     */
    public <T extends Runnable> LifecycleBinder bindStartupTaskTo(final Class<T> runnableClass,
                                                                  final int priority) {
        startupTaskMapBinder.addBinding(new StartupTask(priority))
                .to(runnableClass);
        return this;
    }

    /**
     * Bind the shutdown task with the default priority
     */
    public <T extends Runnable> LifecycleBinder bindShutdownTaskTo(final Class<T> runnableClass) {
        return bindShutdownTaskTo(runnableClass, DEFAULT_PRIORITY);
    }

    /**
     * Bind the shutdown task with the supplied priority
     *
     * @param priority Higher value will shutdown later
     */
    public <T extends Runnable> LifecycleBinder bindShutdownTaskTo(final Class<T> runnableClass,
                                                                   final int priority) {
        shutdownTaskMapBinder.addBinding(new ShutdownTask(priority))
                .to(runnableClass);
        return this;
    }
}
