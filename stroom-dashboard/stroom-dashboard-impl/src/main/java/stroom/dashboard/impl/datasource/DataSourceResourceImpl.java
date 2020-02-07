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

import com.codahale.metrics.health.HealthCheck.Result;
import stroom.datasource.shared.DataSourceFields;
import stroom.datasource.shared.DataSourceResource;
import stroom.docref.DocRef;
import stroom.meta.shared.MetaFields;
import stroom.security.api.SecurityContext;
import stroom.util.HasHealthCheck;
import stroom.util.shared.RestResource;

import javax.inject.Inject;

class DataSourceResourceImpl implements DataSourceResource, RestResource, HasHealthCheck {
    private final DataSourceProviderRegistry dataSourceProviderRegistry;
    private final SecurityContext securityContext;

    @Inject
    DataSourceResourceImpl(final DataSourceProviderRegistry dataSourceProviderRegistry,
                           final SecurityContext securityContext) {
        this.dataSourceProviderRegistry = dataSourceProviderRegistry;
        this.securityContext = securityContext;
    }

    @Override
    public DataSourceFields fetchFields(final DocRef dataSourceRef) {
        return securityContext.secureResult(() -> {
            if (dataSourceRef.equals(MetaFields.STREAM_STORE_DOC_REF)) {
                return new DataSourceFields(MetaFields.getFields());
            }

            // Elevate the users permissions for the duration of this task so they can read the index if they have 'use' permission.
            return securityContext.useAsReadResult(() -> dataSourceProviderRegistry.getDataSourceProvider(dataSourceRef)
                    .map(provider ->
                            new DataSourceFields(provider.getDataSource(dataSourceRef).getFields()))
                    .orElse(null));
        });
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}