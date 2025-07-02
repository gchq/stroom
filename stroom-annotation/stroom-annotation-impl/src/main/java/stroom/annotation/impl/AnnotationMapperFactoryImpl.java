package stroom.annotation.impl;

import stroom.annotation.impl.AnnotationMapper.Mutator;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationColumns;
import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.EventId;
import stroom.query.api.Column;
import stroom.query.api.ColumnFilter;
import stroom.query.api.SpecialColumns;
import stroom.query.common.v2.AnnotationColumnValueProvider;
import stroom.query.common.v2.AnnotationMapperFactory;
import stroom.query.common.v2.ItemMapper;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AnnotationMapperFactoryImpl implements AnnotationMapperFactory {

    private static final Map<String, Function<Annotation, Val>> EXTRACTION_FUNCTIONS = Map.ofEntries(
            createLongFunction(AnnotationColumns.ANNOTATION_ID, Annotation::getId),
            createStringFunction(AnnotationColumns.ANNOTATION_UUID, Annotation::getUuid),
            createDateFunction(AnnotationColumns.ANNOTATION_CREATED_ON, Annotation::getCreateTimeMs),
            createStringFunction(AnnotationColumns.ANNOTATION_CREATED_BY, Annotation::getCreateUser),
            createDateFunction(AnnotationColumns.ANNOTATION_UPDATED_ON, Annotation::getUpdateTimeMs),
            createStringFunction(AnnotationColumns.ANNOTATION_UPDATED_BY, Annotation::getUpdateUser),
            createStringFunction(AnnotationColumns.ANNOTATION_TITLE, Annotation::getName),
            createStringFunction(AnnotationColumns.ANNOTATION_SUBJECT, Annotation::getSubject),
            createStringFunction(AnnotationColumns.ANNOTATION_STATUS, annotation ->
                    NullSafe.get(annotation, Annotation::getStatus, AnnotationTag::getName)),
            createStringFunction(AnnotationColumns.ANNOTATION_ASSIGNED_TO, annotation ->
                    NullSafe.get(annotation, Annotation::getAssignedTo, UserRef::getDisplayName)),
            createStringFunction(AnnotationColumns.ANNOTATION_LABEL, annotation ->
                    NullSafe.get(annotation, Annotation::getLabels, labels -> labels
                            .stream()
                            .map(AnnotationTag::getName)
                            .collect(Collectors.joining(", ")))),
            createStringFunction(AnnotationColumns.ANNOTATION_COLLECTION, annotation ->
                    NullSafe.get(annotation, Annotation::getCollections, collections -> collections
                            .stream()
                            .map(AnnotationTag::getName)
                            .collect(Collectors.joining(", ")))));

    private final Provider<AnnotationService> annotationServiceProvider;

    @Inject
    public AnnotationMapperFactoryImpl(final Provider<AnnotationService> annotationServiceProvider) {
        this.annotationServiceProvider = annotationServiceProvider;
    }

    private static Entry<String, Function<Annotation, Val>> createLongFunction(final Column column,
                                                                               final Function<Annotation, Long>
                                                                                       mapper) {
        return Map.entry(column.getId(), annotation -> {
            final Long l = mapper.apply(annotation);
            return NullSafe.getOrElse(l, ValLong::create, ValNull.INSTANCE);
        });
    }

    private static Entry<String, Function<Annotation, Val>> createStringFunction(final Column column,
                                                                                 final Function<Annotation, String>
                                                                                         mapper) {
        return Map.entry(column.getId(), annotation -> {
            final String s = mapper.apply(annotation);
            return NullSafe.getOrElse(s, ValString::create, ValNull.INSTANCE);
        });
    }

    private static Entry<String, Function<Annotation, Val>> createDateFunction(final Column column,
                                                                               final Function<Annotation, Long>
                                                                                       mapper) {
        return Map.entry(column.getId(), annotation -> {
            final Long l = mapper.apply(annotation);
            return NullSafe.getOrElse(l, ValDate::create, ValNull.INSTANCE);
        });
    }

    @Override
    public ItemMapper createMapper(final List<Column> newColumns,
                                         final ErrorConsumer errorConsumer,
                                         final ItemMapper parentMapper) {
        final int streamIdIndex = getColumnIndexById(newColumns, SpecialColumns.RESERVED_STREAM_ID);
        final int eventIdIndex = getColumnIndexById(newColumns, SpecialColumns.RESERVED_EVENT_ID);
        final List<Mutator> mutators = new ArrayList<>();
        boolean annotationFilter = false;
        for (int i = 0; i < newColumns.size(); i++) {
            final int index = i;
            final Column column = newColumns.get(index);
            // Try to find a template annotation column.
            final Column templateColumn = getTemplateColumn(column);
            if (templateColumn != null) {
                // If we found a column the create a mutator to add the annotation column value to the result.
                final Function<Annotation, Val> function = EXTRACTION_FUNCTIONS.get(templateColumn.getId());
                mutators.add((values, annotation) -> values[index] = function.apply(annotation));
                // If the column has a filter then we will be doing an inner join.
                if (!NullSafe.getOrElse(column, Column::getColumnFilter, ColumnFilter::getFilter, "").isEmpty()) {
                    annotationFilter = true;
                }
            }
        }

        // Allow an outer join if we don't have a filter on annotations.
        final boolean outerJoin = !annotationFilter;

        if (streamIdIndex == -1 || eventIdIndex == -1 || mutators.isEmpty()) {
            return parentMapper;
        }

        final AnnotationService annotationService = annotationServiceProvider.get();
        return new AnnotationMapper(
                annotationService,
                parentMapper,
                streamIdIndex,
                eventIdIndex,
                outerJoin,
                mutators,
                errorConsumer);
    }

    @Override
    public AnnotationColumnValueProvider createValues(final List<Column> columns, final int columnIndex) {
        final int streamIdIndex = getColumnIndexById(columns, SpecialColumns.RESERVED_STREAM_ID);
        final int eventIdIndex = getColumnIndexById(columns, SpecialColumns.RESERVED_EVENT_ID);
        final Column column = columns.get(columnIndex);
        final Optional<Function<Annotation, Val>> optionalFunction = EXTRACTION_FUNCTIONS.entrySet()
                .stream()
                .filter(entry -> column.getId().endsWith(entry.getKey()))
                .map(Entry::getValue)
                .findFirst();
        if (streamIdIndex == -1 || eventIdIndex == -1 || optionalFunction.isEmpty()) {
            return item -> Collections.singletonList(item.getValue(columnIndex));
        }
        final Function<Annotation, Val> function = optionalFunction.get();
        final AnnotationService annotationService = annotationServiceProvider.get();
        return item -> {
            final Long streamId = NullSafe.get(item.getValue(streamIdIndex), Val::toLong);
            final Long eventId = NullSafe.get(item.getValue(eventIdIndex), Val::toLong);
            if (streamId == null || eventId == null) {
                return Collections.singletonList(item.getValue(columnIndex));
            }
            final List<Annotation> list = annotationService
                    .getAnnotationsForEvents(new EventId(streamId, eventId));
            return list.stream().map(function).toList();
        };
    }
//
//    private Optional<Mutator> createMutator(final List<Column> columns,
//                                            final Column templateColumn) {
//        final String id = templateColumn.getId();
//        for (int i = 0; i < columns.size(); i++) {
//            final Column column = columns.get(i);
//            if (id.equals(column.getId()) || column.getId().endsWith(id)) {
//                final int index = i;
//                final Function<Annotation, Val> function = EXTRACTION_FUNCTIONS.get(templateColumn.getId());
//                return Optional.of((values, annotation) -> values[index] = function.apply(annotation));
//            }
//        }
//        return Optional.empty();
//    }

    private Column getTemplateColumn(final Column column) {
        final String id = column.getId();
        for (int i = 0; i < AnnotationColumns.COLUMNS.size(); i++) {
            final Column templateColumn = AnnotationColumns.COLUMNS.get(i);
            if (id.endsWith(templateColumn.getId())) {
                return templateColumn;
            }
        }
        return null;
    }

    private int getColumnIndexById(final List<Column> columns, final String id) {
        for (int i = 0; i < columns.size(); i++) {
            final Column column = columns.get(i);
            if (id.equals(column.getId())) {
                return i;
            }
        }
        return -1;
    }


}
