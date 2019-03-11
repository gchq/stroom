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
 *
 */

package stroom.processor.impl;

import stroom.docref.DocRef;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.FindProcessorFilterCriteria;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;
import stroom.util.shared.BaseResultList;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Mock object.
 * <p>
 * In memory simple process manager that also uses the mock stream store.
 */
@Singleton
public class MockProcessorFilterService implements ProcessorFilterService {
    private final MockIntCrud<ProcessorFilter> mockIntCrud = new MockIntCrud<>();

    @Override
    public ProcessorFilter create(final DocRef pipelineRef, final QueryData queryData, final int priority, final boolean enabled) {
        return null;
    }

    @Override
    public ProcessorFilter create(final Processor processor, final QueryData queryData, final int priority, final boolean enabled) {
        // now create the filter and tracker
        ProcessorFilter filter = new ProcessorFilter();
//        AuditUtil.stamp(securityContext.getUserId(), filter);
        // Blank tracker
        filter.setEnabled(enabled);
        filter.setPriority(priority);
        filter.setProcessor(processor);
        filter.setQueryData(queryData);
        return create(filter);
    }

    @Override
    public ProcessorFilter create(final ProcessorFilter processorFilter) {
        return mockIntCrud.create(processorFilter);
    }

    @Override
    public Optional<ProcessorFilter> fetch(final int id) {
        return Optional.empty();
    }

    @Override
    public ProcessorFilter update(final ProcessorFilter processorFilter) {
        return null;
    }

    @Override
    public boolean delete(final int id) {
        return false;
    }

    @Override
    public BaseResultList<ProcessorFilter> find(final FindProcessorFilterCriteria criteria) {
        return null;
    }


    //    @Override
//    public ProcessorFilter createFilter(final Processor streamProcessor, final QueryData queryData, final boolean enabled, final int priority) {
//        return null;
//    }
//
//    @Override
//    public ProcessorFilter createFilter(final DocRef pipelineRef,
//                                        final QueryData findStreamCriteria,
//                                        final boolean enabled,
//                                        final int priority) {
//        final ProcessorFilter filter = new ProcessorFilter();
//        filter.setProcessorFilterTracker(new ProcessorFilterTracker());
//        filter.setPriority(priority);
//        filter.setProcessor(streamProcessor);
//        filter.setQueryData(queryData);
//
//        save(filter);
//    }
//
//    @Override
//    public Class<ProcessorFilter> getEntityClass() {
//        return ProcessorFilter.class;
//    }
}
