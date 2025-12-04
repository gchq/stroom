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

    private final QueryDataSerialiser queryDataXMLSerialiser;
    private final Provider<UserRefLookup> userRefLookupProvider;

    public RecordToProcessorFilterMapper(final QueryDataSerialiser queryDataXMLSerialiser,
                                         final Provider<UserRefLookup> userRefLookupProvider) {
        this.queryDataXMLSerialiser = queryDataXMLSerialiser;
        this.userRefLookupProvider = userRefLookupProvider;
    }

    @Override
    public ProcessorFilter apply(final Record record) {
        final ProcessorFilter processorFilter = new ProcessorFilter();
        processorFilter.setId(record.get(PROCESSOR_FILTER.ID));
        processorFilter.setVersion(record.get(PROCESSOR_FILTER.VERSION));
        processorFilter.setCreateTimeMs(record.get(PROCESSOR_FILTER.CREATE_TIME_MS));
        processorFilter.setCreateUser(record.get(PROCESSOR_FILTER.CREATE_USER));
        processorFilter.setUpdateTimeMs(record.get(PROCESSOR_FILTER.UPDATE_TIME_MS));
        processorFilter.setUpdateUser(record.get(PROCESSOR_FILTER.UPDATE_USER));
        processorFilter.setUuid(record.get(PROCESSOR_FILTER.UUID));
        processorFilter.setQueryData(queryDataXMLSerialiser.deserialise(record.get(PROCESSOR_FILTER.DATA)));
        processorFilter.setPriority(record.get(PROCESSOR_FILTER.PRIORITY));
        processorFilter.setMaxProcessingTasks(record.get(PROCESSOR_FILTER.MAX_PROCESSING_TASKS));
        processorFilter.setReprocess(record.get(PROCESSOR_FILTER.REPROCESS));
        processorFilter.setEnabled(record.get(PROCESSOR_FILTER.ENABLED));
        processorFilter.setDeleted(record.get(PROCESSOR_FILTER.DELETED));
        processorFilter.setExport(record.get(PROCESSOR_FILTER.EXPORT));
        processorFilter.setMinMetaCreateTimeMs(record.get(PROCESSOR_FILTER.MIN_META_CREATE_TIME_MS));
        processorFilter.setMaxMetaCreateTimeMs(record.get(PROCESSOR_FILTER.MAX_META_CREATE_TIME_MS));
        processorFilter.setRunAsUser(userRefLookupProvider
                .get()
                .getByUuid(record.get(PROCESSOR_FILTER.RUN_AS_USER_UUID), FindUserContext.RUN_AS)
                .orElse(null));
        return processorFilter;
    }
}
