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
import stroom.processor.shared.Processor;
import stroom.util.shared.HasIntCrud;
import stroom.util.shared.ResultPage;

import java.time.Instant;
import java.util.Optional;

public interface ProcessorDao extends HasIntCrud<Processor> {

    ResultPage<Processor> find(ExpressionCriteria criteria);

    Optional<Processor> fetchByUuid(String uuid);

    Optional<Processor> fetchByPipelineUuid(String pipelineUuid);

    /**
     * Will also logically delete all associated processor filters.
     *
     * @return True if the processor is deleted.
     */
    int logicalDeleteByProcessorId(int processorId);

    int physicalDeleteOldProcessors(Instant deleteThreshold);
}
