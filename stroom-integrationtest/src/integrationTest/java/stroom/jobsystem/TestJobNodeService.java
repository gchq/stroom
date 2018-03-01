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

import org.junit.Test;
import stroom.jobsystem.shared.Job;
import stroom.jobsystem.shared.JobNode;
import stroom.node.NodeCache;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;

import javax.inject.Inject;

public class TestJobNodeService extends AbstractCoreIntegrationTest {
    @Inject
    private JobService jobService;
    @Inject
    private JobNodeService jobNodeService;
    @Inject
    private CommonTestControl commonTestControl;
    @Inject
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
