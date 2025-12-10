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

import stroom.event.logging.rs.api.AutoLogged;
import stroom.processor.api.ProcessorService;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorResource;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged
class ProcessorResourceImpl implements ProcessorResource {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProcessorResourceImpl.class);

    private final Provider<ProcessorService> processorServiceProvider;

    @Inject
    ProcessorResourceImpl(final Provider<ProcessorService> processorServiceProvider) {
        this.processorServiceProvider = processorServiceProvider;
    }

    @Override
    public Processor fetch(final Integer id) {
        return processorServiceProvider.get().fetch(id).orElse(null);
    }

    @Override
    public boolean delete(final Integer id) {
        processorServiceProvider.get().delete(id);
        return true;
    }

    @Override
    public boolean setEnabled(final Integer id, final Boolean enabled) {
        processorServiceProvider.get().setEnabled(id, enabled);
        return true;
    }
}
