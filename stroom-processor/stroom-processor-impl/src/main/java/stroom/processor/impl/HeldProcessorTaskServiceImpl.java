/*
 * Copyright 2026 Crown Copyright
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

package stroom.processor.impl;

import stroom.processor.api.HeldProcessorTaskService;
import stroom.processor.api.ProcessorTaskState;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/// Ends processor tasks that are held as `Committing`, and tells what state tasks are in.
class HeldProcessorTaskServiceImpl implements HeldProcessorTaskService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HeldProcessorTaskServiceImpl.class);

    private final ProcessorTaskDao processorTaskDao;
    private final SecurityContext securityContext;

    @Inject
    HeldProcessorTaskServiceImpl(final ProcessorTaskDao processorTaskDao,
                                 final SecurityContext securityContext) {
        this.processorTaskDao = processorTaskDao;
        this.securityContext = securityContext;
    }

    @Override
    public boolean completeHeld(final long taskId, final int runCount) {
        checkProcessingUser();
        final boolean completed = processorTaskDao.completeHeld(taskId, runCount);
        LOGGER.debug("completeHeld() - task {}, run {}, completed {}", taskId, runCount, completed);
        return completed;
    }

    @Override
    public boolean putBack(final long taskId, final int runCount, final String reason) {
        checkProcessingUser();
        final boolean putBack = processorTaskDao.putBackHeld(taskId, runCount, reason);
        if (putBack) {
            LOGGER.info("Put back processor task {} from Committing - {}", taskId, reason);
        }
        return putBack;
    }

    @Override
    public Map<Long, ProcessorTaskState> getStates(final Collection<Long> taskIds) {
        checkProcessingUser();
        return processorTaskDao.getStates(taskIds);
    }

    @Override
    public List<ProcessorTaskState> findHeld(final Instant olderThan, final long afterId, final int limit) {
        checkProcessingUser();
        return processorTaskDao.findHeld(olderThan, afterId, limit);
    }

    private void checkProcessingUser() {
        if (!securityContext.isProcessingUser()) {
            throw new PermissionException(securityContext.getUserRef(),
                    "Only the processing user is allowed to change or look up held processor tasks");
        }
    }
}
