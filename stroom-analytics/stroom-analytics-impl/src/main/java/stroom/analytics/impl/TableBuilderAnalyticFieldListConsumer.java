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

package stroom.analytics.impl;

import stroom.query.api.SearchRequest;
import stroom.query.common.v2.CompiledColumns;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValuesConsumer;
import stroom.search.extraction.FieldValueExtractor;
import stroom.search.extraction.MemoryIndex;

import java.util.function.Predicate;

public class TableBuilderAnalyticFieldListConsumer extends AbstractAnalyticFieldListConsumer {

    public TableBuilderAnalyticFieldListConsumer(final SearchRequest searchRequest,
                                                 final CompiledColumns compiledColumns,
                                                 final FieldValueExtractor fieldValueExtractor,
                                                 final ValuesConsumer valuesConsumer,
                                                 final MemoryIndex memoryIndex,
                                                 final Long minEventId,
                                                 final Predicate<Val[]> valFilter) {
        super(
                searchRequest,
                compiledColumns,
                fieldValueExtractor,
                valuesConsumer,
                memoryIndex,
                minEventId,
                valFilter);
    }

    @Override
    public void start() {

    }

    @Override
    public void end() {

    }
}
