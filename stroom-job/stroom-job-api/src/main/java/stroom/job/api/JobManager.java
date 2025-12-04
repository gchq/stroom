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

/**
 * The job manager provides control over jobs by setting properties in the
 * database.
 */
public interface JobManager {

    /**
     * Gets the enabled job status for a named job.
     *
     * @param jobName The name of the job to check the enabled state for.
     * @return True if the job is enabled.
     * @throws RuntimeException Could be thrown.
     */
    Boolean isJobEnabled(String jobName);

    /**
     * Gets the enabled job status for a named job on the named node.
     *
     * @param jobName  The name of the job to check the enabled state for.
     * @param nodeName The name of the node.
     * @return True if both the parent job is enabled and the job is enabled on the named node.
     * @throws RuntimeException Could be thrown.
     */
    Boolean isJobEnabled(String jobName, final String nodeName);

    /**
     * Sets the enabled flag for a job with this name on all nodes in the
     * cluster.
     *
     * @param jobName The name of the job to change the enabled state of.
     * @param enabled The value of the enabled flag.
     * @return True if successful.
     * @throws RuntimeException Could be thrown.
     */
    Boolean setJobEnabled(String jobName, boolean enabled);

    /**
     * Sets all jobs on the given node to the supplied enabled state.
     *
     * @param node    The node to set the enabled state on.
     * @param enabled The value of the enabled flag.
     * @return True if successful.
     * @throws RuntimeException Could be thrown.
     */
    Boolean setNodeEnabled(String nodeName, boolean enabled);

    /**
     * Set all jobs to be enabled across the cluster.
     *
     * @return True if successful.
     * @throws RuntimeException Could be thrown.
     */
    Boolean enableAllJobs();

    /**
     * Sets the command flag to start a job with this name on all nodes in the
     * cluster.
     *
     * @param jobName The name of the job to start.
     * @return True if successful.
     * @throws RuntimeException Could be thrown.
     */
    Boolean startJob(String jobName);
}
