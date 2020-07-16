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

package stroom.data.retention.impl;

import stroom.data.retention.shared.DataRetentionDeleteSummaryRequest;
import stroom.data.retention.shared.DataRetentionDeleteSummaryResponse;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.retention.shared.DataRetentionRulesResource;
import stroom.meta.api.MetaService;

import io.swagger.annotations.ApiParam;

import javax.inject.Inject;
import javax.inject.Provider;

class DataRetentionRulesResourceImpl implements DataRetentionRulesResource {
    private final Provider<DataRetentionRulesService> dataRetentionRulesServiceProvider;
    private final Provider<MetaService> metaServiceProvider;

    @Inject
    DataRetentionRulesResourceImpl(final Provider<DataRetentionRulesService> dataRetentionRulesServiceProvider,
                                   final Provider<MetaService> metaServiceProvider) {
        this.dataRetentionRulesServiceProvider = dataRetentionRulesServiceProvider;
        this.metaServiceProvider = metaServiceProvider;
    }

    @Override
    public DataRetentionRules read() {
        return dataRetentionRulesServiceProvider.get().getOrCreate();
    }

    @Override
    public DataRetentionRules update(final DataRetentionRules dataRetentionRules) {
        return dataRetentionRulesServiceProvider.get().writeDocument(dataRetentionRules);
    }

    @Override
    public DataRetentionDeleteSummaryResponse getRetentionDeletionSummary(
            @ApiParam("request") DataRetentionDeleteSummaryRequest request) {

        return new DataRetentionDeleteSummaryResponse(
                metaServiceProvider.get()
                        .getRetentionDeleteSummary(
                                request.getQueryId(),
                                request.getDataRetentionRules(),
                                request.getCriteria()),
                request.getQueryId());
    }

    @Override
    public Boolean cancelQuery(final String queryId) {
        return metaServiceProvider.get().cancelRetentionDeleteSummary(queryId);
    }
}