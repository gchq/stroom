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

package stroom.job.impl;


import stroom.job.shared.Job;
import stroom.job.shared.JobNode;
import stroom.node.api.NodeInfo;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.util.AuditUtil;
import stroom.util.exception.DataChangedException;

import jakarta.inject.Inject;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestJobNodeDao extends AbstractCoreIntegrationTest {

    @Inject
    private JobDao jobDao;
    @Inject
    private JobService jobService;
    @Inject
    private JobNodeDao jobNodeDao;
    @Inject
    private JobNodeService jobNodeService;
    @Inject
    private CommonTestControl commonTestControl;
    @Inject
    private NodeInfo nodeInfo;

    @Test
    void test() {
        Job job = AuditUtil.stampNew(() -> "test", Job
                        .builder()
                        .name("Test Job" + System.currentTimeMillis())
                        .enabled(true))
                .build();
        job = jobDao.create(job);

        // Test update
        job = jobDao.update(job);
        job = jobDao.update(job);

        // Test optimistic locking
        final Job finalJob = job;
        Assertions.assertThatThrownBy(() -> {
            jobDao.update(finalJob);
            jobDao.update(finalJob);
        }).isInstanceOf(DataChangedException.class);

        // Test that job service can continually update jobs.
        job = job.copy().enabled(false).build();
        jobService.update(job);
        job = job.copy().enabled(true).build();
        jobService.update(job);

        JobNode jobNode = AuditUtil.stampNew(() -> "test", JobNode
                        .builder()
                        .job(job)
                        .nodeName(nodeInfo.getThisNodeName()))
                .build();
        jobNode = jobNodeDao.create(jobNode);
        jobNode = jobNode.copy().enabled(true).build();

        // Test update
        jobNode = jobNodeDao.update(jobNode);
        jobNode = jobNodeDao.update(jobNode);

        // Test optimistic locking
        final JobNode finalJobNode = jobNode;
        Assertions.assertThatThrownBy(() -> {
            jobNodeDao.update(finalJobNode);
            jobNodeDao.update(finalJobNode);
        }).isInstanceOf(DataChangedException.class);

        // Test that job node service can continually update jobs.
        jobNodeService.update(jobNode.copy().enabled(false).build());
        jobNodeService.update(jobNode.copy().enabled(true).build());
    }
}
