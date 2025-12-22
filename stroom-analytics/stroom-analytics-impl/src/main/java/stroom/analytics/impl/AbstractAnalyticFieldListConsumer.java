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
import stroom.query.common.v2.StringFieldValue;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValuesConsumer;
import stroom.search.extraction.AnalyticFieldListConsumer;
import stroom.search.extraction.FieldValue;
import stroom.search.extraction.FieldValueExtractor;
import stroom.search.extraction.MemoryIndex;

import java.util.List;
import java.util.function.Predicate;

abstract class AbstractAnalyticFieldListConsumer implements AnalyticFieldListConsumer {

    private final SearchRequest searchRequest;
    private final CompiledColumns compiledColumns;
    private final FieldValueExtractor fieldValueExtractor;
    private final ValuesConsumer valuesConsumer;
    private final MemoryIndex memoryIndex;
    private final Long minEventId;
    private final Predicate<Val[]> valFilter;

    private long eventId;

    AbstractAnalyticFieldListConsumer(final SearchRequest searchRequest,
                                      final CompiledColumns compiledColumns,
                                      final FieldValueExtractor fieldValueExtractor,
                                      final ValuesConsumer valuesConsumer,
                                      final MemoryIndex memoryIndex,
                                      final Long minEventId,
                                      final Predicate<Val[]> valFilter) {
        this.searchRequest = searchRequest;
        this.compiledColumns = compiledColumns;
        this.fieldValueExtractor = fieldValueExtractor;
        this.valuesConsumer = valuesConsumer;
        this.memoryIndex = memoryIndex;
        this.minEventId = minEventId;
        this.valFilter = valFilter;
    }

    @Override
    public void acceptFieldValues(final List<FieldValue> fieldValues) {
        eventId++;

        final FieldIndex fieldIndex = compiledColumns.getFieldIndex();
        // Filter events if we have already added them to LMDB.
        if (minEventId == null || minEventId <= eventId) {

            // See if this set of fields matches the rule expression.
            if (memoryIndex.match(searchRequest, fieldValues)) {
                // We have a match so pass the values on to the receiver.
                final Val[] values = new Val[fieldIndex.size()];
                for (final FieldValue fieldValue : fieldValues) {
                    final Integer index = fieldIndex.getPos(fieldValue.field().getFldName());
                    if (index != null) {
                        values[index] = fieldValue.value();
                    }
                }

                if (valFilter.test(values)) {
                    valuesConsumer.accept(Val.of(values));
                }
            }
        }
    }

    @Override
    public void acceptStringValues(final List<StringFieldValue> stringValues) {
        acceptFieldValues(fieldValueExtractor.convert(stringValues));
    }
}
