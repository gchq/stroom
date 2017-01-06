/*
 * Copyright 2016 Crown Copyright
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

import org.springframework.context.annotation.Scope;
import stroom.dashboard.shared.FetchIndexFieldsAction;
import stroom.query.shared.DataSource;
import stroom.query.shared.IndexFields;
import stroom.security.SecurityContext;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = FetchIndexFieldsAction.class)
@Scope(value = StroomScope.TASK)
class FetchIndexFieldsHandler extends AbstractTaskHandler<FetchIndexFieldsAction, IndexFields> {
    private final DataSourceProviderRegistry dataSourceProviderRegistry;
    private final SecurityContext securityContext;

    @Inject
    FetchIndexFieldsHandler(final DataSourceProviderRegistry dataSourceProviderRegistry, final SecurityContext securityContext) {
        this.dataSourceProviderRegistry = dataSourceProviderRegistry;
        this.securityContext = securityContext;
    }

    @Override
    public IndexFields exec(final FetchIndexFieldsAction action) {
        try {
            // Elevate the users permissions for the duration of this task so they can read the index if they have 'use' permission.
            securityContext.elevatePermissions();

            final DataSource dataSource = dataSourceProviderRegistry.getDataSource(action.getDataSourceRef());

            if (dataSource == null) {
                return null;
            }

            return dataSource.getIndexFieldsObject();
        } finally {
            securityContext.restorePermissions();
        }
    }
}
