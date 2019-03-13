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

import stroom.event.logging.api.DocumentEventLog;
import stroom.job.shared.JobNode;
import stroom.job.shared.UpdateJobNodeAction;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;
import java.util.Optional;

class UpdateJobNodeHandler extends AbstractTaskHandler<UpdateJobNodeAction, JobNode> {
    private final JobNodeService jobNodeService;
    private final DocumentEventLog documentEventLog;

    @Inject
    UpdateJobNodeHandler(final JobNodeService jobNodeService,
                         final DocumentEventLog documentEventLog) {
        this.jobNodeService = jobNodeService;
        this.documentEventLog = documentEventLog;
    }

    @Override
    public JobNode exec(final UpdateJobNodeAction action) {
        JobNode result = null;
        try {
            final Optional<JobNode> before = jobNodeService.fetch(action.getJobNode().getId());
            result = jobNodeService.update(action.getJobNode());
            documentEventLog.update(before.orElse(null), result, null);
        } catch (final RuntimeException e) {
            documentEventLog.update(action.getJobNode(), result, e);
        }
        return result;
    }
}