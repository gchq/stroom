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

package stroom.meta.impl;

import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaResource;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.SelectionSummary;
import stroom.meta.shared.UpdateStatusRequest;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.List;
import java.util.stream.Collectors;

// TODO : @66 Add event logging
class MetaResourceImpl implements MetaResource {
    private final Provider<MetaService> metaServiceProvider;

    @Inject
    MetaResourceImpl(Provider<MetaService> metaServiceProvider) {
        this.metaServiceProvider = metaServiceProvider;
    }

    @Override
    public Integer updateStatus(final UpdateStatusRequest request) {
        return metaServiceProvider.get().updateStatus(
                request.getCriteria(),
                request.getCurrentStatus(),
                request.getNewStatus());
    }

    @Override
    public ResultPage<MetaRow> findMetaRow(final FindMetaCriteria criteria) {
        return metaServiceProvider.get().findDecoratedRows(criteria);
    }

    @Override
    public SelectionSummary getSelectionSummary(final FindMetaCriteria criteria) {
        return metaServiceProvider.get().getSelectionSummary(criteria);
    }

    @Override
    public SelectionSummary getReprocessSelectionSummary(final FindMetaCriteria criteria) {
        return metaServiceProvider.get().getReprocessSelectionSummary(criteria);
    }

    @Override
    public List<String> getTypes() {
        return metaServiceProvider
                .get()
                .getTypes()
                .stream()
                .sorted()
                .collect(Collectors.toList());
    }
}