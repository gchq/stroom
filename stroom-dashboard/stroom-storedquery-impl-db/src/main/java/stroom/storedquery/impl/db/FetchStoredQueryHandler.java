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

package stroom.storedquery.impl.db;

import stroom.dashboard.shared.FetchStoredQueryAction;
import stroom.dashboard.shared.StoredQuery;
import stroom.event.logging.api.DocumentEventLog;
import stroom.security.api.Security;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;

class FetchStoredQueryHandler extends AbstractTaskHandler<FetchStoredQueryAction, StoredQuery> {
    private final StoredQueryDao storedQueryDao;
    private final DocumentEventLog entityEventLog;
    private final Security security;

    @Inject
    FetchStoredQueryHandler(final StoredQueryDao storedQueryDao,
                            final DocumentEventLog entityEventLog,
                            final Security security) {
        this.storedQueryDao = storedQueryDao;
        this.entityEventLog = entityEventLog;
        this.security = security;
    }

    @Override
    public StoredQuery exec(final FetchStoredQueryAction action) {
        final StoredQuery storedQuery = action.getStoredQuery();
        return security.secureResult(() -> {
            StoredQuery result;
            try {
                result = storedQueryDao.fetch(storedQuery.getId()).orElse(null);
                entityEventLog.view(result, null);
            } catch (final RuntimeException e) {
                entityEventLog.view(storedQuery, e);
                throw e;
            }

            return result;
        });
    }
}
