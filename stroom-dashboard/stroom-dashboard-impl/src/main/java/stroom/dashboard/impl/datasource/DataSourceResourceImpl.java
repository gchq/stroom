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

import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.shared.DataSourceResource;
import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.meta.shared.MetaFields;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
class DataSourceResourceImpl implements DataSourceResource {

    private final Provider<DataSourceProviderRegistry> dataSourceProviderRegistryProvider;

    @Inject
    DataSourceResourceImpl(final Provider<DataSourceProviderRegistry> dataSourceProviderRegistryProvider) {
        this.dataSourceProviderRegistryProvider = dataSourceProviderRegistryProvider;
    }

    @Override
    public List<AbstractField> fetchFields(final DocRef dataSourceRef) {
        if (dataSourceRef.equals(MetaFields.STREAM_STORE_DOC_REF)) {
            return MetaFields.getFields();
        }

        return dataSourceProviderRegistryProvider.get()
                .getDataSourceProvider(dataSourceRef)
                .map(provider -> provider.getDataSource(dataSourceRef).getFields())
                .orElse(null);
    }
}
