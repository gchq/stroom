/*
 * Copyright 2026 Crown Copyright
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

package stroom.processor.api;

import stroom.processor.shared.TaskStatus;

/// The status of a processor task and the run it is on.
///
/// @param taskId   The id of the task.
/// @param status   Its status. A task that no longer exists is `Deleted`.
/// @param runCount The number of its latest run, which is zero if it has never been run or no longer exists.
public record ProcessorTaskState(long taskId, TaskStatus status, int runCount) {

}
