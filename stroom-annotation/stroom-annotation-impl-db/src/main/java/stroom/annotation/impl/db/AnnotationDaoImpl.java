/*
 * Copyright 2024 Crown Copyright
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

package stroom.annotation.impl.db;

import stroom.annotation.impl.AnnotationConfig;
import stroom.annotation.impl.AnnotationDao;
import stroom.annotation.impl.db.jooq.tables.records.AnnotationRecord;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationDecorationFields;
import stroom.annotation.shared.AnnotationDetail;
import stroom.annotation.shared.AnnotationEntry;
import stroom.annotation.shared.AnnotationFields;
import stroom.annotation.shared.CreateAnnotationRequest;
import stroom.annotation.shared.CreateEntryRequest;
import stroom.annotation.shared.EntryValue;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.EventLink;
import stroom.annotation.shared.SetAssignedToRequest;
import stroom.annotation.shared.SetDescriptionRequest;
import stroom.annotation.shared.SetStatusRequest;
import stroom.annotation.shared.StringEntryValue;
import stroom.annotation.shared.UserRefEntryValue;
import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.JooqUtil;
import stroom.db.util.ValueMapper;
import stroom.db.util.ValueMapper.Mapper;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.security.user.api.UserRefLookup;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Cursor;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static stroom.annotation.impl.db.jooq.tables.Annotation.ANNOTATION;
import static stroom.annotation.impl.db.jooq.tables.AnnotationDataLink.ANNOTATION_DATA_LINK;
import static stroom.annotation.impl.db.jooq.tables.AnnotationEntry.ANNOTATION_ENTRY;

// Make this a singleton so we don't keep recreating the mappers.
@Singleton
class AnnotationDaoImpl implements AnnotationDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnnotationDaoImpl.class);

    private static final Map<String, Field<String>> UPDATE_FIELD_MAP = Map.of(
            Annotation.TITLE, ANNOTATION.TITLE,
            Annotation.SUBJECT, ANNOTATION.SUBJECT,
            Annotation.STATUS, ANNOTATION.STATUS,
            Annotation.ASSIGNED_TO, ANNOTATION.ASSIGNED_TO_UUID,
            Annotation.COMMENT, ANNOTATION.COMMENT);

    private final AnnotationDbConnProvider connectionProvider;
    private final ExpressionMapper expressionMapper;
    private final ValueMapper valueMapper;
    private final UserRefLookup userRefLookup;
    private final Provider<AnnotationConfig> annotationConfigProvider;

    @Inject
    AnnotationDaoImpl(final AnnotationDbConnProvider connectionProvider,
                      final ExpressionMapperFactory expressionMapperFactory,
                      final UserRefLookup userRefLookup,
                      final Provider<AnnotationConfig> annotationConfigProvider) {
        this.connectionProvider = connectionProvider;
        this.userRefLookup = userRefLookup;
        this.expressionMapper = createExpressionMapper(expressionMapperFactory, userRefLookup);
        this.valueMapper = createValueMapper();
        this.annotationConfigProvider = annotationConfigProvider;
    }

    private ExpressionMapper createExpressionMapper(final ExpressionMapperFactory expressionMapperFactory,
                                                    final UserRefLookup userRefLookup) {
        final ExpressionMapper expressionMapper = expressionMapperFactory.create();

        // Decoration
        expressionMapper.map(AnnotationDecorationFields.ANNOTATION_ID_FIELD, ANNOTATION.ID, Long::valueOf);
        expressionMapper.map(AnnotationDecorationFields.ANNOTATION_UUID_FIELD, ANNOTATION.UUID, value -> value);
        expressionMapper.map(AnnotationDecorationFields.ANNOTATION_CREATED_ON_FIELD,
                ANNOTATION.CREATE_TIME_MS,
                value -> DateExpressionParser.getMs(AnnotationDecorationFields.ANNOTATION_CREATED_ON, value));
        expressionMapper.map(AnnotationDecorationFields.ANNOTATION_CREATED_BY_FIELD,
                ANNOTATION.CREATE_USER,
                value -> value);
        expressionMapper.map(AnnotationDecorationFields.ANNOTATION_UPDATED_ON_FIELD,
                ANNOTATION.UPDATE_TIME_MS,
                value -> DateExpressionParser.getMs(AnnotationDecorationFields.ANNOTATION_UPDATED_ON, value));
        expressionMapper.map(AnnotationDecorationFields.ANNOTATION_UPDATED_BY_FIELD,
                ANNOTATION.UPDATE_USER,
                value -> value);
        expressionMapper.map(AnnotationDecorationFields.ANNOTATION_TITLE_FIELD, ANNOTATION.TITLE, value -> value);
        expressionMapper.map(AnnotationDecorationFields.ANNOTATION_SUBJECT_FIELD, ANNOTATION.SUBJECT, value -> value);
        expressionMapper.map(AnnotationDecorationFields.ANNOTATION_STATUS_FIELD, ANNOTATION.STATUS, value -> value);
        expressionMapper.map(AnnotationDecorationFields.ANNOTATION_ASSIGNED_TO_FIELD,
                ANNOTATION.ASSIGNED_TO_UUID,
                uuid ->
                        userRefLookup.getByUuid(uuid)
                                .map(UserRef::getUuid)
                                .orElse(null));
        expressionMapper.map(AnnotationDecorationFields.ANNOTATION_COMMENT_FIELD, ANNOTATION.COMMENT, value -> value);
        expressionMapper.map(AnnotationDecorationFields.ANNOTATION_HISTORY_FIELD, ANNOTATION.HISTORY, value -> value);
        expressionMapper.map(AnnotationDecorationFields.ANNOTATION_DESCRIPTION_FIELD,
                ANNOTATION.DESCRIPTION,
                value -> value);

        // Direct fields
        expressionMapper.map(AnnotationFields.ID_FIELD, ANNOTATION.ID, Long::valueOf);
        expressionMapper.map(AnnotationFields.UUID_FIELD, ANNOTATION.UUID, value -> value);
        expressionMapper.map(AnnotationFields.CREATED_ON_FIELD,
                ANNOTATION.CREATE_TIME_MS,
                value -> DateExpressionParser.getMs(AnnotationFields.CREATED_ON, value));
        expressionMapper.map(AnnotationFields.CREATED_BY_FIELD, ANNOTATION.CREATE_USER, value -> value);
        expressionMapper.map(AnnotationFields.UPDATED_ON_FIELD,
                ANNOTATION.UPDATE_TIME_MS,
                value -> DateExpressionParser.getMs(AnnotationFields.UPDATED_ON, value));
        expressionMapper.map(AnnotationFields.UPDATED_BY_FIELD, ANNOTATION.UPDATE_USER, value -> value);
        expressionMapper.map(AnnotationFields.TITLE_FIELD, ANNOTATION.TITLE, value -> value);
        expressionMapper.map(AnnotationFields.SUBJECT_FIELD, ANNOTATION.SUBJECT, value -> value);
        expressionMapper.map(AnnotationFields.STATUS_FIELD, ANNOTATION.STATUS, value -> value);
        expressionMapper.map(AnnotationFields.ASSIGNED_TO_FIELD, ANNOTATION.ASSIGNED_TO_UUID, uuid ->
                userRefLookup.getByUuid(uuid)
                        .map(UserRef::getUuid)
                        .orElse(null));
        expressionMapper.map(AnnotationFields.COMMENT_FIELD, ANNOTATION.COMMENT, value -> value);
        expressionMapper.map(AnnotationFields.HISTORY_FIELD, ANNOTATION.HISTORY, value -> value);
        expressionMapper.map(AnnotationFields.DESCRIPTION_FIELD, ANNOTATION.DESCRIPTION, value -> value);
        expressionMapper.map(AnnotationFields.STREAM_ID_FIELD, ANNOTATION_DATA_LINK.STREAM_ID, Long::valueOf);
        expressionMapper.map(AnnotationFields.EVENT_ID_FIELD, ANNOTATION_DATA_LINK.EVENT_ID, Long::valueOf);

        return expressionMapper;
    }

    private ValueMapper createValueMapper() {
        final ValueMapper valueMapper = new ValueMapper();

        // Decoration
        valueMapper.map(AnnotationDecorationFields.ANNOTATION_ID_FIELD, ANNOTATION.ID, ValLong::create);
        valueMapper.map(AnnotationDecorationFields.ANNOTATION_UUID_FIELD, ANNOTATION.UUID, ValString::create);
        valueMapper.map(AnnotationDecorationFields.ANNOTATION_CREATED_ON_FIELD,
                ANNOTATION.CREATE_TIME_MS,
                ValDate::create);
        valueMapper.map(AnnotationDecorationFields.ANNOTATION_CREATED_BY_FIELD,
                ANNOTATION.CREATE_USER,
                ValString::create);
        valueMapper.map(AnnotationDecorationFields.ANNOTATION_UPDATED_ON_FIELD,
                ANNOTATION.UPDATE_TIME_MS,
                ValDate::create);
        valueMapper.map(AnnotationDecorationFields.ANNOTATION_UPDATED_BY_FIELD,
                ANNOTATION.UPDATE_USER,
                ValString::create);
        valueMapper.map(AnnotationDecorationFields.ANNOTATION_TITLE_FIELD, ANNOTATION.TITLE, ValString::create);
        valueMapper.map(AnnotationDecorationFields.ANNOTATION_SUBJECT_FIELD, ANNOTATION.SUBJECT, ValString::create);
        valueMapper.map(AnnotationDecorationFields.ANNOTATION_STATUS_FIELD, ANNOTATION.STATUS, ValString::create);
        valueMapper.map(AnnotationDecorationFields.ANNOTATION_ASSIGNED_TO_FIELD,
                ANNOTATION.ASSIGNED_TO_UUID,
                this::mapUserUuidToValString);
        valueMapper.map(AnnotationDecorationFields.ANNOTATION_COMMENT_FIELD, ANNOTATION.COMMENT, ValString::create);
        valueMapper.map(AnnotationDecorationFields.ANNOTATION_HISTORY_FIELD, ANNOTATION.HISTORY, ValString::create);
        valueMapper.map(AnnotationDecorationFields.ANNOTATION_DESCRIPTION_FIELD,
                ANNOTATION.DESCRIPTION,
                ValString::create);

        // Direct fields
        valueMapper.map(AnnotationFields.ID_FIELD, ANNOTATION.ID, ValLong::create);
        valueMapper.map(AnnotationFields.UUID_FIELD, ANNOTATION.UUID, ValString::create);
        valueMapper.map(AnnotationFields.CREATED_ON_FIELD, ANNOTATION.CREATE_TIME_MS, ValDate::create);
        valueMapper.map(AnnotationFields.CREATED_BY_FIELD, ANNOTATION.CREATE_USER, ValString::create);
        valueMapper.map(AnnotationFields.UPDATED_ON_FIELD, ANNOTATION.UPDATE_TIME_MS, ValDate::create);
        valueMapper.map(AnnotationFields.UPDATED_BY_FIELD, ANNOTATION.UPDATE_USER, ValString::create);
        valueMapper.map(AnnotationFields.TITLE_FIELD, ANNOTATION.TITLE, ValString::create);
        valueMapper.map(AnnotationFields.SUBJECT_FIELD, ANNOTATION.SUBJECT, ValString::create);
        valueMapper.map(AnnotationFields.STATUS_FIELD, ANNOTATION.STATUS, ValString::create);
        valueMapper.map(AnnotationFields.ASSIGNED_TO_FIELD, ANNOTATION.ASSIGNED_TO_UUID, this::mapUserUuidToValString);
        valueMapper.map(AnnotationFields.COMMENT_FIELD, ANNOTATION.COMMENT, ValString::create);
        valueMapper.map(AnnotationFields.HISTORY_FIELD, ANNOTATION.HISTORY, ValString::create);
        valueMapper.map(AnnotationFields.DESCRIPTION_FIELD, ANNOTATION.DESCRIPTION, ValString::create);
        valueMapper.map(AnnotationFields.STREAM_ID_FIELD, ANNOTATION_DATA_LINK.STREAM_ID, ValLong::create);
        valueMapper.map(AnnotationFields.EVENT_ID_FIELD, ANNOTATION_DATA_LINK.EVENT_ID, ValLong::create);

        return valueMapper;
    }

    @Override
    public Annotation get(final long annotationId) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                        .select()
                        .from(ANNOTATION)
                        .where(ANNOTATION.ID.eq(annotationId))
                        .fetchOptional())
                .map(this::mapToAnnotation)
                .orElse(null);
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
                        .where(ANNOTATION_DATA_LINK.STREAM_ID.eq(streamId)
                                .and(ANNOTATION_DATA_LINK.EVENT_ID.eq(eventId)))
                        .fetch())
                .map(this::mapToAnnotation);
    }

    private AnnotationEntry mapToAnnotationEntry(final Record record) {
        final AnnotationEntry entry = new AnnotationEntry();
        entry.setId(record.get(ANNOTATION_ENTRY.ID));
        entry.setEntryTime(record.get(ANNOTATION_ENTRY.ENTRY_TIME_MS));
        entry.setEntryUser(getUserRef(record.get(ANNOTATION_ENTRY.ENTRY_USER_UUID)));

        final String type = record.get(ANNOTATION_ENTRY.TYPE);
        entry.setEntryType(type);
        final String data = record.get(ANNOTATION_ENTRY.DATA);
        if (data != null) {
            final EntryValue entryValue = Annotation.ASSIGNED_TO.equals(type)
                    ? UserRefEntryValue.of(getUserRef(data))
                    : StringEntryValue.of(data);
            entry.setEntryValue(entryValue);
        } else {
            entry.setEntryValue(null);
        }
        return entry;
    }

    private UserRef getUserRef(final String uuid) {
        if (uuid == null) {
            return null;
        }
        return userRefLookup.getByUuid(uuid)
                .orElse(UserRef.builder()
                        .uuid(uuid)
                        .build());
    }

    private Annotation mapToAnnotation(final Record record) {
        final Annotation annotation = new Annotation();
        annotation.setType(Annotation.TYPE);
        annotation.setUuid(record.get(ANNOTATION.UUID));
        annotation.setName(record.get(ANNOTATION.TITLE));
        annotation.setVersion("" + record.get(ANNOTATION.VERSION));
        annotation.setCreateTimeMs(record.get(ANNOTATION.CREATE_TIME_MS));
        annotation.setCreateUser(record.get(ANNOTATION.CREATE_USER));
        annotation.setUpdateTimeMs(record.get(ANNOTATION.UPDATE_TIME_MS));
        annotation.setUpdateUser(record.get(ANNOTATION.UPDATE_USER));
        annotation.setId(record.get(ANNOTATION.ID));
        annotation.setSubject(record.get(ANNOTATION.SUBJECT));
        annotation.setStatus(record.get(ANNOTATION.STATUS));
        annotation.setAssignedTo(getUserRef(record.get(ANNOTATION.ASSIGNED_TO_UUID)));
        annotation.setComment(record.get(ANNOTATION.COMMENT));
        annotation.setHistory(record.get(ANNOTATION.HISTORY));
        annotation.setDescription(record.get(ANNOTATION.DESCRIPTION));
        return annotation;
    }

    private AnnotationDetail getDetail(final Annotation annotation) {
        final List<AnnotationEntry> entries = JooqUtil.contextResult(connectionProvider, context -> context
                        .select()
                        .from(ANNOTATION_ENTRY)
                        .where(ANNOTATION_ENTRY.FK_ANNOTATION_ID.eq(annotation.getId()))
                        .orderBy(ANNOTATION_ENTRY.ID)
                        .fetch())
                .map(this::mapToAnnotationEntry);

        return new AnnotationDetail(annotation, entries);
    }

    @Override
    public AnnotationDetail createAnnotation(final CreateAnnotationRequest request,
                                             final UserRef currentUser) {
        final Instant now = Instant.now();
        final long nowMs = now.toEpochMilli();
        Annotation annotation = request.getAnnotation();
        annotation.setId(null);
        annotation.setType(Annotation.TYPE);
        if (annotation.getUuid() == null) {
            annotation.setUuid(UUID.randomUUID().toString());
        }
        if (annotation.getStatus() == null) {
            final AnnotationConfig annotationConfig = annotationConfigProvider.get();
            final List<String> statusValues = annotationConfig.getStatusValues();
            if (statusValues == null || statusValues.isEmpty()) {
                throw new RuntimeException("No default status value");
            }
            annotation.setStatus(statusValues.getFirst());
        }

        annotation.setCreateTimeMs(nowMs);
        annotation.setCreateUser(currentUser.toDisplayString());
        annotation.setUpdateTimeMs(nowMs);
        annotation.setUpdateUser(currentUser.toDisplayString());
        annotation = create(annotation);

        // Create default entries.
        createEntry(annotation.getId(), currentUser, now, Annotation.STATUS, annotation.getStatus());
        createEntry(
                annotation.getId(),
                currentUser,
                now,
                Annotation.ASSIGNED_TO,
                NullSafe.get(annotation.getAssignedTo(), UserRef::getUuid));

        final long annotationId = annotation.getId();
        request.getLinkedEvents().forEach(eventID ->
                createEventLink(now, currentUser, annotationId, eventID));

        // Now select everything back to provide refreshed details.
        return getDetail(annotation.getId());
    }

    @Override
    public AnnotationDetail createEntry(final CreateEntryRequest request, final UserRef currentUser) {
        final Instant now = Instant.now();
        final long nowMs = now.toEpochMilli();

        // Create the parent annotation first if it hasn't been already.
        Annotation annotation = request.getAnnotation();
        if (annotation.getId() == null) {
            throw new RuntimeException("Unable to create entry for unknown annotation");
        }

        // Update parent if we need to.
        final long annotationId = annotation.getId();
        final Field<String> field = UPDATE_FIELD_MAP.get(request.getType());
        final String fieldValue = request.getEntryValue().asPersistedValue();

        if (ANNOTATION.COMMENT.equals(field)) {
            JooqUtil.context(connectionProvider, context -> context
                    .update(ANNOTATION)
                    .set(ANNOTATION.COMMENT, fieldValue)
                    .set(ANNOTATION.HISTORY, DSL
                            .when(ANNOTATION.HISTORY.isNull(), fieldValue)
                            .otherwise(DSL.concat(ANNOTATION.HISTORY, "  |  " + fieldValue)))
                    .set(ANNOTATION.UPDATE_USER, currentUser.toDisplayString())
                    .set(ANNOTATION.UPDATE_TIME_MS, nowMs)
                    .where(ANNOTATION.ID.eq(annotationId))
                    .execute());

        } else if (field != null) {
            JooqUtil.context(connectionProvider, context -> context
                    .update(ANNOTATION)
                    .set(field, fieldValue)
                    .set(ANNOTATION.UPDATE_USER, currentUser.toDisplayString())
                    .set(ANNOTATION.UPDATE_TIME_MS, nowMs)
                    .where(ANNOTATION.ID.eq(annotationId))
                    .execute());
        } else {
            JooqUtil.context(connectionProvider, context -> context
                    .update(ANNOTATION)
                    .set(ANNOTATION.UPDATE_USER, currentUser.toDisplayString())
                    .set(ANNOTATION.UPDATE_TIME_MS, nowMs)
                    .where(ANNOTATION.ID.eq(annotationId))
                    .execute());
        }

        // Create entry.
        createEntry(annotation.getId(), currentUser, now, request.getType(), fieldValue);

        // Now select everything back to provide refreshed details.
        return getDetail(annotation.getId());
    }

    private void createEntry(final long annotationId,
                             final UserRef currentUser,
                             final Instant now,
                             final String type,
                             final String fieldValue) {
        // Create entry.
        final int count = JooqUtil.contextResult(connectionProvider, context -> context
                .insertInto(ANNOTATION_ENTRY,
                        ANNOTATION_ENTRY.ENTRY_TIME_MS,
                        ANNOTATION_ENTRY.ENTRY_USER_UUID,
                        ANNOTATION_ENTRY.FK_ANNOTATION_ID,
                        ANNOTATION_ENTRY.TYPE,
                        ANNOTATION_ENTRY.DATA)
                .values(now.toEpochMilli(),
                        currentUser.getUuid(),
                        annotationId,
                        type,
                        fieldValue)
                .execute());

        if (count != 1) {
            throw new RuntimeException("Unable to create annotation entry");
        }
    }

    private Annotation create(final Annotation annotation) {
        final Optional<Long> optional = JooqUtil.contextResult(connectionProvider, context -> context
                        .insertInto(ANNOTATION,
                                ANNOTATION.UUID,
                                ANNOTATION.VERSION,
                                ANNOTATION.CREATE_USER,
                                ANNOTATION.CREATE_TIME_MS,
                                ANNOTATION.UPDATE_USER,
                                ANNOTATION.UPDATE_TIME_MS,
                                ANNOTATION.TITLE,
                                ANNOTATION.SUBJECT,
                                ANNOTATION.STATUS,
                                ANNOTATION.ASSIGNED_TO_UUID,
                                ANNOTATION.COMMENT,
                                ANNOTATION.HISTORY,
                                ANNOTATION.DESCRIPTION)
                        .values(
                                annotation.getUuid(),
                                1,
                                annotation.getCreateUser(),
                                annotation.getCreateTimeMs(),
                                annotation.getUpdateUser(),
                                annotation.getUpdateTimeMs(),
                                annotation.getName(),
                                annotation.getSubject(),
                                annotation.getStatus(),
                                mapUserNameToUserUuid(annotation.getAssignedTo()),
                                annotation.getComment(),
                                annotation.getHistory(),
                                annotation.getDescription())
                        .returning(ANNOTATION.ID)
                        .fetchOptional())
                .map(AnnotationRecord::getId);

        return optional.map(id -> {
            annotation.setId(id);
            annotation.setVersion(UUID.randomUUID().toString());
            return annotation;
        }).orElse(get(annotation));
    }

    private void createEventLink(final Instant now,
                                 final UserRef currentUser,
                                 final long annotationId,
                                 final EventId eventId) {
        try {
            // Create event link.
            try {
                JooqUtil.onDuplicateKeyIgnore(() ->
                        JooqUtil.context(connectionProvider, context -> context
                                .insertInto(ANNOTATION_DATA_LINK,
                                        ANNOTATION_DATA_LINK.FK_ANNOTATION_ID,
                                        ANNOTATION_DATA_LINK.STREAM_ID,
                                        ANNOTATION_DATA_LINK.EVENT_ID)
                                .values(annotationId,
                                        eventId.getStreamId(),
                                        eventId.getEventId())
                                .execute()));
            } catch (final Exception e) {
                throw new RuntimeException("Unable to create event link", e);
            }

            // Record this link.
            createEntry(annotationId, currentUser, now, Annotation.LINK, eventId.toString());

        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
        }
    }

    private void removeEventLink(final Instant now,
                                 final UserRef currentUser,
                                 final long annotationId,
                                 final EventId eventId) {
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
            createEntry(annotationId, currentUser, now, Annotation.UNLINK, eventId.toString());

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
                        .fetch())
                .map(r -> new EventId(r.get(ANNOTATION_DATA_LINK.STREAM_ID), r.get(ANNOTATION_DATA_LINK.EVENT_ID)));
    }

    @Override
    public List<EventId> link(final UserRef currentUser,
                              final EventLink eventLink) {
        final Instant now = Instant.now();
        createEventLink(now, currentUser, eventLink.getAnnotationId(), eventLink.getEventId());
        return getLinkedEvents(eventLink.getAnnotationId());
    }

    @Override
    public List<EventId> unlink(final EventLink eventLink, final UserRef currentUser) {
        final Instant now = Instant.now();
        removeEventLink(now, currentUser, eventLink.getAnnotationId(), eventLink.getEventId());
        return getLinkedEvents(eventLink.getAnnotationId());
    }

    @Override
    public Integer setStatus(final SetStatusRequest request, final UserRef currentUser) {
        return changeFields(
                request.getAnnotationIdList(),
                currentUser,
                Annotation.STATUS,
                ANNOTATION.STATUS,
                request.getStatus());
    }

    @Override
    public Integer setAssignedTo(final SetAssignedToRequest request, final UserRef currentUser) {
        return changeFields(
                request.getAnnotationIdList(),
                currentUser,
                Annotation.ASSIGNED_TO,
                ANNOTATION.ASSIGNED_TO_UUID,
                NullSafe.get(request.getAssignedTo(), UserRef::getUuid));
    }

    @Override
    public Integer setDescription(final SetDescriptionRequest request) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                .update(ANNOTATION)
                .set(ANNOTATION.DESCRIPTION, request.getDescription())
                .where(ANNOTATION.ID.eq(request.getAnnotationId()))
                .execute());
    }

    private Integer changeFields(final List<Long> annotationIdList,
                                 final UserRef currentUser,
                                 final String type,
                                 final Field<String> field,
                                 final String value) {
        final Instant now = Instant.now();
        int count = 0;
        for (final Long annotationId : annotationIdList) {
            try {
                changeField(annotationId, now, currentUser, type, field, value);
                count++;
            } catch (final RuntimeException e) {
                LOGGER.debug(e::getMessage, e);
            }
        }
        return count;
    }

    private void changeField(final long annotationId,
                             final Instant now,
                             final UserRef currentUser,
                             final String type,
                             final Field<String> field,
                             final String value) {
        JooqUtil.context(connectionProvider, context -> context
                .update(ANNOTATION)
                .set(field, value)
                .set(ANNOTATION.UPDATE_USER, currentUser.toDisplayString())
                .set(ANNOTATION.UPDATE_TIME_MS, now.toEpochMilli())
                .where(ANNOTATION.ID.eq(annotationId))
                .execute());
        createEntry(annotationId, currentUser, now, type, value);
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final ValuesConsumer consumer,
                       final Predicate<String> uuidPredicate) {
        final String[] fieldNames = fieldIndex.getFields();
        final Condition condition = createCondition(criteria.getExpression());
        final Set<Field<?>> dbFields = new HashSet<>(valueMapper.getDbFieldsByName(fieldNames));
        final Mapper<?>[] mappers = valueMapper.getMappersForFieldNames(fieldNames);
        dbFields.add(ANNOTATION.UUID);

        JooqUtil.context(connectionProvider, context -> {
            SelectJoinStep<?> select = context.select(dbFields)
                    .from(ANNOTATION);

            if (dbFields.contains(ANNOTATION_DATA_LINK.STREAM_ID) ||
                dbFields.contains(ANNOTATION_DATA_LINK.EVENT_ID)) {
                select = select
                        .leftOuterJoin(ANNOTATION_DATA_LINK)
                        .on(ANNOTATION_DATA_LINK.FK_ANNOTATION_ID.eq(ANNOTATION.ID));
            }

            try (final Cursor<?> cursor = select
                    .where(condition)
                    .fetchLazy()) {

                while (cursor.hasNext()) {
                    final Result<?> result = cursor.fetchNext(1000);
                    result.forEach(r -> {
                        final String uuid = r.get(ANNOTATION.UUID);
                        if (uuidPredicate.test(uuid)) {
                            final Val[] arr = new Val[fieldNames.length];
                            for (int i = 0; i < fieldNames.length; i++) {
                                Val val = ValNull.INSTANCE;
                                final Mapper<?> mapper = mappers[i];
                                if (mapper != null) {
                                    val = mapper.map(r);
                                }
                                arr[i] = val;
                            }
                            consumer.accept(Val.of(arr));
                        }
                    });
                }
            }
        });
    }

    @Override
    public List<Annotation> fetchByAssignedUser(final String userUuid) {
        Objects.requireNonNull(userUuid);
        return JooqUtil.contextResult(connectionProvider, context -> context
                        .select()
                        .from(ANNOTATION)
                        .where(ANNOTATION.ASSIGNED_TO_UUID.eq(userUuid))
                        .fetch())
                .map(this::mapToAnnotation);
    }

    private Val mapUserUuidToValString(final String userUuid) {
        if (userUuid == null) {
            return ValNull.INSTANCE;
        } else if (NullSafe.isBlankString(userUuid)) {
            return ValString.create(userUuid);
        } else {
            return NullSafe.getAsOptional(
                            userUuid,
                            this::getUserRef,
                            UserRef::toDisplayString,
                            value -> (Val) ValString.create(value))
                    .orElse(ValNull.INSTANCE);
        }
    }

    private String mapUserNameToUserUuid(final UserRef userRef) {
        if (userRef == null) {
            return null;
        } else {
            return userRef.getUuid();
        }
    }

    private Condition createCondition(final ExpressionOperator expression) {
        return expressionMapper.apply(expression);
    }
}
