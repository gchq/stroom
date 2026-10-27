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

package stroom.job.api;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/// Read-only views of a job's state across the cluster, as the Jobs screen shows it: whether it is enabled, and on
/// each node its schedule, task limit, and what that node says it is doing now. For explaining why work a job does
/// isn't happening; nothing here changes any state.
public interface JobStates {

    /// A job and its node settings, with each node's live state asked of every node in parallel.
    ///
    /// @param jobName The job's name, e.g. `Data Processor`.
    /// @param timeout How long to wait for the nodes; a node that hasn't answered by then is reported with an error.
    /// @return The job, or empty if there is no job of that name.
    /// @throws stroom.util.shared.PermissionException without the Manage Jobs permission.
    Optional<JobState> getJob(String jobName, Duration timeout);

    /// A job.
    ///
    /// @param name    Its name.
    /// @param enabled Whether it is enabled across the cluster.
    /// @param nodes   Its settings and state on each node.
    record JobState(String name, boolean enabled, List<JobNodeState> nodes) {

    }

    /// A job on a node.
    ///
    /// @param node              The node.
    /// @param enabled           Whether the job is enabled on that node.
    /// @param type              How it runs: `Cron`, `Frequency` or `Distributed`.
    /// @param schedule          Its schedule (a cron expression or frequency), or null for a distributed job.
    /// @param taskLimit         The most tasks it runs at once on that node (distributed jobs).
    /// @param currentTaskCount  The tasks it is running now, or null if the node couldn't be asked.
    /// @param lastExecutedMs    When it last ran there, or null if never (or not known).
    /// @param nextScheduledMs   When it is next due there, or null.
    /// @param error             Why the node couldn't be asked, or null.
    record JobNodeState(String node,
                        boolean enabled,
                        String type,
                        String schedule,
                        int taskLimit,
                        Integer currentTaskCount,
                        Long lastExecutedMs,
                        Long nextScheduledMs,
                        String error) {

    }
}
