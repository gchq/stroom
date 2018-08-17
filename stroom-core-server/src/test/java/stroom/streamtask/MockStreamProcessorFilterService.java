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

package stroom.streamtask;

import stroom.entity.MockEntityService;
import stroom.docref.DocRef;
import stroom.streamstore.shared.QueryData;
import stroom.streamtask.shared.FindStreamProcessorFilterCriteria;
import stroom.streamtask.shared.Processor;
import stroom.streamtask.shared.ProcessorFilter;
import stroom.streamtask.shared.ProcessorFilterTracker;

import javax.inject.Singleton;

/**
 * Mock object.
 * <p>
 * In memory simple process manager that also uses the mock stream store.
 */
@Singleton
public class MockStreamProcessorFilterService
        extends MockEntityService<ProcessorFilter, FindStreamProcessorFilterCriteria>
        implements StreamProcessorFilterService {
    @Override
    public void addFindStreamCriteria(final Processor streamProcessor,
                                      final int priority,
                                      final QueryData queryData) {
        final ProcessorFilter filter = new ProcessorFilter();
        filter.setStreamProcessorFilterTracker(new ProcessorFilterTracker());
        filter.setPriority(priority);
        filter.setStreamProcessor(streamProcessor);
        filter.setQueryData(queryData);

        save(filter);
    }

    @Override
    public ProcessorFilter createNewFilter(final DocRef pipelineRef,
                                           final QueryData findStreamCriteria,
                                           final boolean enabled,
                                           final int priority) {
        return null;
    }

    @Override
    public Class<ProcessorFilter> getEntityClass() {
        return ProcessorFilter.class;
    }
}
