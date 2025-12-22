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

package stroom.job.api;

import java.util.List;

/**
 * This interface is to be used by all classes that will create tasks for the
 * job system.
 */
public interface DistributedTaskFactory {

    /**
     * Gets a list of tasks if available up to the number requested.
     */
    List<DistributedTask> fetch(String nodeName, int count);

    /**
     * Return tasks back that could not be returned to a worker
     */
    Boolean abandon(String nodeName, List<DistributedTask> tasks);
}
