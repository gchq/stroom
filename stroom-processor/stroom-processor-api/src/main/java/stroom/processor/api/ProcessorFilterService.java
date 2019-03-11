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

package stroom.processor.api;

import stroom.docref.DocRef;
import stroom.processor.shared.FindProcessorFilterCriteria;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;
import stroom.util.shared.BaseResultList;
import stroom.util.shared.HasIntCrud;

import java.util.Optional;

public interface ProcessorFilterService extends HasIntCrud<ProcessorFilter> {
    ProcessorFilter create(final DocRef pipelineRef,
                           final QueryData queryData,
                           final int priority,
                           final boolean enabled);

    ProcessorFilter create(final Processor processor,
                           final QueryData queryData,
                           final int priority,
                           final boolean enabled);
//
//    Optional<ProcessorFilter> fetch(final int id);
//
//    ProcessorFilter update(final ProcessorFilter processorFilter);
//
//    boolean delete(final int id);

    BaseResultList<ProcessorFilter> find(FindProcessorFilterCriteria criteria);
}
