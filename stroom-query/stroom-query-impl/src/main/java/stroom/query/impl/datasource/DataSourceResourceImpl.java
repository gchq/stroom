/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.query.impl.datasource;

import stroom.datasource.shared.DataSourceResource;
import stroom.docref.DocRef;
import stroom.docstore.shared.Documentation;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
class DataSourceResourceImpl implements DataSourceResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DataSourceResourceImpl.class);

    private final Provider<QueryService> queryServiceProvider;

    @Inject
    DataSourceResourceImpl(final Provider<QueryService> queryServiceProvider) {
        this.queryServiceProvider = queryServiceProvider;
    }

    @Override
    public ResultPage<QueryField> findFields(final FindFieldCriteria criteria) {
        return queryServiceProvider.get().findFields(criteria);
    }

    @Override
    public Documentation fetchDocumentation(final DocRef dataSourceRef) {
        return Documentation.of(queryServiceProvider.get().fetchDocumentation(dataSourceRef).orElse(""));
    }

    @Override
    public DocRef fetchDefaultExtractionPipeline(final DocRef dataSourceRef) {
        try {
            return queryServiceProvider.get().fetchDefaultExtractionPipeline(dataSourceRef).orElse(null);
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
        }
        return null;
    }
}
