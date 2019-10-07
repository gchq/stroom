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
 */

package stroom.dashboard.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import stroom.dashboard.shared.CreateQueryFavouriteAction;
import stroom.dashboard.shared.QueryEntity;
import stroom.logging.DocumentEventLog;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@SuppressWarnings("unused")
@TaskHandlerBean(task = CreateQueryFavouriteAction.class)
@Scope(value = StroomScope.TASK)
class CreateQueryFavouriteActionHandler extends AbstractTaskHandler<CreateQueryFavouriteAction, QueryEntity> {
    private transient static final Logger LOGGER = LoggerFactory.getLogger(CreateQueryFavouriteActionHandler.class);

    private final QueryService queryService;
    private final DocumentEventLog documentEventLog;

    @Inject
    CreateQueryFavouriteActionHandler(final QueryService queryService, final DocumentEventLog documentEventLog) {
        this.queryService = queryService;
        this.documentEventLog = documentEventLog;
    }

    @Override
    public QueryEntity exec(final CreateQueryFavouriteAction action) {
        try {
            QueryEntity before = action.getQueryEntity();
            final QueryEntity newQuery = queryService.create(before.getName());
            newQuery.setDashboardId(before.getDashboardId());
            newQuery.setQueryId(before.getQueryId());
            newQuery.setData(before.getData());
            newQuery.setQuery(before.getQuery());
            newQuery.setFavourite(before.isFavourite());
            final QueryEntity after = queryService.writeDocument(newQuery);

            documentEventLog.update(before, after, null);
            return after;
        } catch (final RuntimeException e) {
            documentEventLog.update(null, action.getQueryEntity(), e);
            throw e;
        }
    }
}
