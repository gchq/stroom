/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.job.shared.JobNode;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class TestJobNodeService {

    @Test
    void executeJobRequiresManageJobsPermission() {
        // Forcing immediate execution is a job-management action and must be gated behind MANAGE_JOBS, like
        // the other job-node mutators (setEnabled/setSchedule/setTaskLimit route through update()).
        final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        final JobNodeTrackerCache jobNodeTrackerCache = Mockito.mock(JobNodeTrackerCache.class);
        final JobNodeService service = new JobNodeService(
                null, jobNodeTrackerCache, securityContext, null, null);

        service.executeJob(JobNode.builder().id(1).version(1).nodeName("node1").build());

        // The gate is applied with MANAGE_JOBS...
        Mockito.verify(securityContext).secure(eq(AppPermission.MANAGE_JOBS_PERMISSION), any(Runnable.class));
        // ...and since the (mocked) gate does not run the guarded body, no immediate execution is triggered.
        Mockito.verifyNoInteractions(jobNodeTrackerCache);
    }
}
