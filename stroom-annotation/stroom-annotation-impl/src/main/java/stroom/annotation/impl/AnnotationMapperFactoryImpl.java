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

package stroom.annotation.impl;

import stroom.annotation.impl.AnnotationDao.RecordMappers;
import stroom.annotation.shared.EventId;
import stroom.index.shared.IndexConstants;
import stroom.query.api.SpecialColumns;
import stroom.query.common.v2.AnnotationMapperFactory;
import stroom.query.common.v2.StoredValueMapper;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Objects;
import java.util.stream.Stream;

public class AnnotationMapperFactoryImpl implements AnnotationMapperFactory {

    private final Provider<AnnotationService> annotationServiceProvider;

    @Inject
    public AnnotationMapperFactoryImpl(final Provider<AnnotationService> annotationServiceProvider) {
        this.annotationServiceProvider = annotationServiceProvider;
    }

    @Override
    public StoredValueMapper createMapper(final ValueReferenceIndex valueReferenceIndex) {
        final int streamIdIndex = getFieldValIndex(valueReferenceIndex,
                SpecialColumns.RESERVED_STREAM_ID, IndexConstants.STREAM_ID);
        final int eventIdIndex = getFieldValIndex(valueReferenceIndex,
                SpecialColumns.RESERVED_EVENT_ID, IndexConstants.EVENT_ID);

        if (streamIdIndex == -1 || eventIdIndex == -1) {
            return AnnotationMapperFactory.NO_OP.createMapper(valueReferenceIndex);
        }

        final AnnotationService annotationService = annotationServiceProvider.get();
        final RecordMappers mappers = annotationService.createDecorationMappers(valueReferenceIndex);
        return new StoredValueMapperImpl(annotationService, streamIdIndex, eventIdIndex, mappers);
    }

    private int getFieldValIndex(final ValueReferenceIndex valueReferenceIndex,
                                 final String primaryName,
                                 final String secondaryName) {
        return Objects.requireNonNullElse(
                valueReferenceIndex.getFieldValIndex(primaryName),
                Objects.requireNonNullElse(
                        valueReferenceIndex.getFieldValIndex(secondaryName), -1));
    }

    private record StoredValueMapperImpl(AnnotationService annotationService,
                                         int streamIdIndex,
                                         int eventIdIndex,
                                         RecordMappers mappers) implements StoredValueMapper {

        @Override
        public Stream<StoredValues> create(final StoredValues storedValues) {
            final Val streamId = (Val) storedValues.get(streamIdIndex);
            final Val eventId = (Val) storedValues.get(eventIdIndex);
            if (streamId == null || !streamId.type().isNumber() || eventId == null || !eventId.type().isNumber()) {
                return Stream.of(storedValues);
            }

            return annotationService.decorate(new EventId(streamId.toLong(), eventId.toLong()), storedValues, mappers);
        }
    }
}
