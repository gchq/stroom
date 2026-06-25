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

import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.TaskStatus;

import org.jooq.Record;

import java.util.function.Function;

import static stroom.processor.impl.db.jooq.tables.ProcessorFeed.PROCESSOR_FEED;
import static stroom.processor.impl.db.jooq.tables.ProcessorNode.PROCESSOR_NODE;
import static stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK;

class RecordToProcessorTaskMapper implements Function<Record, ProcessorTask> {

    @Override
    public ProcessorTask apply(final Record record) {
        String nodeName = null;
        if (record.field(PROCESSOR_NODE.NAME) != null) {
            nodeName = record.get(PROCESSOR_NODE.NAME);
        }
        String feedName = null;
        if (record.field(PROCESSOR_FEED.NAME) != null) {
            feedName = record.get(PROCESSOR_FEED.NAME);
        }

        return ProcessorTask.builder()
                .id(record.get(PROCESSOR_TASK.ID))
                .version(record.get(PROCESSOR_TASK.VERSION))
                .metaId(record.get(PROCESSOR_TASK.META_ID))
                .data(record.get(PROCESSOR_TASK.DATA))
                .nodeName(nodeName)
                .feedName(feedName)
                .status(TaskStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(record.get(PROCESSOR_TASK.STATUS)))
                .startTimeMs(record.get(PROCESSOR_TASK.START_TIME_MS))
                .createTimeMs(record.get(PROCESSOR_TASK.CREATE_TIME_MS))
                .statusTimeMs(record.get(PROCESSOR_TASK.STATUS_TIME_MS))
                .endTimeMs(record.get(PROCESSOR_TASK.END_TIME_MS))
                .build();
    }
}
