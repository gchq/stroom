/*
 * Copyright 2018 Crown Copyright
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

package stroom.storedquery.impl;

import stroom.dashboard.shared.DeleteStoredQueryAction;
import stroom.dashboard.shared.StoredQuery;
import stroom.event.logging.api.DocumentEventLog;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;

class DeleteStoredQueryHandler extends AbstractTaskHandler<DeleteStoredQueryAction, VoidResult> {
    private final StoredQueryServiceImpl storedQueryService;
    private final DocumentEventLog entityEventLog;

    @Inject
    DeleteStoredQueryHandler(final StoredQueryServiceImpl storedQueryService,
                             final DocumentEventLog entityEventLog) {
        this.storedQueryService = storedQueryService;
        this.entityEventLog = entityEventLog;
    }

    @Override
    public VoidResult exec(final DeleteStoredQueryAction action) {
        final StoredQuery storedQuery = action.getStoredQuery();
        try {
            storedQueryService.delete(storedQuery.getId());
            entityEventLog.delete(storedQuery, null);
        } catch (final RuntimeException e) {
            entityEventLog.delete(storedQuery, e);
            throw e;
        }

        return VoidResult.INSTANCE;
    }
}
