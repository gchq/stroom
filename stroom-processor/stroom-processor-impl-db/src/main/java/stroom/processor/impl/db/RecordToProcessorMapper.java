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

package stroom.processor.impl.db;

import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorType;

import org.jooq.Record;

import java.util.function.Function;

import static stroom.processor.impl.db.jooq.tables.Processor.PROCESSOR;

class RecordToProcessorMapper implements Function<Record, Processor> {

    @Override
    public Processor apply(final Record record) {
        return Processor
                .builder()
                .id(record.get(PROCESSOR.ID))
                .version(record.get(PROCESSOR.VERSION))
                .createTimeMs(record.get(PROCESSOR.CREATE_TIME_MS))
                .createUser(record.get(PROCESSOR.CREATE_USER))
                .updateTimeMs(record.get(PROCESSOR.UPDATE_TIME_MS))
                .updateUser(record.get(PROCESSOR.UPDATE_USER))
                .uuid(record.get(PROCESSOR.UUID))
                .pipelineUuid(record.get(PROCESSOR.PIPELINE_UUID))
                .processorType(ProcessorType.fromDisplayValue(record.get(PROCESSOR.TASK_TYPE)))
                .enabled(record.get(PROCESSOR.ENABLED))
                .deleted(record.get(PROCESSOR.DELETED))
                .build();
    }
}
