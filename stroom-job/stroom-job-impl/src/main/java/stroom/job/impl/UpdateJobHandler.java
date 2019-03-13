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
import stroom.job.shared.Job;
import stroom.job.shared.UpdateJobAction;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;
import java.util.Optional;

class UpdateJobHandler extends AbstractTaskHandler<UpdateJobAction, Job> {
    private final JobService jobService;
    private final DocumentEventLog documentEventLog;

    @Inject
    UpdateJobHandler(final JobService jobService,
                     final DocumentEventLog documentEventLog) {
        this.jobService = jobService;
        this.documentEventLog = documentEventLog;
    }

    @Override
    public Job exec(final UpdateJobAction action) {
        Job result = null;

        try {
            final Optional<Job> before = jobService.fetch(action.getJob().getId());
            result = jobService.update(action.getJob());
            documentEventLog.update(before.orElse(null), result, null);
        } catch (final RuntimeException e) {
            documentEventLog.update(action.getJob(), result, e);
        }

        return result;
    }
}
