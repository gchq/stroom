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

import stroom.annotation.shared.AnnotationDecorationFields;
import stroom.annotation.shared.AnnotationIdentity;
import stroom.annotation.shared.EventId;
import stroom.index.shared.IndexConstants;
import stroom.query.api.SpecialColumns;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.AnnotationMapperFactory;
import stroom.query.common.v2.StoredValueMapper;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ref.StoredValues;
import stroom.query.language.functions.ref.ValueReferenceIndex;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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

        final Set<QueryField> requiredAnnotationFields = new HashSet<>();
        final List<Mutator> mutators = AnnotationDecorationFields.DECORATION_FIELDS
                .stream()
                .map(field -> {
                    final Integer pos = valueReferenceIndex.getFieldValIndex(field.getFldName());
                    if (pos == null) {
                        return null;
                    }
                    requiredAnnotationFields.add(field);
                    return (Mutator) (storedValues, annotationValues) ->
                            storedValues.set(pos, annotationValues.get(field));
                })
                .filter(Objects::nonNull)
                .toList();

        // Don't do any annotation decoration if we were not asked for any annotation fields.
        if (mutators.isEmpty()) {
            return AnnotationMapperFactory.NO_OP.createMapper(valueReferenceIndex);
        }

        // If the only required annotation field is the id then we will not decorate.
        // TODO : The problem with this is that the user might just want to see the annotation id but as it is always
        //  added by the UI invisibly we cannot know if it's inclusion is intentional or not at this point in the code.
        //  Ideally the UI would only add a special hidden annotation field by default so that we could just ignore if
        //  that was the only field present here.
        if (requiredAnnotationFields.size() == 1 &&
            requiredAnnotationFields.contains(AnnotationDecorationFields.ANNOTATION_ID_FIELD)) {
            return AnnotationMapperFactory.NO_OP.createMapper(valueReferenceIndex);
        }

        final List<Mutator> allMutators;

        // Add annotation id if needed.
        final int annotationIdIndex = getFieldValIndex(valueReferenceIndex, SpecialColumns.RESERVED_ANNOTATION_ID,
                AnnotationDecorationFields.ANNOTATION_ID);
        if (annotationIdIndex != -1) {
            allMutators = new ArrayList<>(mutators);
            allMutators.add((storedValues, annotationValues) -> storedValues.set(
                    annotationIdIndex,
                    ValLong.create(annotationValues.getAnnotationIdentity().getId())));
        } else {
            allMutators = mutators;
        }

        final AnnotationService annotationService = annotationServiceProvider.get();
        return new StoredValueMapperImpl(annotationService, streamIdIndex, eventIdIndex, requiredAnnotationFields,
                allMutators);
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
                                         Set<QueryField> requiredAnnotationFields,
                                         List<Mutator> mutators) implements StoredValueMapper {

        @Override
        public Stream<StoredValues> create(final StoredValues storedValues) {
            final Val streamId = (Val) storedValues.get(streamIdIndex);
            final Val eventId = (Val) storedValues.get(eventIdIndex);
            if (streamId == null || !streamId.type().isNumber() || eventId == null || !eventId.type().isNumber()) {
                return Stream.of(storedValues);
            }

            // Start by getting a list of annotation ids.
            final Collection<AnnotationIdentity> idList = annotationService
                    .getAnnotationIdListForEvent(new EventId(streamId.toLong(), eventId.toLong()));

            // If we get no ids then just return.
            if (idList.isEmpty()) {
                return Stream.of(storedValues);
            }

            // Get requested annotation fields for the ids.
            final Collection<AnnotationValues> valueList = annotationService
                    .getAnnotationValues(idList, requiredAnnotationFields);

            // If we can not resolve any annotation fields (possibly due to permissions) then just return.
            if (valueList.isEmpty()) {
                return Stream.of(storedValues);
            }

            // If we have id's then turn them into the values we need.
            if (valueList.size() == 1) {
                for (final Mutator mutator : mutators) {
                    mutator.mutate(storedValues, valueList.iterator().next());
                }
                return Stream.of(storedValues);
            }

            return valueList.stream().map(annotationValues -> {
                final StoredValues copy = storedValues.copy();
                copy.setPeriod(storedValues.getPeriod());
                for (final Mutator mutator : mutators) {
                    mutator.mutate(copy, annotationValues);
                }

                return copy;
            });
        }
    }

    private interface Mutator {

        void mutate(StoredValues storedValues, AnnotationValues annotationValues);
    }
}
