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

import event.logging.BaseAdvancedQueryOperator.And;
import event.logging.Query;
import event.logging.Query.Advanced;
import stroom.dashboard.shared.FindStoredQueryAction;
import stroom.dashboard.shared.FindStoredQueryCriteria;
import stroom.dashboard.shared.StoredQuery;
import stroom.event.logging.api.DocumentEventLog;
import stroom.security.Security;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.ResultList;

import javax.inject.Inject;

class FindStoredQueryHandler extends AbstractTaskHandler<FindStoredQueryAction, ResultList<StoredQuery>> {
    private final StoredQueryDao storedQueryDao;
    private final DocumentEventLog entityEventLog;
    private final Security security;

    @Inject
    FindStoredQueryHandler(final StoredQueryDao storedQueryDao,
                           final DocumentEventLog entityEventLog,
                           final Security security) {
        this.storedQueryDao = storedQueryDao;
        this.entityEventLog = entityEventLog;
        this.security = security;
    }

    @Override
    public ResultList<StoredQuery> exec(final FindStoredQueryAction action) {
        final FindStoredQueryCriteria criteria = action.getCriteria();
        return security.secureResult(() -> {
            BaseResultList<StoredQuery> result;

            final Query query = new Query();
            final Advanced advanced = new Advanced();
            query.setAdvanced(advanced);
            final And and = new And();
            advanced.getAdvancedQueryItems().add(and);

            try {
                result = storedQueryDao.find(criteria);
                entityEventLog.search(criteria, query, result, null);
            } catch (final RuntimeException e) {
                entityEventLog.search(criteria, query, null, e);
                throw e;
            }

            return result;
        });
    }
}
