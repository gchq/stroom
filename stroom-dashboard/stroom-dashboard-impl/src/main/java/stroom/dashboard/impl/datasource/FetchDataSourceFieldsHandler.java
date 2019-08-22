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

package stroom.dashboard.impl.datasource;

import stroom.datasource.shared.DataSourceFields;
import stroom.datasource.shared.FetchDataSourceFieldsAction;
import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;

import javax.inject.Inject;


class FetchDataSourceFieldsHandler extends AbstractTaskHandler<FetchDataSourceFieldsAction, DataSourceFields> {
    private final DataSourceProviderRegistry dataSourceProviderRegistry;
    private final SecurityContext securityContext;

    @Inject
    FetchDataSourceFieldsHandler(final DataSourceProviderRegistry dataSourceProviderRegistry,
                                 final SecurityContext securityContext) {
        this.dataSourceProviderRegistry = dataSourceProviderRegistry;
        this.securityContext = securityContext;
    }

    @Override
    public DataSourceFields exec(final FetchDataSourceFieldsAction action) {
        return securityContext.secureResult(() -> {
            // Elevate the users permissions for the duration of this task so they can read the index if they have 'use' permission.
            return securityContext.useAsReadResult(() -> dataSourceProviderRegistry.getDataSourceProvider(action.getDataSourceRef())
                    .map(provider ->
                            new DataSourceFields(provider.getDataSource(action.getDataSourceRef()).getFields()))
                    .orElse(null));
        });
    }
}
