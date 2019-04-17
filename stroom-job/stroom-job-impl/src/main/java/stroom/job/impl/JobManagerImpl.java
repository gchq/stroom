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

package stroom.job.impl;

import stroom.job.api.JobManager;
import stroom.job.shared.FindJobCriteria;
import stroom.job.shared.FindJobNodeCriteria;
import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.security.api.SecurityContext;
import stroom.util.AuditUtil;

import javax.inject.Inject;
import java.util.List;

/**
 * The job manager is used to update the database with the status of all job
 * instances running on a cluster and to communicate commands to all job
 * instances across a cluster using values from the database.
 */
public class JobManagerImpl implements JobManager {
    private final JobDao jobDao;
    private final JobNodeDao jobNodeDao;
    private final SecurityContext securityContext;

    @Inject
    public JobManagerImpl(final JobDao jobDao,
                          final JobNodeDao jobNodeDao,
                          final SecurityContext securityContext) {
        this.jobDao = jobDao;
        this.jobNodeDao = jobNodeDao;
        this.securityContext = securityContext;
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
        final List<Job> jobs = jobDao.find(criteria);
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
    public Boolean setNodeEnabled(final String nodeName, final boolean enabled) {
        modifyNode(nodeName, enabled);

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
        final List<Job> jobs = jobDao.find(criteria);

        if (jobs.size() > 0) {
            final Job job = jobs.get(0);
            job.setEnabled(enabled);
            AuditUtil.stamp(securityContext.getUserId(), job);
            jobDao.update(job);
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
        criteria.getJobName().setString(jobName);

        final List<JobNode> jobNodes = jobNodeDao.find(criteria);
        for (final JobNode jobNode : jobNodes) {
            jobNode.setEnabled(enabled);
            AuditUtil.stamp(securityContext.getUserId(), jobNode);
            jobNodeDao.update(jobNode);
        }
    }

    /**
     * Used to set the job command for all job instances associated with a
     * specified node.
     *
     * @param node The node that the job instances are associated with.
     */
    private void modifyNode(final String nodeName, final boolean enabled) {
        final FindJobNodeCriteria criteria = new FindJobNodeCriteria();
        criteria.getNodeName().setString(nodeName);

        final List<JobNode> jobNodes = jobNodeDao.find(criteria);
        for (final JobNode jobNode : jobNodes) {
            jobNode.setEnabled(enabled);
            AuditUtil.stamp(securityContext.getUserId(), jobNode);
            jobNodeDao.update(jobNode);
        }
    }
}
