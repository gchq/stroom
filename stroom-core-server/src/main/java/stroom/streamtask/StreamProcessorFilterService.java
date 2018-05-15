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

import stroom.entity.BaseEntityService;
import stroom.entity.CountService;
import stroom.entity.FindService;
import stroom.pipeline.shared.PipelineEntity;
import stroom.streamstore.shared.QueryData;
import stroom.streamtask.shared.FindStreamProcessorFilterCriteria;
import stroom.streamtask.shared.StreamProcessor;
import stroom.streamtask.shared.StreamProcessorFilter;

public interface StreamProcessorFilterService
        extends BaseEntityService<StreamProcessorFilter>,
        FindService<StreamProcessorFilter, FindStreamProcessorFilterCriteria>,
        CountService<FindStreamProcessorFilterCriteria> {
    void addFindStreamCriteria(StreamProcessor streamProcessor,
                               int priority,
                               QueryData queryData);

    StreamProcessorFilter createNewFilter(PipelineEntity pipeline,
                                          QueryData queryData,
                                          boolean enabled,
                                          int priority);
}
