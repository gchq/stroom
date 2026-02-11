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

import stroom.processor.shared.ProcessorFilter;
import stroom.security.shared.FindUserContext;
import stroom.security.user.api.UserRefLookup;

import jakarta.inject.Provider;
import org.jooq.Record;

import java.util.function.Function;

import static stroom.processor.impl.db.jooq.tables.ProcessorFilter.PROCESSOR_FILTER;

class RecordToProcessorFilterMapper implements Function<Record, ProcessorFilter> {

    private final QueryDataSerialiser queryDataSerialiser;
    private final Provider<UserRefLookup> userRefLookupProvider;

    public RecordToProcessorFilterMapper(final QueryDataSerialiser queryDataSerialiser,
                                         final Provider<UserRefLookup> userRefLookupProvider) {
        this.queryDataSerialiser = queryDataSerialiser;
        this.userRefLookupProvider = userRefLookupProvider;
    }

    @Override
    public ProcessorFilter apply(final Record record) {
        return ProcessorFilter
                .builder()
                .id(record.get(PROCESSOR_FILTER.ID))
                .version(record.get(PROCESSOR_FILTER.VERSION))
                .createTimeMs(record.get(PROCESSOR_FILTER.CREATE_TIME_MS))
                .createUser(record.get(PROCESSOR_FILTER.CREATE_USER))
                .updateTimeMs(record.get(PROCESSOR_FILTER.UPDATE_TIME_MS))
                .updateUser(record.get(PROCESSOR_FILTER.UPDATE_USER))
                .uuid(record.get(PROCESSOR_FILTER.UUID))
                .queryData(queryDataSerialiser.deserialise(record.get(PROCESSOR_FILTER.DATA)))
                .priority(record.get(PROCESSOR_FILTER.PRIORITY))
                .maxProcessingTasks(record.get(PROCESSOR_FILTER.MAX_PROCESSING_TASKS))
                .reprocess(record.get(PROCESSOR_FILTER.REPROCESS))
                .enabled(record.get(PROCESSOR_FILTER.ENABLED))
                .deleted(record.get(PROCESSOR_FILTER.DELETED))
                .export(record.get(PROCESSOR_FILTER.EXPORT))
                .minMetaCreateTimeMs(record.get(PROCESSOR_FILTER.MIN_META_CREATE_TIME_MS))
                .maxMetaCreateTimeMs(record.get(PROCESSOR_FILTER.MAX_META_CREATE_TIME_MS))
                .runAsUser(userRefLookupProvider
                        .get()
                        .getByUuid(record.get(PROCESSOR_FILTER.RUN_AS_USER_UUID), FindUserContext.RUN_AS)
                        .orElse(null))
                .build();
    }
}
