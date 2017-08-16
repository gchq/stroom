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

package stroom.jobsystem.shared;

import stroom.node.shared.Node;

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
    Boolean isJobEnabled(String jobName) throws RuntimeException;

    /**
     * Sets the enabled flag for a job with this name on all nodes in the
     * cluster.
     *
     * @param jobName The name of the job to change the enabled state of.
     * @param enabled The value of the enabled flag.
     * @return True if successful.
     * @throws RuntimeException Could be thrown.
     */
    Boolean setJobEnabled(String jobName, boolean enabled) throws RuntimeException;

    /**
     * Sets all jobs on the given node to the supplied enabled state.
     *
     * @param node    The node to set the enabled state on.
     * @param enabled The value of the enabled flag.
     * @return True if successful.
     * @throws RuntimeException Could be thrown.
     */
    Boolean setNodeEnabled(Node node, boolean enabled) throws RuntimeException;

    /**
     * AKA start on load. The cluster maybe enabled but it will do nothing
     * unless it is started.
     *
     * @return True if the cluster is enabled.
     * @throws RuntimeException Could be thrown.
     */
    Boolean isClusterRunning() throws RuntimeException;

    /**
     * Set all jobs to be enabled across the cluster.
     *
     * @return True if successful.
     * @throws RuntimeException Could be thrown.
     */
    Boolean enableAllJobs() throws RuntimeException;

    /**
     * Sets the command flag to start a job with this name on all nodes in the
     * cluster.
     *
     * @param jobName The name of the job to start.
     * @return True if successful.
     * @throws RuntimeException Could be thrown.
     */
    Boolean startJob(String jobName) throws RuntimeException;
}
