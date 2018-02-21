/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.jobsystem;

import stroom.jobsystem.shared.FindJobCriteria;
import stroom.jobsystem.shared.FindJobNodeCriteria;
import stroom.jobsystem.shared.Job;
import stroom.jobsystem.shared.JobManager;
import stroom.jobsystem.shared.JobNode;
import stroom.lifecycle.LifecycleServiceImpl;
import stroom.node.shared.Node;

import javax.inject.Inject;
import java.util.List;

/**
 * The job manager is used to update the database with the status of all job
 * instances running on a cluster and to communicate commands to all job
 * instances across a cluster using values from the database.
 */
public class JobManagerImpl implements JobManager {
    private final JobService jobService;
    private final JobNodeService jobNodeService;
    private final LifecycleServiceImpl lifecycleService;

    @Inject
    public JobManagerImpl(final JobService jobService,
                          final JobNodeService jobNodeService,
                          final LifecycleServiceImpl lifecycleService) {
        this.jobService = jobService;
        this.jobNodeService = jobNodeService;
        this.lifecycleService = lifecycleService;
    }

    /**
     * Gets the enabled job status for a named job.
     *
     * @param jobName The name of the job to check the enabled state for.
     * @return True if the job is enabled.
     */
    @Override
    public Boolean isJobEnabled(final String jobName) {
        final FindJobCriteria criteria = new FindJobCriteria();
        criteria.getName().setString(jobName);
        final List<Job> jobs = jobService.find(criteria);
        if (jobs.size() > 0) {
            final Job job = jobs.get(0);
            return job.isEnabled();
        }
        // No such job !
        return false;
    }

    /**
     * Sets the enabled flag for a job with this name on all nodes in the
     * cluster.
     *
     * @param jobName The name of the job to change the enabled state of.
     * @param enabled The value of the enabled flag.
     * @return True if successful.
     */
    @Override
    public Boolean setJobEnabled(final String jobName, final boolean enabled) {
        modifyCluster(jobName, enabled);
        return Boolean.TRUE;
    }

    /**
     * Sets all jobs on the given node to the supplied enabled state.
     *
     * @param node    The node to set the enabled state on.
     * @param enabled The value of the enabled flag.
     * @return True if successful.
     */
    @Override
    public Boolean setNodeEnabled(final Node node, final boolean enabled) {
        modifyNode(node, enabled);

        return Boolean.TRUE;
    }

    /**
     * Set all jobs to be enabled across the cluster.
     *
     * @return True if successful.
     */
    @Override
    public Boolean enableAllJobs() {
        modifyCluster(null, true);
        return Boolean.TRUE;
    }

    /**
     * Sets the command flag to start a job with this name on all nodes in the
     * cluster.
     *
     * @param jobName The name of the job to start.
     * @return True if successful.
     */
    @Override
    public Boolean startJob(final String jobName) {
        modifyJob(jobName, true);
        return Boolean.TRUE;
    }

    private void modifyCluster(final String jobName, final boolean enabled) {
        final FindJobCriteria criteria = new FindJobCriteria();
        criteria.getName().setString(jobName);
        final List<Job> jobs = jobService.find(criteria);

        if (jobs.size() > 0) {
            final Job job = jobs.get(0);
            job.setEnabled(enabled);
            jobService.save(job);
        }
    }

    /**
     * Used to set the job command for all job instances associated with the
     * specified job name.
     *
     * @param jobName The name of the job that the job instances are associated
     *                with.
     */
    private void modifyJob(final String jobName, final boolean enabled) {
        final FindJobNodeCriteria criteria = new FindJobNodeCriteria();
        criteria.setJobName(jobName);

        final List<JobNode> jobNodes = jobNodeService.find(criteria);
        for (final JobNode jobNode : jobNodes) {
            jobNode.setEnabled(enabled);
            jobNodeService.save(jobNode);
        }
    }

    /**
     * Used to set the job command for all job instances associated with a
     * specified node.
     *
     * @param node The node that the job instances are associated with.
     */
    private void modifyNode(final Node node, final boolean enabled) {
        final FindJobNodeCriteria criteria = new FindJobNodeCriteria();
        criteria.getNodeIdSet().add(node);

        final List<JobNode> jobNodes = jobNodeService.find(criteria);
        for (final JobNode jobNode : jobNodes) {
            jobNode.setEnabled(enabled);
            jobNodeService.save(jobNode);
        }
    }

    @Override
    public Boolean isClusterRunning() throws RuntimeException {
        return lifecycleService.isRunning();
    }
}
