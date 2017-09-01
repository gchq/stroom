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

package stroom.datasource;

import stroom.query.api.v2.DocRef;

import java.util.Optional;

public interface DataSourceProviderRegistry {
    /**
     * Gets a valid instance of a {@link RemoteDataSourceProvider} by querying service discovery
     *
     * @param dataSourceRef The docRef to get a data source provider for
     * @return A remote data source provider that can handle the passed docRef. Will return
     * an empty optional for two reasons:
     * There may be no services that can handle the passed docRefType.
     * The service has no instances that are up and enabled.
     */
    Optional<DataSourceProvider> getDataSourceProvider(DocRef dataSourceRef);
}
