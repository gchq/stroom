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
        final Processor processor = new Processor();
        processor.setId(record.get(PROCESSOR.ID));
        processor.setVersion(record.get(PROCESSOR.VERSION));
        processor.setCreateTimeMs(record.get(PROCESSOR.CREATE_TIME_MS));
        processor.setCreateUser(record.get(PROCESSOR.CREATE_USER));
        processor.setUpdateTimeMs(record.get(PROCESSOR.UPDATE_TIME_MS));
        processor.setUpdateUser(record.get(PROCESSOR.UPDATE_USER));
        processor.setUuid(record.get(PROCESSOR.UUID));
        processor.setPipelineUuid(record.get(PROCESSOR.PIPELINE_UUID));
        processor.setProcessorType(ProcessorType.fromDisplayValue(record.get(PROCESSOR.TASK_TYPE)));
        processor.setEnabled(record.get(PROCESSOR.ENABLED));
        processor.setDeleted(record.get(PROCESSOR.DELETED));
        return processor;
    }
}
