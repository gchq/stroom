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

package stroom.processor.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.util.shared.Clearable;
import stroom.util.shared.ResultPage;

import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Singleton
public class MockProcessorFilterDao implements ProcessorFilterDao, Clearable {

    private final MockIntCrud<ProcessorFilter> dao = new MockIntCrud<>();

    @Override
    public ProcessorFilter create(final ProcessorFilter processorFilter) {
        if (processorFilter.getProcessorFilterTracker() == null) {
            processorFilter.setProcessorFilterTracker(new ProcessorFilterTracker());
        }
        return dao.create(processorFilter);
    }

    @Override
    public Optional<ProcessorFilter> fetch(final int id) {
        return dao.fetch(id);
    }

    @Override
    public ProcessorFilter update(final ProcessorFilter processorFilter) {
        return dao.update(processorFilter);
    }

    @Override
    public boolean delete(final int id) {
        return dao.delete(id);
    }

    @Override
    public int logicalDeleteByProcessorFilterId(final int processorFilterId) {
        return fetch(processorFilterId)
                .map(processorFilter -> {
                    processorFilter.setDeleted(true);
                    return 1;
                })
                .orElse(0);
    }

    @Override
    public int logicallyDeleteOldProcessorFilters(final Instant deleteThreshold) {
        return 0;
    }

    @Override
    public ProcessorFilter restoreProcessorFilter(final ProcessorFilter processorFilter, final boolean resetTracker) {
        processorFilter.setDeleted(false);
        return processorFilter;
    }

    @Override
    public Set<String> physicalDeleteOldProcessorFilters(final Instant deleteThreshold) {
        return Collections.emptySet();
    }

    @Override
    public List<ProcessorFilter> fetchByRunAsUser(final String userUuid) {
        return Collections.emptyList();
    }

    @Override
    public Optional<ProcessorFilter> fetchByUuid(final String uuid) {
        return dao
                .getMap()
                .values()
                .stream()
                .filter(processorFilter ->
                        Objects.equals(processorFilter.getUuid(), uuid))
                .findAny();
    }

    @Override
    public ResultPage<ProcessorFilter> find(final ExpressionCriteria criteria) {
        final List<ProcessorFilter> list = new ArrayList<>(dao.getMap().values());
        return ResultPage.createCriterialBasedList(list, criteria);
    }

    @Override
    public void clear() {
        dao.clear();
    }
}
