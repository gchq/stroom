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

package stroom.util;

import io.dropwizard.servlets.tasks.Task;

import java.util.List;

/**
 * Implement this if your class provides Dropwizard Admin tasks.
 */
public interface HasAdminTasks {

    /**
     * Called ONCE on boot after the guice bindings have been done.
     *
     * @return A list of admin tasks that can be executed on the admin port.
     * e.g.
     * <p>
     * <pre>{@code localhost:8091/proxyAdmin/tasks/clear-all-caches}</pre>
     * </p>
     */
    List<Task> getTasks();
}
