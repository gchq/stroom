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

package stroom.processor.shared;

import stroom.docref.DocRef;
import stroom.util.shared.Document;

/**
 * A wrapper for a {@link ProcessorFilter} that has a name (which {@link ProcessorFilter}
 * doesn't.
 */
public class ProcessorFilterDoc implements Document {

    public static final String TYPE = ProcessorFilter.ENTITY_TYPE;

    private final ProcessorFilter processorFilter;
    private final String name;

    public ProcessorFilterDoc(final ProcessorFilter processorFilter,
                              final String name) {
        this.processorFilter = processorFilter;
        this.name = name;
    }

    public ProcessorFilter getProcessorFilter() {
        return processorFilter;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getType() {
        return ProcessorFilter.ENTITY_TYPE;
    }

    @Override
    public String getUuid() {
        return processorFilter.getUuid();
    }

    public Long getCreateTimeMs() {
        return processorFilter.getCreateTimeMs();
    }

    public String getCreateUser() {
        return processorFilter.getCreateUser();
    }

    public Long getUpdateTimeMs() {
        return processorFilter.getUpdateTimeMs();
    }

    public String getUpdateUser() {
        return processorFilter.getUpdateUser();
    }

    /**
     * @return A new builder for creating a {@link DocRef} for this document's type.
     */
    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
    }
}
