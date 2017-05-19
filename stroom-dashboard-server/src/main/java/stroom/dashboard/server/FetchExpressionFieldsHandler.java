/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.server;

import org.springframework.context.annotation.Scope;
import stroom.dashboard.shared.DataSourceFields;
import stroom.dashboard.shared.FetchDataSourceFieldsAction;
import stroom.datasource.DataSourceProviderRegistry;
import stroom.security.SecurityContext;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = FetchDataSourceFieldsAction.class)
@Scope(value = StroomScope.TASK)
class FetchExpressionFieldsHandler extends AbstractTaskHandler<FetchDataSourceFieldsAction, DataSourceFields> {
    private final DataSourceProviderRegistry dataSourceProviderRegistry;
    private final SecurityContext securityContext;

    @Inject
    FetchExpressionFieldsHandler(final DataSourceProviderRegistry dataSourceProviderRegistry, final SecurityContext securityContext) {
        this.dataSourceProviderRegistry = dataSourceProviderRegistry;
        this.securityContext = securityContext;
    }

    @Override
    public DataSourceFields exec(final FetchDataSourceFieldsAction action) {
        try {
            // Elevate the users permissions for the duration of this task so they can read the index if they have 'use' permission.
            securityContext.elevatePermissions();

            return dataSourceProviderRegistry.getDataSourceProvider(action.getDataSourceRef())
                    .map(provider ->
                            new DataSourceFields(provider.getDataSource(action.getDataSourceRef()).getFields()))
                    .orElse(null);
        } finally {
            securityContext.restorePermissions();
        }
    }
}
