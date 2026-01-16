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
import stroom.event.logging.rs.api.AutoLogged;
import stroom.processor.shared.ProcessorProfile;
import stroom.processor.shared.ProcessorProfileResource;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
class ProcessorProfileResourceImpl implements ProcessorProfileResource {

    private final Provider<ProcessorProfileService> processorProfileServiceProvider;

    @Inject
    ProcessorProfileResourceImpl(final Provider<ProcessorProfileService> processorProfileServiceProvider) {
        this.processorProfileServiceProvider = processorProfileServiceProvider;
    }

    @Override
    public ResultPage<ProcessorProfile> find(final ExpressionCriteria request) {
        return ResultPage.createUnboundedList(processorProfileServiceProvider.get().getAll());
    }

    @Override
    public ProcessorProfile create(final String name) {
        return processorProfileServiceProvider.get().getOrCreate(name);
    }

    @Override
    public ProcessorProfile fetch(final Integer id) {
        return processorProfileServiceProvider.get().get(id);
    }

    @Override
    public ProcessorProfile fetchByName(final String name) {
        return processorProfileServiceProvider.get().get(name);
    }

    @Override
    public ProcessorProfile update(final Integer id, final ProcessorProfile indexVolumeGroup) {
        return processorProfileServiceProvider.get().update(indexVolumeGroup);
    }

    @Override
    public Boolean delete(final Integer id) {
        processorProfileServiceProvider.get().delete(id);
        return true;
    }
}
