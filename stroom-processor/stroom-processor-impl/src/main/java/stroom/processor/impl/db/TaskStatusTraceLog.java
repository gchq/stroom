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

import org.jooq.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static stroom.processor.impl.db.jooq.tables.ProcessorTask.PROCESSOR_TASK;

class TaskStatusTraceLog {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskStatusTraceLog.class);

    void createdTasks(final Class<?> clazz, final List<Record> records) {
        if (LOGGER.isTraceEnabled() && records.size() > 0) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Master created ");
            sb.append(records.size());
            appendStreamTaskList(sb, records);
            appendClass(sb, clazz);
            LOGGER.trace(sb.toString());
        }
    }

    private void appendStreamTaskList(final StringBuilder sb, final List<Record> records) {
        sb.append(" ( ");
        for (final Record record : records) {
            sb.append(record.get(PROCESSOR_TASK.ID));
            sb.append(" ");
        }
        sb.append(")");
    }

    private void appendClass(final StringBuilder sb, final Class<?> clazz) {
        sb.append(" [");
        sb.append(clazz.getSimpleName());
        sb.append("]");
    }
}
