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

import stroom.dashboard.shared.CreateStoredQueryAction;
import stroom.dashboard.shared.StoredQuery;
import stroom.event.logging.api.DocumentEventLog;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;

class CreateStoredQueryHandler extends AbstractTaskHandler<CreateStoredQueryAction, StoredQuery> {
    private final StoredQueryServiceImpl storedQueryService;
    private final DocumentEventLog documentEventLog;

    @Inject
    CreateStoredQueryHandler(final StoredQueryServiceImpl storedQueryService,
                             final DocumentEventLog documentEventLog) {
        this.storedQueryService = storedQueryService;
        this.documentEventLog = documentEventLog;
    }

    @Override
    public StoredQuery exec(final CreateStoredQueryAction action) {
        StoredQuery result;

        try {
            result = storedQueryService.create(action.getStoredQuery());
            documentEventLog.create(result, null);
        } catch (final RuntimeException e) {
            documentEventLog.create(new StoredQuery(), e);
            throw e;
        }

        return result;
    }
}
