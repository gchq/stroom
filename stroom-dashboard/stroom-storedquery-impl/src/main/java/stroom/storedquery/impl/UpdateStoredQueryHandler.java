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

import stroom.dashboard.shared.StoredQuery;
import stroom.dashboard.shared.UpdateStoredQueryAction;
import stroom.event.logging.api.DocumentEventLog;
import stroom.security.api.Security;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;

class UpdateStoredQueryHandler extends AbstractTaskHandler<UpdateStoredQueryAction, StoredQuery> {
    private final StoredQueryServiceImpl storedQueryService;
    private final DocumentEventLog entityEventLog;
    private final Security security;

    @Inject
    UpdateStoredQueryHandler(final StoredQueryServiceImpl storedQueryService,
                             final DocumentEventLog entityEventLog,
                             final Security security) {
        this.storedQueryService = storedQueryService;
        this.entityEventLog = entityEventLog;
        this.security = security;
    }

    @Override
    public StoredQuery exec(final UpdateStoredQueryAction action) {
        final StoredQuery storedQuery = action.getStoredQuery();
        return security.secureResult(() -> {
            StoredQuery result;
            StoredQuery before = null;

            try {
                // Get the before version.
                before = storedQueryService.fetch(storedQuery.getId());
                result = storedQueryService.update(storedQuery);
                entityEventLog.update(before, result, null);
            } catch (final RuntimeException e) {
                // Get the before version.
                entityEventLog.update(before, storedQuery, e);
                throw e;
            }

            return result;
        });
    }
}
