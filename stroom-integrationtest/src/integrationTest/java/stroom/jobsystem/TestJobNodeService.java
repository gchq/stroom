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

package stroom.jobsystem.server;

import org.junit.Test;
import stroom.jobsystem.shared.Job;
import stroom.jobsystem.shared.JobNode;
import stroom.node.server.NodeCache;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;

import javax.annotation.Resource;

public class TestJobNodeService extends AbstractCoreIntegrationTest {
    @Resource
    private JobService jobService;
    @Resource
    private JobNodeService jobNodeService;
    @Resource
    private CommonTestControl commonTestControl;
    @Resource
    private NodeCache nodeCache;

    @Test
    public void testSaveJob() {
        Job job = new Job();
        job.setName("Test Job" + System.currentTimeMillis());
        job.setEnabled(true);
        job = jobService.save(job);

        jobService.save(job);
        jobService.save(job);

        JobNode jobNode = new JobNode();
        jobNode.setJob(job);
        jobNode.setNode(nodeCache.getDefaultNode());

        jobNode = jobNodeService.save(jobNode);
        jobNode.setEnabled(true);
        jobNodeService.save(jobNode);
    }

    @Test
    public void testStartup() {
        jobNodeService.startup();
    }
}
