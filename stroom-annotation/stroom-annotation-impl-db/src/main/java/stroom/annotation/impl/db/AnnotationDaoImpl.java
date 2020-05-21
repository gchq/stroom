package stroom.annotation.impl.db;

import stroom.annotation.api.AnnotationFields;
import stroom.annotation.impl.AnnotationDao;
import stroom.annotation.impl.db.jooq.tables.records.AnnotationRecord;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationDetail;
import stroom.annotation.shared.AnnotationEntry;
import stroom.annotation.shared.CreateEntryRequest;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.EventLink;
import stroom.annotation.shared.SetAssignedToRequest;
import stroom.annotation.shared.SetStatusRequest;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ValNull;
import stroom.dashboard.expression.v1.ValString;
import stroom.datasource.api.v2.AbstractField;
import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.JooqUtil;
import stroom.db.util.ValueMapper;
import stroom.db.util.ValueMapper.Mapper;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.common.v2.DateExpressionParser;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Sort;

import org.jooq.Condition;
import org.jooq.Cursor;
import org.jooq.Field;
import org.jooq.OrderField;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import static stroom.annotation.impl.db.jooq.tables.Annotation.ANNOTATION;
import static stroom.annotation.impl.db.jooq.tables.AnnotationDataLink.ANNOTATION_DATA_LINK;
import static stroom.annotation.impl.db.jooq.tables.AnnotationEntry.ANNOTATION_ENTRY;

class AnnotationDaoImpl implements AnnotationDao {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnnotationDaoImpl.class);

    private static final Function<Record, Annotation> RECORD_TO_ANNOTATION_MAPPER = record -> {
        final Annotation annotation = new Annotation();
        annotation.setId(record.get(ANNOTATION.ID));
        annotation.setVersion(record.get(ANNOTATION.VERSION));
        annotation.setCreateTime(record.get(ANNOTATION.CREATE_TIME_MS));
        annotation.setCreateUser(record.get(ANNOTATION.CREATE_USER));
        annotation.setUpdateTime(record.get(ANNOTATION.UPDATE_TIME_MS));
        annotation.setUpdateUser(record.get(ANNOTATION.UPDATE_USER));
        annotation.setTitle(record.get(ANNOTATION.TITLE));
        annotation.setSubject(record.get(ANNOTATION.SUBJECT));
        annotation.setStatus(record.get(ANNOTATION.STATUS));
        annotation.setAssignedTo(record.get(ANNOTATION.ASSIGNED_TO));
        annotation.setComment(record.get(ANNOTATION.COMMENT));
        annotation.setHistory(record.get(ANNOTATION.HISTORY));
        return annotation;
    };

    private static final Function<Record, AnnotationEntry> RECORD_TO_ANNOTATION_ENTRY_MAPPER = record -> {
        final AnnotationEntry entry = new AnnotationEntry();
        entry.setId(record.get(ANNOTATION_ENTRY.ID));
        entry.setVersion(record.get(ANNOTATION_ENTRY.VERSION));
        entry.setCreateTime(record.get(ANNOTATION_ENTRY.CREATE_TIME_MS));
        entry.setCreateUser(record.get(ANNOTATION_ENTRY.CREATE_USER));
        entry.setUpdateTime(record.get(ANNOTATION_ENTRY.UPDATE_TIME_MS));
        entry.setUpdateUser(record.get(ANNOTATION_ENTRY.UPDATE_USER));
        entry.setEntryType(record.get(ANNOTATION_ENTRY.TYPE));
        entry.setData(record.get(ANNOTATION_ENTRY.DATA));
        return entry;
    };

    private static final Map<String, Field<String>> UPDATE_FIELD_MAP = new HashMap<>();

    static {
        UPDATE_FIELD_MAP.put(Annotation.TITLE, ANNOTATION.TITLE);
        UPDATE_FIELD_MAP.put(Annotation.SUBJECT, ANNOTATION.SUBJECT);
        UPDATE_FIELD_MAP.put(Annotation.STATUS, ANNOTATION.STATUS);
        UPDATE_FIELD_MAP.put(Annotation.ASSIGNED_TO, ANNOTATION.ASSIGNED_TO);
        UPDATE_FIELD_MAP.put(Annotation.COMMENT, ANNOTATION.COMMENT);
    }

    private final AnnotationDbConnProvider connectionProvider;
    private final ExpressionMapper expressionMapper;
    private final ValueMapper valueMapper;

    @Inject
    AnnotationDaoImpl(final AnnotationDbConnProvider connectionProvider,
                      final ExpressionMapperFactory expressionMapperFactory) {
        this.connectionProvider = connectionProvider;

        expressionMapper = expressionMapperFactory.create();
        expressionMapper.map(AnnotationFields.ID_FIELD, ANNOTATION.ID, Long::valueOf);
//        expressionMapper.map(AnnotationDataSource.STREAM_ID_FIELD, ANNOTATION_DATA_LINK.STREAM_ID, Long::valueOf);
//        expressionMapper.map(AnnotationDataSource.EVENT_ID_FIELD, ANNOTATION_DATA_LINK.EVENT_ID, Long::valueOf);
        expressionMapper.map(AnnotationFields.CREATED_ON_FIELD, ANNOTATION.CREATE_TIME_MS, value -> getDate(AnnotationFields.CREATED_ON, value));
        expressionMapper.map(AnnotationFields.CREATED_BY_FIELD, ANNOTATION.CREATE_USER, value -> value);
        expressionMapper.map(AnnotationFields.UPDATED_ON_FIELD, ANNOTATION.UPDATE_TIME_MS, value -> getDate(AnnotationFields.UPDATED_ON, value));
        expressionMapper.map(AnnotationFields.UPDATED_BY_FIELD, ANNOTATION.UPDATE_USER, value -> value);
        expressionMapper.map(AnnotationFields.TITLE_FIELD, ANNOTATION.TITLE, value -> value);
        expressionMapper.map(AnnotationFields.SUBJECT_FIELD, ANNOTATION.SUBJECT, value -> value);
        expressionMapper.map(AnnotationFields.STATUS_FIELD, ANNOTATION.STATUS, value -> value);
        expressionMapper.map(AnnotationFields.ASSIGNED_TO_FIELD, ANNOTATION.ASSIGNED_TO, value -> value);
        expressionMapper.map(AnnotationFields.COMMENT_FIELD, ANNOTATION.COMMENT, value -> value);
        expressionMapper.map(AnnotationFields.HISTORY_FIELD, ANNOTATION.HISTORY, value -> value);

        valueMapper = new ValueMapper();
        valueMapper.map(AnnotationFields.ID_FIELD, ANNOTATION.ID, ValLong::create);
//        valueMapper.map(AnnotationDataSource.STREAM_ID_FIELD, ANNOTATION_DATA_LINK.STREAM_ID, ValLong::create);
//        valueMapper.map(AnnotationDataSource.EVENT_ID_FIELD, ANNOTATION_DATA_LINK.EVENT_ID, ValLong::create);
        valueMapper.map(AnnotationFields.CREATED_ON_FIELD, ANNOTATION.CREATE_TIME_MS, ValLong::create);
        valueMapper.map(AnnotationFields.CREATED_BY_FIELD, ANNOTATION.CREATE_USER, ValString::create);
        valueMapper.map(AnnotationFields.UPDATED_ON_FIELD, ANNOTATION.UPDATE_TIME_MS, ValLong::create);
        valueMapper.map(AnnotationFields.UPDATED_BY_FIELD, ANNOTATION.UPDATE_USER, ValString::create);
        valueMapper.map(AnnotationFields.TITLE_FIELD, ANNOTATION.TITLE, ValString::create);
        valueMapper.map(AnnotationFields.SUBJECT_FIELD, ANNOTATION.SUBJECT, ValString::create);
        valueMapper.map(AnnotationFields.STATUS_FIELD, ANNOTATION.STATUS, ValString::create);
        valueMapper.map(AnnotationFields.ASSIGNED_TO_FIELD, ANNOTATION.ASSIGNED_TO, ValString::create);
        valueMapper.map(AnnotationFields.COMMENT_FIELD, ANNOTATION.COMMENT, ValString::create);
        valueMapper.map(AnnotationFields.HISTORY_FIELD, ANNOTATION.HISTORY, ValString::create);
    }

    private long getDate(final String fieldName, final String value) {
        try {
            final Optional<ZonedDateTime> optional = DateExpressionParser.parse(value, ZoneOffset.UTC.getId(), System.currentTimeMillis());

            return optional.orElseThrow(() -> new RuntimeException("Expected a standard date value for field \"" + fieldName
                    + "\" but was given string \"" + value + "\"")).toInstant().toEpochMilli();
        } catch (final Exception e) {
            throw new RuntimeException("Expected a standard date value for field \"" + fieldName
                    + "\" but was given string \"" + value + "\"", e);
        }
    }

    @Override
    public Annotation get(final long annotationId) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .select()
                .from(ANNOTATION)
                .where(ANNOTATION.ID.eq(annotationId))
                .fetchOptional()
                .map(RECORD_TO_ANNOTATION_MAPPER)
                .orElse(null));
    }

    private Annotation get(final Annotation annotation) {
        final Annotation result = get(annotation.getId());
        if (result == null) {
            return annotation;
        }
        return result;
    }

    @Override
    public AnnotationDetail getDetail(final long annotationId) {
        final Annotation annotation = get(annotationId);
        if (annotation == null) {
            return null;
        }
        return getDetail(annotation);
    }

    @Override
    public List<Annotation> getAnnotationsForEvents(final long streamId, final long eventId) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .select()
                .from(ANNOTATION)
                .join(ANNOTATION_DATA_LINK).on(ANNOTATION_DATA_LINK.FK_ANNOTATION_ID.eq(ANNOTATION.ID))
                .where(ANNOTATION_DATA_LINK.STREAM_ID.eq(streamId).and(ANNOTATION_DATA_LINK.EVENT_ID.eq(eventId)))
                .fetch()
                .stream()
                .map(RECORD_TO_ANNOTATION_MAPPER)
                .collect(Collectors.toList()));
    }

    @Override
    public List<AnnotationDetail> getAnnotationDetailsForEvents(final long streamId, final long eventId) {
        final List<Annotation> list = getAnnotationsForEvents(streamId, eventId);
        return list.stream().map(this::getDetail).collect(Collectors.toList());
    }

    private AnnotationDetail getDetail(final Annotation annotation) {
        final List<AnnotationEntry> entries = JooqUtil.contextResult(connectionProvider, context -> context
                .select()
                .from(ANNOTATION_ENTRY)
                .where(ANNOTATION_ENTRY.FK_ANNOTATION_ID.eq(annotation.getId()))
                .orderBy(ANNOTATION_ENTRY.ID)
                .fetch()
                .map(RECORD_TO_ANNOTATION_ENTRY_MAPPER::apply));

        return new AnnotationDetail(annotation, entries);
    }

    @Override
    public AnnotationDetail createEntry(final CreateEntryRequest request, final String user) {
        final long now = System.currentTimeMillis();

        // Create the parent annotation first if it hasn't been already.
        Annotation annotation = request.getAnnotation();
        if (annotation.getId() == null) {
            annotation = request.getAnnotation();
            annotation.setCreateTime(now);
            annotation.setCreateUser(user);
            annotation.setUpdateTime(now);
            annotation.setUpdateUser(user);
            annotation = create(annotation);

            // Create change entries for all fields so we know what their initial values were.
            createEntry(annotation.getId(), user, now, Annotation.TITLE, annotation.getTitle());
            createEntry(annotation.getId(), user, now, Annotation.SUBJECT, annotation.getSubject());
            createEntry(annotation.getId(), user, now, Annotation.STATUS, annotation.getStatus());
            createEntry(annotation.getId(), user, now, Annotation.ASSIGNED_TO, annotation.getAssignedTo());
            createEntry(annotation.getId(), user, now, Annotation.COMMENT, annotation.getComment());

            final long annotationId = annotation.getId();
            request.getLinkedEvents().forEach(eventID -> createEventLink(annotationId, eventID, user, now));
        } else {
            // Update parent if we need to.
            final long annotationId = annotation.getId();
            final Field<String> field = UPDATE_FIELD_MAP.get(request.getType());

            if (ANNOTATION.COMMENT.equals(field)) {
                JooqUtil.context(connectionProvider, context -> context
                        .update(ANNOTATION)
                        .set(ANNOTATION.COMMENT, request.getData())
                        .set(ANNOTATION.HISTORY, DSL
                                .when(ANNOTATION.HISTORY.isNull(), request.getData())
                                .otherwise(DSL.concat(ANNOTATION.HISTORY, "  |  " + request.getData())))
                        .set(ANNOTATION.UPDATE_USER, user)
                        .set(ANNOTATION.UPDATE_TIME_MS, now)
                        .where(ANNOTATION.ID.eq(annotationId))
                        .execute());

            } else if (field != null) {
                JooqUtil.context(connectionProvider, context -> context
                        .update(ANNOTATION)
                        .set(field, request.getData())
                        .set(ANNOTATION.UPDATE_USER, user)
                        .set(ANNOTATION.UPDATE_TIME_MS, now)
                        .where(ANNOTATION.ID.eq(annotationId))
                        .execute());
            } else {
                JooqUtil.context(connectionProvider, context -> context
                        .update(ANNOTATION)
                        .set(ANNOTATION.UPDATE_USER, user)
                        .set(ANNOTATION.UPDATE_TIME_MS, now)
                        .where(ANNOTATION.ID.eq(annotationId))
                        .execute());
            }

            // Create entry.
            createEntry(annotation.getId(), user, now, request.getType(), request.getData());
        }

        // Now select everything back to provide refreshed details.
        return getDetail(annotation.getId());
    }

    private void createEntry(final long annotationId, final String user, final long now, final String type, final String data) {
        // Create entry.
        final int count = JooqUtil.contextResult(connectionProvider, context -> context
                .insertInto(ANNOTATION_ENTRY,
                        ANNOTATION_ENTRY.VERSION,
                        ANNOTATION_ENTRY.CREATE_USER,
                        ANNOTATION_ENTRY.CREATE_TIME_MS,
                        ANNOTATION_ENTRY.UPDATE_USER,
                        ANNOTATION_ENTRY.UPDATE_TIME_MS,
                        ANNOTATION_ENTRY.FK_ANNOTATION_ID,
                        ANNOTATION_ENTRY.TYPE,
                        ANNOTATION_ENTRY.DATA)
                .values(1,
                        user,
                        now,
                        user,
                        now,
                        annotationId,
                        type,
                        data)
                .execute());

        if (count != 1) {
            throw new RuntimeException("Unable to create annotation entry");
        }
    }

    private Annotation create(final Annotation annotation) {
        final Optional<Long> optional = JooqUtil.contextResult(connectionProvider, context -> context
                .insertInto(ANNOTATION,
                        ANNOTATION.VERSION,
                        ANNOTATION.CREATE_USER,
                        ANNOTATION.CREATE_TIME_MS,
                        ANNOTATION.UPDATE_USER,
                        ANNOTATION.UPDATE_TIME_MS,
                        ANNOTATION.TITLE,
                        ANNOTATION.SUBJECT,
                        ANNOTATION.STATUS,
                        ANNOTATION.ASSIGNED_TO,
                        ANNOTATION.COMMENT,
                        ANNOTATION.HISTORY)
                .values(1,
                        annotation.getCreateUser(),
                        annotation.getCreateTime(),
                        annotation.getUpdateUser(),
                        annotation.getUpdateTime(),
                        annotation.getTitle(),
                        annotation.getSubject(),
                        annotation.getStatus(),
                        annotation.getAssignedTo(),
                        annotation.getComment(),
                        annotation.getHistory())
                .onDuplicateKeyIgnore()
                .returning(ANNOTATION.ID)
                .fetchOptional()
                .map(AnnotationRecord::getId));

        return optional.map(id -> {
            annotation.setId(id);
            annotation.setVersion(1);
            return annotation;
        }).orElse(get(annotation));
    }

    private void createEventLink(final long annotationId,
                                 final EventId eventId,
                                 final String user,
                                 final long now) {
        try {
            // Create event link.
            final int count = JooqUtil.contextResult(connectionProvider, context -> context
                    .insertInto(ANNOTATION_DATA_LINK,
                            ANNOTATION_DATA_LINK.FK_ANNOTATION_ID,
                            ANNOTATION_DATA_LINK.STREAM_ID,
                            ANNOTATION_DATA_LINK.EVENT_ID)
                    .values(annotationId,
                            eventId.getStreamId(),
                            eventId.getEventId())
                    .onDuplicateKeyIgnore()
                    .execute());

            if (count != 1) {
                throw new RuntimeException("Unable to create event link");
            }

            // Record this link.
            createEntry(annotationId, user, now, Annotation.LINK, eventId.toString());

        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
        }
    }

    private void removeEventLink(final long annotationId,
                                 final EventId eventId,
                                 final String user,
                                 final long now) {
        try {
            // Remove event link.
            final int count = JooqUtil.contextResult(connectionProvider, context -> context
                    .deleteFrom(ANNOTATION_DATA_LINK)
                    .where(ANNOTATION_DATA_LINK.FK_ANNOTATION_ID.eq(annotationId))
                    .and(ANNOTATION_DATA_LINK.STREAM_ID.eq(eventId.getStreamId()))
                    .and(ANNOTATION_DATA_LINK.EVENT_ID.eq(eventId.getEventId()))
                    .execute());

            if (count != 1) {
                throw new RuntimeException("Unable to remove event link");
            }

            // Record this link.
            createEntry(annotationId, user, now, Annotation.UNLINK, eventId.toString());

        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
        }
    }

    @Override
    public List<EventId> getLinkedEvents(final Long annotationId) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .select(ANNOTATION_DATA_LINK.STREAM_ID, ANNOTATION_DATA_LINK.EVENT_ID)
                .from(ANNOTATION_DATA_LINK)
                .where(ANNOTATION_DATA_LINK.FK_ANNOTATION_ID.eq(annotationId))
                .orderBy(ANNOTATION_DATA_LINK.STREAM_ID, ANNOTATION_DATA_LINK.EVENT_ID)
                .fetch()
                .map(r -> new EventId(r.get(ANNOTATION_DATA_LINK.STREAM_ID), r.get(ANNOTATION_DATA_LINK.EVENT_ID))));
    }

    @Override
    public List<EventId> link(final EventLink eventLink, final String user) {
        final long now = System.currentTimeMillis();
        createEventLink(eventLink.getAnnotationId(), eventLink.getEventId(), user, now);
        return getLinkedEvents(eventLink.getAnnotationId());
    }

    @Override
    public List<EventId> unlink(final EventLink eventLink, final String user) {
        final long now = System.currentTimeMillis();
        removeEventLink(eventLink.getAnnotationId(), eventLink.getEventId(), user, now);
        return getLinkedEvents(eventLink.getAnnotationId());
    }

    @Override
    public Integer setStatus(final SetStatusRequest request, final String user) {
        return changeFields(
                request.getAnnotationIdList(),
                user,
                Annotation.STATUS,
                ANNOTATION.STATUS,
                request.getStatus());
    }

    @Override
    public Integer setAssignedTo(final SetAssignedToRequest request, final String user) {
        return changeFields(
                request.getAnnotationIdList(),
                user,
                Annotation.ASSIGNED_TO,
                ANNOTATION.ASSIGNED_TO,
                request.getAssignedTo());
    }

    private Integer changeFields(final List<Long> annotationIdList,
                                 final String user,
                                 final String type,
                                 final Field<String> field,
                                 final String value) {
        final long now = System.currentTimeMillis();
        int count = 0;
        for (final Long annotationId : annotationIdList) {
            try {
                changeField(annotationId, now, user, type, field, value);
                count++;
            } catch (final RuntimeException e) {
                LOGGER.debug(e::getMessage, e);
            }
        }
        return count;
    }

    private void changeField(final long annotationId,
                             final long now,
                             final String user,
                             final String type,
                             final Field<String> field,
                             final String value) {
        JooqUtil.context(connectionProvider, context -> context
                .update(ANNOTATION)
                .set(field, value)
                .set(ANNOTATION.UPDATE_USER, user)
                .set(ANNOTATION.UPDATE_TIME_MS, now)
                .where(ANNOTATION.ID.eq(annotationId))
                .execute());
        createEntry(annotationId, user, now, type, value);
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final AbstractField[] fields,
                       final Consumer<Val[]> consumer) {
        final List<AbstractField> fieldList = Arrays.asList(fields);

        final PageRequest pageRequest = criteria.getPageRequest();
        final Collection<Condition> conditions = createCondition(criteria.getExpression());
        final Collection<OrderField<?>> orderFields = createOrderFields(criteria);
        final List<Field<?>> dbFields = new ArrayList<>(valueMapper.getFields(fieldList));
        final Mapper<?>[] mappers = valueMapper.getMappers(fields);

        JooqUtil.context(connectionProvider, context -> {
            int offset = 0;
            int numberOfRows = 1000000;

            if (pageRequest != null) {
                offset = pageRequest.getOffset().intValue();
                numberOfRows = pageRequest.getLength();
            }

            SelectJoinStep<?> select = context.select(dbFields).from(ANNOTATION);
            try (final Cursor<?> cursor = select
                    .where(conditions)
                    .orderBy(orderFields)
                    .limit(offset, numberOfRows)
                    .fetchLazy()) {

                while (cursor.hasNext()) {
                    final Result<?> result = cursor.fetchNext(1000);
                    result.forEach(r -> {
                        final Val[] arr = new Val[fields.length];
                        for (int i = 0; i < fields.length; i++) {
                            Val val = ValNull.INSTANCE;
                            final Mapper<?> mapper = mappers[i];
                            if (mapper != null) {
                                val = mapper.map(r);
                            }
                            arr[i] = val;
                        }
                        consumer.accept(arr);
                    });
                }
            }
        });
    }

    private Collection<Condition> createCondition(final ExpressionOperator expression) {
        return expressionMapper.apply(expression);
    }

    private Collection<OrderField<?>> createOrderFields(final ExpressionCriteria criteria) {
        if (criteria.getSortList() == null || criteria.getSortList().size() == 0) {
            return Collections.singleton(ANNOTATION.ID);
        }

        return criteria.getSortList().stream().map(sort -> {
            Field<?> field;
            if (AnnotationFields.CREATED_ON.equals(sort.getField())) {
                field = ANNOTATION.CREATE_TIME_MS;
            } else if (AnnotationFields.CREATED_BY.equals(sort.getField())) {
                field = ANNOTATION.CREATE_USER;
            } else if (AnnotationFields.UPDATED_ON.equals(sort.getField())) {
                field = ANNOTATION.UPDATE_TIME_MS;
            } else if (AnnotationFields.UPDATED_BY.equals(sort.getField())) {
                field = ANNOTATION.UPDATE_USER;
            } else if (AnnotationFields.TITLE.equals(sort.getField())) {
                field = ANNOTATION.TITLE;
            } else if (AnnotationFields.SUBJECT.equals(sort.getField())) {
                field = ANNOTATION.SUBJECT;
            } else if (AnnotationFields.STATUS.equals(sort.getField())) {
                field = ANNOTATION.STATUS;
            } else if (AnnotationFields.ASSIGNED_TO.equals(sort.getField())) {
                field = ANNOTATION.ASSIGNED_TO;
            } else if (AnnotationFields.COMMENT.equals(sort.getField())) {
                field = ANNOTATION.COMMENT;
            } else if (AnnotationFields.HISTORY.equals(sort.getField())) {
                field = ANNOTATION.HISTORY;
            } else {
                field = ANNOTATION.ID;
            }

            OrderField<?> orderField = field;
            if (Sort.Direction.DESCENDING.equals(sort.getDirection())) {
                orderField = field.desc();
            }

            return orderField;
        }).collect(Collectors.toList());
    }
}
