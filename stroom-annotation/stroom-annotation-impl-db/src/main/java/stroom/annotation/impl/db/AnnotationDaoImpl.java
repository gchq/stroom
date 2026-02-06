/*
 * Copyright 2016-2026 Crown Copyright
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
import stroom.annotation.impl.db.jooq.tables.records.AnnotationDataLinkRecord;
import stroom.annotation.impl.db.jooq.tables.records.AnnotationEntryRecord;
import stroom.annotation.impl.db.jooq.tables.records.AnnotationLinkRecord;
import stroom.annotation.impl.db.jooq.tables.records.AnnotationRecord;
import stroom.annotation.shared.AbstractAnnotationChange;
import stroom.annotation.shared.AddAnnotationTable;
import stroom.annotation.shared.AddTag;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationDecorationFields;
import stroom.annotation.shared.AnnotationEntry;
import stroom.annotation.shared.AnnotationEntryType;
import stroom.annotation.shared.AnnotationFields;
import stroom.annotation.shared.AnnotationTable;
import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.AnnotationTagType;
import stroom.annotation.shared.ChangeAssignedTo;
import stroom.annotation.shared.ChangeComment;
import stroom.annotation.shared.ChangeDescription;
import stroom.annotation.shared.ChangeRetentionPeriod;
import stroom.annotation.shared.ChangeSubject;
import stroom.annotation.shared.ChangeTitle;
import stroom.annotation.shared.CreateAnnotationRequest;
import stroom.annotation.shared.EntryValue;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.FindAnnotationRequest;
import stroom.annotation.shared.LinkAnnotations;
import stroom.annotation.shared.LinkEvents;
import stroom.annotation.shared.RemoveTag;
import stroom.annotation.shared.SetTag;
import stroom.annotation.shared.SingleAnnotationChangeRequest;
import stroom.annotation.shared.StringEntryValue;
import stroom.annotation.shared.UnlinkAnnotations;
import stroom.annotation.shared.UnlinkEvents;
import stroom.annotation.shared.UserRefEntryValue;
import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapper.MultiConverter;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.JooqUtil;
import stroom.db.util.TermHandler;
import stroom.db.util.TermHandlerFactory;
import stroom.db.util.ValueMapper;
import stroom.db.util.ValueMapper.Mapper;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.api.StreamFeedProvider;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionUtil;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.common.v2.FieldProviderImpl;
import stroom.query.common.v2.SimpleStringExpressionParser;
import stroom.query.common.v2.SimpleStringExpressionParser.FieldProvider;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.security.shared.FindUserContext;
import stroom.security.user.api.UserRefLookup;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.util.time.SimpleDurationUtil;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jooq.Condition;
import org.jooq.Cursor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertValuesStep2;
import org.jooq.InsertValuesStep4;
import org.jooq.InsertValuesStep7;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SelectJoinStep;
import org.jooq.impl.DSL;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static stroom.annotation.impl.db.jooq.tables.Annotation.ANNOTATION;
import static stroom.annotation.impl.db.jooq.tables.AnnotationDataLink.ANNOTATION_DATA_LINK;
import static stroom.annotation.impl.db.jooq.tables.AnnotationEntry.ANNOTATION_ENTRY;
import static stroom.annotation.impl.db.jooq.tables.AnnotationLink.ANNOTATION_LINK;
import static stroom.annotation.impl.db.jooq.tables.AnnotationTag.ANNOTATION_TAG;
import static stroom.annotation.impl.db.jooq.tables.AnnotationTagLink.ANNOTATION_TAG_LINK;

/**
 * Implementation note: This DAO uses two different query strategies:
 * <p>
 * 1. Single-query approach (getAnnotationById, getAnnotationByDocRef, etc.):
 * Uses multiset and subqueries to fetch all related data in one query.
 * Best for fetching individual annotations or small sets.
 * <p>
 * 2. Join-based approach (search method):
 * Uses LEFT JOINs with GROUP BY for efficient streaming and pagination.
 * Best for large result sets with filtering.
 */
// Make this a singleton so we don't keep recreating the mappers.
@SuppressWarnings("checkstyle:FileLength")
@Singleton
class AnnotationDaoImpl implements AnnotationDao, Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnnotationDaoImpl.class);

    private static final int BATCH_SIZE = 1000;

    private static final stroom.annotation.impl.db.jooq.tables.AnnotationTag STATUS =
            ANNOTATION_TAG.as("status");
    private static final stroom.annotation.impl.db.jooq.tables.AnnotationTag LABEL =
            ANNOTATION_TAG.as("label");
    private static final stroom.annotation.impl.db.jooq.tables.AnnotationTag COLLECTION =
            ANNOTATION_TAG.as("collection");
    private static final stroom.annotation.impl.db.jooq.tables.AnnotationEntry COMMENT =
            ANNOTATION_ENTRY.as("comment");
    private static final stroom.annotation.impl.db.jooq.tables.AnnotationEntry HISTORY =
            ANNOTATION_ENTRY.as("history");

    private static final Field<String> STATUS_FIELD =
            DSL.groupConcatDistinct(STATUS.NAME).orderBy(STATUS.NAME.asc()).separator("|");
    private static final Field<String> LABEL_FIELD =
            DSL.groupConcatDistinct(LABEL.NAME).orderBy(LABEL.NAME.asc()).separator("|");
    private static final Field<String> COLLECTION_FIELD =
            DSL.groupConcatDistinct(COLLECTION.NAME).orderBy(COLLECTION.NAME.asc()).separator("|");
    private static final Field<String> COMMENT_FIELD = COMMENT.DATA;
    private static final Field<String> HISTORY_FIELD =
            DSL.groupConcatDistinct(HISTORY.DATA).orderBy(HISTORY.ENTRY_TIME_MS.asc()).separator("|");
    private static final String DATA_RETENTION_USER_NAME = "Data Retention";

    /**
     * Special field for retrieving the latest comment for an annotation.
     */
    private static final Field<String> LATEST_COMMENT_FIELD = DSL
            .select(ANNOTATION_ENTRY.DATA)
            .from(ANNOTATION_ENTRY)
            .where(ANNOTATION_ENTRY.FK_ANNOTATION_ID.eq(ANNOTATION.ID))
            .and(ANNOTATION_ENTRY.TYPE_ID.eq(AnnotationEntryType.COMMENT.getPrimitiveValue()))
            .orderBy(ANNOTATION_ENTRY.ID.desc())
            .limit(1)
            .asField("latest_comment");

    /**
     * Special field for retrieving a pipe-delimited history of all comments for an annotation.
     */
    private static final Field<String> COMMENT_HISTORY_FIELD = DSL.field(
                    "(SELECT GROUP_CONCAT(data ORDER BY id SEPARATOR '|') " +
                    "FROM annotation_entry " +
                    "WHERE fk_annotation_id = annotation.id AND type_id = ?)",
                    String.class,
                    AnnotationEntryType.COMMENT.getPrimitiveValue())
            .as("comment_history");

    /**
     * Special field for retrieving all tags related to an annotation.
     */
    private static final Field<Result<Record>> TAGS_FIELD = DSL.multiset(
                    DSL.select(ANNOTATION_TAG.asterisk())
                            .from(ANNOTATION_TAG_LINK)
                            .join(ANNOTATION_TAG)
                            .on(ANNOTATION_TAG.ID.eq(ANNOTATION_TAG_LINK.FK_ANNOTATION_TAG_ID))
                            .where(ANNOTATION_TAG_LINK.FK_ANNOTATION_ID.eq(ANNOTATION.ID)))
            .as("tags");

    private final AnnotationDbConnProvider connectionProvider;
    private final ExpressionMapper expressionMapper;
    private final TermHandlerFactory termHandlerFactory;
    private final ValueMapper valueMapper;
    private final UserRefLookup userRefLookup;
    private final Provider<AnnotationConfig> annotationConfigProvider;
    private final AnnotationTagDaoImpl annotationTagDao;
    private final StreamFeedProvider streamFeedProvider;
    private final AnnotationFeedNameToIdCache annotationFeedNameToIdCache;
    private final AnnotationFeedIdToNameCache annotationFeedIdToNameCache;

    @Inject
    AnnotationDaoImpl(final AnnotationDbConnProvider connectionProvider,
                      final ExpressionMapperFactory expressionMapperFactory,
                      final TermHandlerFactory termHandlerFactory,
                      final UserRefLookup userRefLookup,
                      final Provider<AnnotationConfig> annotationConfigProvider,
                      final AnnotationTagDaoImpl annotationTagDao,
                      final StreamFeedProvider streamFeedProvider,
                      final AnnotationFeedNameToIdCache annotationFeedNameToIdCache,
                      final AnnotationFeedIdToNameCache annotationFeedIdToNameCache) {
        this.connectionProvider = connectionProvider;
        this.userRefLookup = userRefLookup;
        this.valueMapper = createValueMapper();
        this.annotationConfigProvider = annotationConfigProvider;
        this.annotationTagDao = annotationTagDao;
        this.streamFeedProvider = streamFeedProvider;
        this.annotationFeedNameToIdCache = annotationFeedNameToIdCache;
        this.annotationFeedIdToNameCache = annotationFeedIdToNameCache;
        this.expressionMapper = createExpressionMapper(expressionMapperFactory, userRefLookup);
        this.termHandlerFactory = termHandlerFactory;
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
        addTagHandler(expressionMapper, AnnotationDecorationFields.ANNOTATION_STATUS_FIELD,
                AnnotationTagType.STATUS);
        expressionMapper.map(AnnotationDecorationFields.ANNOTATION_ASSIGNED_TO_FIELD,
                ANNOTATION.ASSIGNED_TO_UUID,
                uuid -> userRefLookup.getByUuid(uuid, FindUserContext.ANNOTATION_ASSIGNMENT)
                        .map(UserRef::getUuid)
                        .orElse(null));
        addTagHandler(expressionMapper, AnnotationDecorationFields.ANNOTATION_LABEL_FIELD,
                AnnotationTagType.LABEL);
        addTagHandler(expressionMapper, AnnotationDecorationFields.ANNOTATION_COLLECTION_FIELD,
                AnnotationTagType.COLLECTION);
        expressionMapper.map(AnnotationDecorationFields.ANNOTATION_COMMENT_FIELD, COMMENT.DATA, value -> value);
        addEntryHandler(expressionMapper, AnnotationDecorationFields.ANNOTATION_HISTORY_FIELD);
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
        addTagHandler(expressionMapper, AnnotationFields.STATUS_FIELD,
                AnnotationTagType.STATUS);
        expressionMapper.map(AnnotationFields.ASSIGNED_TO_FIELD, ANNOTATION.ASSIGNED_TO_UUID, uuid ->
                userRefLookup.getByUuid(uuid, FindUserContext.ANNOTATION_ASSIGNMENT)
                        .map(UserRef::getUuid)
                        .orElse(null));
        addTagHandler(expressionMapper, AnnotationFields.LABEL_FIELD,
                AnnotationTagType.LABEL);
        addTagHandler(expressionMapper, AnnotationFields.COLLECTION_FIELD,
                AnnotationTagType.COLLECTION);
        expressionMapper.map(AnnotationFields.COMMENT_FIELD, COMMENT.DATA, value -> value);
        addEntryHandler(expressionMapper, AnnotationFields.HISTORY_FIELD);
        expressionMapper.map(AnnotationFields.DESCRIPTION_FIELD, ANNOTATION.DESCRIPTION, value -> value);
        expressionMapper.map(AnnotationFields.STREAM_ID_FIELD, ANNOTATION_DATA_LINK.STREAM_ID, Long::valueOf);
        expressionMapper.map(AnnotationFields.EVENT_ID_FIELD, ANNOTATION_DATA_LINK.EVENT_ID, Long::valueOf);
        expressionMapper.multiMap(AnnotationFields.FEED_FIELD, ANNOTATION_DATA_LINK.FEED_ID,
                annotationFeedNameToIdCache::getIds);

        return expressionMapper;
    }

    private void addTagHandler(final ExpressionMapper expressionMapper,
                               final QueryField queryField,
                               final AnnotationTagType annotationTagType) {
        expressionMapper.addHandler(queryField, term -> {
            final MultiConverter<Integer> converter = value ->
                    annotationTagDao.getIds(annotationTagType, value);
            final TermHandler<Integer> termHandler = termHandlerFactory.create(
                    queryField,
                    ANNOTATION_TAG_LINK.FK_ANNOTATION_TAG_ID,
                    converter);

            // We need to put negation on 'in'.
            if (ExpressionTerm.Condition.NOT_EQUALS.equals(term.getCondition())) {
                final ExpressionTerm inverted = term.copy().condition(ExpressionTerm.Condition.EQUALS).build();
                final Condition condition = termHandler.apply(inverted);
                return ANNOTATION.ID.notIn(DSL
                        .selectDistinct(ANNOTATION_TAG_LINK.FK_ANNOTATION_ID)
                        .from(ANNOTATION_TAG_LINK)
                        .where(condition));
            }

            final Condition condition = termHandler.apply(term);
            return ANNOTATION.ID.in(DSL
                    .selectDistinct(ANNOTATION_TAG_LINK.FK_ANNOTATION_ID)
                    .from(ANNOTATION_TAG_LINK)
                    .where(condition));
        });
    }

    private void addEntryHandler(final ExpressionMapper expressionMapper,
                                 final QueryField queryField) {
        expressionMapper.addHandler(queryField, term -> {
            final MultiConverter<String> converter = value -> value;
            final TermHandler<String> termHandler = termHandlerFactory.create(
                    queryField,
                    ANNOTATION_ENTRY.DATA,
                    converter);

            // We need to put negation on 'in'.
            if (ExpressionTerm.Condition.NOT_EQUALS.equals(term.getCondition())) {
                final ExpressionTerm inverted = term.copy().condition(ExpressionTerm.Condition.EQUALS).build();
                final Condition condition = termHandler.apply(inverted);
                return ANNOTATION.ID.notIn(DSL
                        .selectDistinct(ANNOTATION_ENTRY.FK_ANNOTATION_ID)
                        .from(ANNOTATION_ENTRY)
                        .where(condition));
            }

            final Condition condition = termHandler.apply(term);
            return ANNOTATION.ID.in(DSL
                    .selectDistinct(ANNOTATION_ENTRY.FK_ANNOTATION_ID)
                    .from(ANNOTATION_ENTRY)
                    .where(condition));
        });
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
        valueMapper.map(AnnotationDecorationFields.ANNOTATION_STATUS_FIELD, STATUS_FIELD, ValString::create);
        valueMapper.map(AnnotationDecorationFields.ANNOTATION_ASSIGNED_TO_FIELD,
                ANNOTATION.ASSIGNED_TO_UUID,
                this::mapUserUuidToValString);
        valueMapper.map(AnnotationDecorationFields.ANNOTATION_LABEL_FIELD, LABEL_FIELD, ValString::create);
        valueMapper.map(AnnotationDecorationFields.ANNOTATION_COLLECTION_FIELD, COLLECTION_FIELD, ValString::create);
        valueMapper.map(AnnotationDecorationFields.ANNOTATION_COMMENT_FIELD, COMMENT_FIELD, ValString::create);
        valueMapper.map(AnnotationDecorationFields.ANNOTATION_HISTORY_FIELD, HISTORY_FIELD, ValString::create);
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
        valueMapper.map(AnnotationFields.STATUS_FIELD, STATUS_FIELD, ValString::create);
        valueMapper.map(AnnotationFields.ASSIGNED_TO_FIELD, ANNOTATION.ASSIGNED_TO_UUID, this::mapUserUuidToValString);
        valueMapper.map(AnnotationFields.LABEL_FIELD, LABEL_FIELD, ValString::create);
        valueMapper.map(AnnotationFields.COLLECTION_FIELD, COLLECTION_FIELD, ValString::create);
        valueMapper.map(AnnotationFields.COMMENT_FIELD, COMMENT_FIELD, ValString::create);
        valueMapper.map(AnnotationFields.HISTORY_FIELD, HISTORY_FIELD, ValString::create);
        valueMapper.map(AnnotationFields.DESCRIPTION_FIELD, ANNOTATION.DESCRIPTION, ValString::create);
        valueMapper.map(AnnotationFields.STREAM_ID_FIELD, ANNOTATION_DATA_LINK.STREAM_ID, ValLong::create);
        valueMapper.map(AnnotationFields.EVENT_ID_FIELD, ANNOTATION_DATA_LINK.EVENT_ID, ValLong::create);
        valueMapper.map(AnnotationFields.FEED_FIELD, ANNOTATION_DATA_LINK.FEED_ID, id ->
                ValString.create(annotationFeedIdToNameCache.getName(id).orElse("")));

        return valueMapper;
    }

    private Optional<Long> getId(final DocRef docRef) {
        return JooqUtil.contextResult(connectionProvider, context ->
                getId(context, docRef));
    }

    private Optional<Long> getId(final DSLContext context, final DocRef docRef) {
        return context
                .select(ANNOTATION.ID)
                .from(ANNOTATION)
                .where(ANNOTATION.UUID.eq(docRef.getUuid()))
                .fetchOptional(ANNOTATION.ID);
    }

    @Override
    public ResultPage<Annotation> findAnnotations(final FindAnnotationRequest request,
                                                  final Predicate<Annotation> viewPredicate) {
        final List<Condition> conditions = new ArrayList<>();
        final FieldProvider fieldProvider = new FieldProviderImpl(
                List.of(AnnotationFields.TITLE),
                List.of(AnnotationFields.ID,
                        AnnotationFields.UUID,
                        AnnotationFields.SUBJECT,
                        AnnotationFields.STATUS,
                        AnnotationFields.ASSIGNED_TO,
                        AnnotationFields.LABEL,
                        AnnotationFields.COLLECTION));
        try {
            final Optional<ExpressionOperator> optionalExpressionOperator = SimpleStringExpressionParser
                    .create(fieldProvider, request.getFilter());
            optionalExpressionOperator.ifPresent(expressionOperator ->
                    conditions.add(expressionMapper.apply(expressionOperator)));
            conditions.add(ANNOTATION.DELETED.isFalse());
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            return ResultPage.empty();
        }

        final List<Annotation> list = JooqUtil.contextResult(connectionProvider, context ->
                findAnnotations(context, conditions, request, viewPredicate).toList());
        return ResultPage.createPageLimitedList(list, request.getPageRequest());
    }

    private Stream<Annotation> findAnnotations(final DSLContext context,
                                               final List<Condition> conditions,
                                               final FindAnnotationRequest request,
                                               final Predicate<Annotation> viewPredicate) {
        SelectJoinStep<Record> select = commonSelect(context);

        if (request.getSourceId() != null) {
            select = select
                    .join(ANNOTATION_LINK)
                    .on(ANNOTATION_LINK.FK_ANNOTATION_DST_ID.eq(ANNOTATION.ID));
            conditions.add(ANNOTATION_LINK.FK_ANNOTATION_SRC_ID.eq(request.getSourceId()));
        } else if (request.getDestinationId() != null) {
            select = select
                    .join(ANNOTATION_LINK)
                    .on(ANNOTATION_LINK.FK_ANNOTATION_SRC_ID.eq(ANNOTATION.ID));
            conditions.add(ANNOTATION_LINK.FK_ANNOTATION_DST_ID.eq(request.getDestinationId()));
        }

        return select
                .where(conditions)
                .fetch()
                .stream()
                .map(this::mapToAnnotation)
                .filter(viewPredicate);
    }

    private SelectJoinStep<Record> commonSelect(final DSLContext context) {
        return context
                .select(ANNOTATION.asterisk(),
                        // Latest comment via subquery
                        LATEST_COMMENT_FIELD,
                        // History using GROUP_CONCAT
                        COMMENT_HISTORY_FIELD,
                        // Tags as multiset
                        TAGS_FIELD)
                .from(ANNOTATION);
    }

    @Override
    public Optional<Annotation> getAnnotationById(final long id) {
        return JooqUtil.contextResult(connectionProvider, context -> commonSelect(context)
                .where(ANNOTATION.ID.eq(id))
                .fetchOptional()
                .map(this::mapToAnnotation));
    }

    @Override
    public Optional<Annotation> getAnnotationByDocRef(final DocRef annotationRef) {
        return JooqUtil.contextResult(connectionProvider, context -> commonSelect(context)
                .where(ANNOTATION.UUID.eq(annotationRef.getUuid()))
                .fetchOptional()
                .map(this::mapToAnnotation));
    }

    @Override
    public List<DocRef> idListToDocRefs(final Collection<Long> idList) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                        .select(ANNOTATION.UUID)
                        .from(ANNOTATION)
                        .where(ANNOTATION.ID.in(idList))
                        .fetch(ANNOTATION.UUID))
                .stream()
                .map(uuid -> Annotation.buildDocRef().uuid(uuid).build())
                .toList();
    }

    @Override
    public List<AnnotationEntry> getAnnotationEntries(final DocRef annotationRef) {
        final List<AnnotationEntry> entries =
                JooqUtil.contextResult(connectionProvider, context -> context
                                .select(ANNOTATION_ENTRY.ID,
                                        ANNOTATION_ENTRY.ENTRY_TIME_MS,
                                        ANNOTATION_ENTRY.ENTRY_USER_UUID,
                                        ANNOTATION_ENTRY.UPDATE_TIME_MS,
                                        ANNOTATION_ENTRY.UPDATE_USER_UUID,
                                        ANNOTATION_ENTRY.TYPE_ID,
                                        ANNOTATION_ENTRY.DATA,
                                        ANNOTATION_ENTRY.DELETED)
                                .from(ANNOTATION_ENTRY)
                                .join(ANNOTATION).on(ANNOTATION_ENTRY.FK_ANNOTATION_ID.eq(ANNOTATION.ID))
                                .where(ANNOTATION.UUID.eq(annotationRef.getUuid()))
                                .orderBy(ANNOTATION_ENTRY.ID)
                                .fetch())
                        .map(this::mapToAnnotationEntry);

        final Map<AnnotationEntryType, EntryValue> currentValues = new HashMap<>();
        return entries
                .stream()
                .map(entry -> {
                    // Get the previous value.
                    final EntryValue currentValue = currentValues.get(entry.getEntryType());
                    // Remember the previous value.
                    currentValues.put(entry.getEntryType(), entry.getEntryValue());

                    // Set the previous value.
                    return entry.copy().previousValue(currentValue).build();
                })
                .filter(entry -> !entry.isDeleted())
                .toList();
    }

    @Override
    public List<Annotation> getAnnotationsForEvents(final EventId eventId) {
        return JooqUtil.contextResult(connectionProvider, context -> commonSelect(context)
                .join(ANNOTATION_DATA_LINK).on(ANNOTATION_DATA_LINK.FK_ANNOTATION_ID.eq(ANNOTATION.ID))
                .where(ANNOTATION_DATA_LINK.STREAM_ID.eq(eventId.getStreamId())
                        .and(ANNOTATION_DATA_LINK.EVENT_ID.eq(eventId.getEventId())))
                .and(ANNOTATION.DELETED.isFalse())
                .fetch()
                .map(this::mapToAnnotation));
    }

    private AnnotationEntry mapToAnnotationEntry(final Record record) {
        final byte typeId = record.get(ANNOTATION_ENTRY.TYPE_ID);
        final AnnotationEntryType type = AnnotationEntryType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(typeId);

        EntryValue entryValue = null;
        final String data = record.get(ANNOTATION_ENTRY.DATA);
        if (data != null) {
            entryValue = switch (type) {
                case AnnotationEntryType.ASSIGNED -> UserRefEntryValue.of(getUserRef(data));
                case AnnotationEntryType.ADD_TABLE_DATA -> JsonUtil.readValue(data, AnnotationTable.class);
                default -> StringEntryValue.of(data);
            };
        }

        return AnnotationEntry.builder()
                .id(record.get(ANNOTATION_ENTRY.ID))
                .entryType(type)
                .entryTime(record.get(ANNOTATION_ENTRY.ENTRY_TIME_MS))
                .entryUser(getUserRef(record.get(ANNOTATION_ENTRY.ENTRY_USER_UUID)))
                .updateTime(record.get(ANNOTATION_ENTRY.UPDATE_TIME_MS))
                .updateUser(getUserRef(record.get(ANNOTATION_ENTRY.UPDATE_USER_UUID)))
                .deleted(record.get(ANNOTATION_ENTRY.DELETED))
                .entryValue(entryValue)
                .build();
    }

    private UserRef getUserRef(final String uuid) {
        if (uuid == null) {
            return null;
        }
        return userRefLookup.getByUuid(uuid, FindUserContext.ANNOTATION_ASSIGNMENT)
                .orElse(UserRef.builder().uuid(uuid).build());
    }

    private Annotation mapToAnnotation(final Record record) {
        final Result<Record> tagRecords = record.get(TAGS_FIELD);
        final Map<AnnotationTagType, List<AnnotationTag>> tags = tagRecords.stream()
                .map(r -> r.into(ANNOTATION_TAG))
                .map(annotationTagDao::mapToAnnotationTag)
                .collect(Collectors.groupingBy(
                        AnnotationTag::getType,
                        () -> new EnumMap<>(AnnotationTagType.class),
                        Collectors.toList()));

        final List<AnnotationTag> statuses = tags.get(AnnotationTagType.STATUS);
        final AnnotationTag status = NullSafe.first(statuses);
        final List<AnnotationTag> labels = tags.get(AnnotationTagType.LABEL);
        final List<AnnotationTag> collections = tags.get(AnnotationTagType.COLLECTION);
        final String comment = record.get(LATEST_COMMENT_FIELD);
        final String history = record.get(COMMENT_HISTORY_FIELD);

        return Annotation.builder()
                .uuid(record.get(ANNOTATION.UUID))
                .name(record.get(ANNOTATION.TITLE))
                .version("" + record.get(ANNOTATION.VERSION))
                .createTimeMs(record.get(ANNOTATION.CREATE_TIME_MS))
                .createUser(record.get(ANNOTATION.CREATE_USER))
                .updateTimeMs(record.get(ANNOTATION.UPDATE_TIME_MS))
                .updateUser(record.get(ANNOTATION.UPDATE_USER))
                .id(record.get(ANNOTATION.ID))
                .subject(record.get(ANNOTATION.SUBJECT))
                .status(status)
                .assignedTo(getUserRef(record.get(ANNOTATION.ASSIGNED_TO_UUID)))
                .labels(labels)
                .collections(collections)
                .comment(comment)
                .history(history)
                .description(record.get(ANNOTATION.DESCRIPTION))
                .retentionPeriod(mapToSimpleDuration(record))
                .retainUntilTimeMs(record.get(ANNOTATION.RETAIN_UNTIL_MS))
                .build();
    }

    private SimpleDuration mapToSimpleDuration(final Record record) {
        final Long time = record.get(ANNOTATION.RETENTION_TIME);
        final Byte unit = record.get(ANNOTATION.RETENTION_UNIT);
        if (time != null && unit != null) {
            return new SimpleDuration(time, TimeUnit.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(unit));
        }
        return null;
    }

    private SimpleDuration getDefaultRetentionPeriod() {
        try {
            return SimpleDurationUtil.parse(annotationConfigProvider.get().getDefaultRetentionPeriod());
        } catch (final ParseException | RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
        }
        return null;
    }

    @Override
    public Annotation createAnnotation(final CreateAnnotationRequest request,
                                       final UserRef currentUser) {
        final String userUuid = currentUser.getUuid();
        final String userName = currentUser.toDisplayString();
        final Instant now = Instant.now();
        final long nowMs = now.toEpochMilli();

        final AnnotationTag statusTag = annotationTagDao
                .findAnnotationTag(AnnotationTagType.STATUS, request.getStatus())
                .orElse(null);
        final SimpleDuration retentionPeriod = getDefaultRetentionPeriod();
        final Long retainUntilTimeMs = calculateRetainUntilTimeMs(retentionPeriod, nowMs);

        // Check assignment is allowed to the supplied user.
        final String assignedToUuid = NullSafe
                .get(request.getAssignTo(), UserRef::getUuid);
        validateAssignedToUser(assignedToUuid);

        return JooqUtil.transactionResult(connectionProvider, context -> {
            Annotation annotation = Annotation.builder()
                    .uuid(UUID.randomUUID().toString())
                    .createTimeMs(nowMs)
                    .createUser(userName)
                    .updateTimeMs(nowMs)
                    .updateUser(userName)
                    .name(request.getTitle())
                    .subject(request.getSubject())
                    .status(statusTag)
                    .assignedTo(request.getAssignTo())
                    .retentionPeriod(retentionPeriod)
                    .retainUntilTimeMs(retainUntilTimeMs)
                    .build();
            annotation = create(context, annotation);
            final long annotationId = annotation.getId();

            // Create default entries.
            if (statusTag != null) {
                // Add the new tag.
                addTag(context, annotationId, statusTag);

                // Create history entry.
                createEntry(context, annotationId, userUuid, now, AnnotationEntryType.STATUS, statusTag.getName());
            }

            if (assignedToUuid != null) {
                // Create history entry.
                createEntry(context, annotationId, userUuid, now, AnnotationEntryType.ASSIGNED, assignedToUuid);
            }

            if (!NullSafe.isEmptyCollection(request.getLinkedEvents())) {
                createEventLinks(context, userUuid, now, annotationId, request.getLinkedEvents());
            }

            if (request.getTable() != null) {
                createEntry(
                        context,
                        annotationId,
                        userUuid,
                        now,
                        AnnotationEntryType.ADD_TABLE_DATA,
                        JsonUtil.writeValueAsString(request.getTable()));
            }

            // Now select everything back to provide refreshed details.
            return annotation;
        });
    }

    private Long calculateRetainUntilTimeMs(final SimpleDuration retentionPeriod,
                                            final long createTimeMs) {
        if (retentionPeriod != null && retentionPeriod.getTimeUnit() != null) {
            return SimpleDurationUtil
                    .plus(Instant.ofEpochMilli(createTimeMs), retentionPeriod).toEpochMilli();
        }
        return null;
    }

    @Override
    public boolean change(final SingleAnnotationChangeRequest request, final UserRef currentUser) {
        try {
            final Instant now = Instant.now();

            // Update parent if we need to.
            final Optional<Long> optionalId = getId(request.getAnnotationRef());
            if (optionalId.isEmpty()) {
                throw new RuntimeException("Unable to create entry for unknown annotation");
            }

            final long annotationId = optionalId.get();
            final String userUuid = currentUser.getUuid();
            final AbstractAnnotationChange change = request.getChange();

            switch (change) {
                case final ChangeTitle changeTitle ->
                        changeAnnotationTitle(changeTitle, currentUser, annotationId, now);
                case final ChangeSubject changeSubject ->
                        changeAnnotationSubject(changeSubject, currentUser, annotationId, now);
                case final AddTag addTag -> addTag(addTag, currentUser, annotationId, now);
                case final RemoveTag removeTag -> removeTag(removeTag, currentUser, annotationId, now);
                case final SetTag setTag -> setTag(setTag, currentUser, annotationId, now);
                case final ChangeAssignedTo changeAssignedTo ->
                        changeAssignedTo(changeAssignedTo, currentUser, annotationId, now);
                case final ChangeComment changeComment -> changeComment(changeComment, currentUser, annotationId, now);
                case final ChangeDescription changeDescription ->
                        changeDescription(changeDescription, currentUser, annotationId, now);
                case final ChangeRetentionPeriod changeRetentionPeriod ->
                        changeRetentionPeriod(changeRetentionPeriod, currentUser, annotationId, now);
                case final LinkEvents linkEvents -> linkEvents(userUuid, now, annotationId, linkEvents.getEvents());
                case final UnlinkEvents unlinkEvents ->
                        unlinkEvents(userUuid, now, annotationId, unlinkEvents.getEvents());
                case final LinkAnnotations linkAnnotations ->
                        linkAnnotations(userUuid, now, annotationId, linkAnnotations.getAnnotations());
                case final UnlinkAnnotations unlinkAnnotations ->
                        unlinkAnnotations(userUuid, now, annotationId, unlinkAnnotations.getAnnotations());
                case final AddAnnotationTable addAnnotationTable ->
                        addAnnotationTable(userUuid, now, annotationId, addAnnotationTable);
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }

        return true;
    }

    private void changeAnnotationTitle(final ChangeTitle changeTitle,
                                       final UserRef currentUser,
                                       final long annotationId,
                                       final Instant now) {
        final String userUuid = currentUser.getUuid();
        final String userName = currentUser.toDisplayString();

        JooqUtil.transaction(connectionProvider, context -> {
            // Set annotation title.
            context
                    .update(ANNOTATION)
                    .set(ANNOTATION.TITLE, changeTitle.getTitle())
                    .set(ANNOTATION.UPDATE_USER, userName)
                    .set(ANNOTATION.UPDATE_TIME_MS, now.toEpochMilli())
                    .where(ANNOTATION.ID.eq(annotationId))
                    .execute();

            // Create history entry.
            createEntry(context, annotationId, userUuid, now, AnnotationEntryType.TITLE,
                    changeTitle.getTitle());
        });
    }

    private void changeAnnotationSubject(final ChangeSubject changeSubject,
                                         final UserRef currentUser,
                                         final long annotationId,
                                         final Instant now) {
        final String userUuid = currentUser.getUuid();
        final String userName = currentUser.toDisplayString();

        JooqUtil.transaction(connectionProvider, context -> {
            // Set annotation subject.
            context
                    .update(ANNOTATION)
                    .set(ANNOTATION.SUBJECT, changeSubject.getSubject())
                    .set(ANNOTATION.UPDATE_USER, userName)
                    .set(ANNOTATION.UPDATE_TIME_MS, now.toEpochMilli())
                    .where(ANNOTATION.ID.eq(annotationId))
                    .execute();

            // Create history entry.
            createEntry(context, annotationId, userUuid, now, AnnotationEntryType.SUBJECT,
                    changeSubject.getSubject());
        });
    }

    private void addTag(final AddTag addTag,
                        final UserRef currentUser,
                        final long annotationId,
                        final Instant now) {
        final String userUuid = currentUser.getUuid();
        final String userName = currentUser.toDisplayString();

        JooqUtil.transaction(connectionProvider, context -> {
            // Add the new tag.
            addTag(context, annotationId, addTag.getTag());
            // Mark the annotation as updated.
            updateAnnotation(context, annotationId, userName, now.toEpochMilli());

            // Create history entry.
            final AnnotationEntryType entryType = addTag.getTag().getType() == AnnotationTagType.LABEL
                    ? AnnotationEntryType.ADD_LABEL
                    : AnnotationEntryType.ADD_TO_COLLECTION;
            createEntry(context, annotationId, userUuid, now, entryType,
                    addTag.getTag().getName());
        });
    }

    private void removeTag(final RemoveTag removeTag,
                           final UserRef currentUser,
                           final long annotationId,
                           final Instant now) {
        final String userUuid = currentUser.getUuid();
        final String userName = currentUser.toDisplayString();

        JooqUtil.transaction(connectionProvider, context -> {
            // Remove the tag.
            removeTag(context, annotationId, removeTag.getTag());

            // Mark the annotation as updated.
            updateAnnotation(context, annotationId, userName, now.toEpochMilli());

            // Create history entry.
            final AnnotationEntryType entryType = removeTag.getTag().getType() == AnnotationTagType.LABEL
                    ? AnnotationEntryType.REMOVE_LABEL
                    : AnnotationEntryType.REMOVE_FROM_COLLECTION;
            createEntry(context, annotationId, userUuid, now, entryType,
                    removeTag.getTag().getName());
        });
    }

    private void setTag(final SetTag setTag,
                        final UserRef currentUser,
                        final long annotationId,
                        final Instant now) {
        final String userUuid = currentUser.getUuid();
        final String userName = currentUser.toDisplayString();

        JooqUtil.transaction(connectionProvider, context -> {
            // Delete any existing tags with the same type.
            removeAllTags(context, annotationId, setTag.getTag().getType());

            // Add the new tag.
            addTag(context, annotationId, setTag.getTag());

            // Mark the annotation as updated.
            updateAnnotation(context, annotationId, userName, now.toEpochMilli());

            // Create history entry.
            createEntry(context, annotationId, userUuid, now, AnnotationEntryType.STATUS,
                    setTag.getTag().getName());
        });
    }

    private void changeAssignedTo(final ChangeAssignedTo changeAssignedTo,
                                  final UserRef currentUser,
                                  final long annotationId,
                                  final Instant now) {
        final String userUuid = currentUser.getUuid();
        final String userName = currentUser.toDisplayString();

        final String assignedToUuid = NullSafe
                .get(changeAssignedTo, ChangeAssignedTo::getUserRef, UserRef::getUuid);

        // Check assignment is allowed to the supplied user.
        validateAssignedToUser(assignedToUuid);

        JooqUtil.transaction(connectionProvider, context -> {
            // Change assigned to UUID.
            context
                    .update(ANNOTATION)
                    .set(ANNOTATION.ASSIGNED_TO_UUID, assignedToUuid)
                    .set(ANNOTATION.UPDATE_USER, userName)
                    .set(ANNOTATION.UPDATE_TIME_MS, now.toEpochMilli())
                    .where(ANNOTATION.ID.eq(annotationId))
                    .execute();

            // Create history entry.
            createEntry(context, annotationId, userUuid, now, AnnotationEntryType.ASSIGNED, assignedToUuid);
        });
    }

    private void changeComment(final ChangeComment changeComment,
                               final UserRef currentUser,
                               final long annotationId,
                               final Instant now) {
        final String userUuid = currentUser.getUuid();
        final String userName = currentUser.toDisplayString();

        JooqUtil.transaction(connectionProvider, context -> {
            // Update annotation update time.
            context
                    .update(ANNOTATION)
                    .set(ANNOTATION.UPDATE_USER, userName)
                    .set(ANNOTATION.UPDATE_TIME_MS, now.toEpochMilli())
                    .where(ANNOTATION.ID.eq(annotationId))
                    .execute();

            // Create history entry.
            createEntry(context, annotationId, userUuid, now, AnnotationEntryType.COMMENT,
                    changeComment.getComment());
        });
    }

    private void changeDescription(final ChangeDescription changeDescription,
                                   final UserRef currentUser,
                                   final long annotationId,
                                   final Instant now) {
//        final String userUuid = currentUser.getUuid();
        final String userName = currentUser.toDisplayString();

        JooqUtil.transaction(connectionProvider, context -> {
            // Set annotation description.
            context
                    .update(ANNOTATION)
                    .set(ANNOTATION.DESCRIPTION, changeDescription.getDescription())
                    .set(ANNOTATION.UPDATE_USER, userName)
                    .set(ANNOTATION.UPDATE_TIME_MS, now.toEpochMilli())
                    .where(ANNOTATION.ID.eq(annotationId))
                    .execute();

            // Don't log history....
//            // Create history entry.
//            createEntry(context, annotationId, userUuid, now, AnnotationEntryType.DESCRIPTION,
//                    changeDescription.getDescription());
        });
    }

    private void changeRetentionPeriod(final ChangeRetentionPeriod changeRetentionPeriod,
                                       final UserRef currentUser,
                                       final long annotationId,
                                       final Instant now) {
        final String userUuid = currentUser.getUuid();

        JooqUtil.transaction(connectionProvider, context -> {
            final SimpleDuration retentionPeriod = changeRetentionPeriod.getRetentionPeriod();
            final int updateCount;
            if (retentionPeriod != null && retentionPeriod.getTimeUnit() != null) {
                // Calculate retention in SQL using the create_time_ms from the database
                final long millis = SimpleDurationUtil.convertToStroomDuration(retentionPeriod).toMillis();
                updateCount = context
                        .update(ANNOTATION)
                        .set(ANNOTATION.RETENTION_TIME, retentionPeriod.getTime())
                        .set(ANNOTATION.RETENTION_UNIT, retentionPeriod.getTimeUnit().getPrimitiveValue())
                        .set(ANNOTATION.RETAIN_UNTIL_MS, ANNOTATION.CREATE_TIME_MS.plus(millis))
                        .set(ANNOTATION.UPDATE_USER, currentUser.toDisplayString())
                        .set(ANNOTATION.UPDATE_TIME_MS, now.toEpochMilli())
                        .where(ANNOTATION.ID.eq(annotationId))
                        .execute();
            } else {
                // Forever retention
                updateCount = context
                        .update(ANNOTATION)
                        .set(ANNOTATION.RETENTION_TIME, (Long) null)
                        .set(ANNOTATION.RETENTION_UNIT, (Byte) null)
                        .set(ANNOTATION.RETAIN_UNTIL_MS, (Long) null)
                        .set(ANNOTATION.UPDATE_USER, currentUser.toDisplayString())
                        .set(ANNOTATION.UPDATE_TIME_MS, now.toEpochMilli())
                        .where(ANNOTATION.ID.eq(annotationId))
                        .execute();
            }

            LOGGER.debug("change() - changeRetentionPeriod - id: {}, updated {} rows", annotationId, updateCount);

            // Create history entry.
            createEntry(context, annotationId, userUuid, now, AnnotationEntryType.RETENTION_PERIOD,
                    NullSafe.getOrElse(retentionPeriod, SimpleDuration::toString, "Forever"));
        });
    }

    private void validateAssignedToUser(final String assignedToUuid) {
        // Check assignment is allowed to the supplied user.
        if (assignedToUuid != null) {
            final Optional<UserRef> userRef = userRefLookup
                    .getByUuid(assignedToUuid, FindUserContext.ANNOTATION_ASSIGNMENT);
            if (userRef.isEmpty()) {
                throw new RuntimeException("You cannot assign annotation to: " + assignedToUuid);
            }
        }
    }

    private void removeAllTags(final DSLContext context,
                               final long annotationId,
                               final AnnotationTagType annotationTagType) {
        context.deleteFrom(ANNOTATION_TAG_LINK)
                .where(ANNOTATION_TAG_LINK.FK_ANNOTATION_ID.eq(annotationId)
                        .and(ANNOTATION_TAG_LINK.FK_ANNOTATION_TAG_ID.in(
                                context
                                        .select(ANNOTATION_TAG.ID)
                                        .from(ANNOTATION_TAG)
                                        .where(ANNOTATION_TAG.TYPE_ID.eq(
                                                annotationTagType.getPrimitiveValue())))
                        )).execute();
    }

    private void removeTag(final DSLContext context, final long annotationId, final AnnotationTag tag) {
        context
                .deleteFrom(ANNOTATION_TAG_LINK)
                .where(ANNOTATION_TAG_LINK.FK_ANNOTATION_ID.eq(annotationId))
                .and(ANNOTATION_TAG_LINK.FK_ANNOTATION_TAG_ID.eq(tag.getId()))
                .execute();
    }

    private void addTag(final DSLContext context, final long annotationId, final AnnotationTag tag) {
        context
                .insertInto(ANNOTATION_TAG_LINK,
                        ANNOTATION_TAG_LINK.FK_ANNOTATION_ID,
                        ANNOTATION_TAG_LINK.FK_ANNOTATION_TAG_ID)
                .values(annotationId,
                        tag.getId())
                .onDuplicateKeyUpdate()
                // Set to same values to avoid exception while returning 0 on duplicate
                .set(ANNOTATION_TAG_LINK.FK_ANNOTATION_ID, ANNOTATION_TAG_LINK.FK_ANNOTATION_ID)
                .set(ANNOTATION_TAG_LINK.FK_ANNOTATION_TAG_ID, ANNOTATION_TAG_LINK.FK_ANNOTATION_TAG_ID)
                .execute();
    }

    private void updateAnnotation(final DSLContext context,
                                  final long annotationId,
                                  final String userName,
                                  final long nowMs) {
        context
                .update(ANNOTATION)
                .set(ANNOTATION.UPDATE_USER, userName)
                .set(ANNOTATION.UPDATE_TIME_MS, nowMs)
                .where(ANNOTATION.ID.eq(annotationId))
                .execute();
    }

    @NullMarked
    private void createEntry(final DSLContext context,
                             final long annotationId,
                             final String userUuid,
                             final Instant now,
                             final AnnotationEntryType type,
                             @Nullable final String entryData) {
        Objects.requireNonNull(userUuid);
        context.insertInto(ANNOTATION_ENTRY,
                        ANNOTATION_ENTRY.ENTRY_TIME_MS,
                        ANNOTATION_ENTRY.ENTRY_USER_UUID,
                        ANNOTATION_ENTRY.UPDATE_TIME_MS,
                        ANNOTATION_ENTRY.UPDATE_USER_UUID,
                        ANNOTATION_ENTRY.FK_ANNOTATION_ID,
                        ANNOTATION_ENTRY.TYPE_ID,
                        ANNOTATION_ENTRY.DATA)
                .values(now.toEpochMilli(),
                        userUuid,
                        now.toEpochMilli(),
                        userUuid,
                        annotationId,
                        type.getPrimitiveValue(),
                        entryData)
                .execute();
    }

    private int createEntries(final DSLContext context,
                              final List<Entry> entries) {
        // Exit immediately if there are no entries to insert.
        if (NullSafe.isEmptyCollection(entries)) {
            return 0;
        }

        // Build batch insert
        InsertValuesStep7<AnnotationEntryRecord, Long, String, Long, String, Long, Byte, String> insertStep =
                context.insertInto(ANNOTATION_ENTRY,
                        ANNOTATION_ENTRY.ENTRY_TIME_MS,
                        ANNOTATION_ENTRY.ENTRY_USER_UUID,
                        ANNOTATION_ENTRY.UPDATE_TIME_MS,
                        ANNOTATION_ENTRY.UPDATE_USER_UUID,
                        ANNOTATION_ENTRY.FK_ANNOTATION_ID,
                        ANNOTATION_ENTRY.TYPE_ID,
                        ANNOTATION_ENTRY.DATA);

        for (final Entry entry : entries) {
            insertStep = insertStep.values(
                    entry.entryTime.toEpochMilli(),
                    entry.entryUserUuid,
                    entry.updateTime.toEpochMilli(),
                    entry.updateUserUuid,
                    entry.annotationId,
                    entry.type.getPrimitiveValue(),
                    entry.entryData);
        }

        return insertStep.execute();
    }

    private Annotation create(final DSLContext context, final Annotation annotation) {
        final String userUuid = getUserUuid(annotation.getAssignedTo());
        final Long retentionTime = NullSafe.get(
                annotation.getRetentionPeriod(),
                SimpleDuration::getTime);
        final Byte retentionTimeUnit = NullSafe.get(
                annotation.getRetentionPeriod(),
                SimpleDuration::getTimeUnit,
                TimeUnit::getPrimitiveValue);

        final Optional<Long> optional = context
                .insertInto(ANNOTATION,
                        ANNOTATION.UUID,
                        ANNOTATION.VERSION,
                        ANNOTATION.CREATE_USER,
                        ANNOTATION.CREATE_TIME_MS,
                        ANNOTATION.UPDATE_USER,
                        ANNOTATION.UPDATE_TIME_MS,
                        ANNOTATION.TITLE,
                        ANNOTATION.SUBJECT,
                        ANNOTATION.ASSIGNED_TO_UUID,
                        ANNOTATION.DESCRIPTION,
                        ANNOTATION.RETENTION_TIME,
                        ANNOTATION.RETENTION_UNIT,
                        ANNOTATION.RETAIN_UNTIL_MS)
                .values(
                        annotation.getUuid(),
                        1,
                        annotation.getCreateUser(),
                        annotation.getCreateTimeMs(),
                        annotation.getUpdateUser(),
                        annotation.getUpdateTimeMs(),
                        annotation.getName(),
                        annotation.getSubject(),
                        userUuid,
                        annotation.getDescription(),
                        retentionTime,
                        retentionTimeUnit,
                        annotation.getRetainUntilTimeMs())
                .returning(ANNOTATION.ID)
                .fetchOptional()
                .map(AnnotationRecord::getId);

        return optional
                .map(id -> annotation.copy().id(id).version("1").build())
                .orElse(null);
    }

    private void linkEvents(final String userUuid,
                            final Instant now,
                            final long annotationId,
                            final List<EventId> eventIds) {
        JooqUtil.transaction(connectionProvider, context ->
                createEventLinks(context, userUuid, now, annotationId, eventIds));
    }

    private void createEventLinks(final DSLContext context,
                                  final String userUuid,
                                  final Instant now,
                                  final long annotationId,
                                  final List<EventId> eventIds) {
        // If we have been given an empty list then exit.
        if (NullSafe.isEmptyCollection(eventIds)) {
            return;
        }

        // Batch lookup feed names and IDs
        final Map<Long, String> streamToFeed = new HashMap<>();
        final Map<String, Integer> feedNameToId = new HashMap<>();

        // Resolve all feed named and IDs.
        for (final EventId eventId : eventIds) {
            final String feedName = streamToFeed
                    .computeIfAbsent(eventId.getStreamId(), streamFeedProvider::getFeedName);
            if (feedName != null) {
                feedNameToId.computeIfAbsent(feedName, annotationFeedNameToIdCache::getOrCreateId);
            }
        }

        // Filter to only valid event IDs (those with resolved feed IDs)
        final List<EventId> validEventIds = eventIds
                .stream()
                .filter(eventId -> {
                    final String feedName = streamToFeed.get(eventId.getStreamId());
                    return feedName != null && feedNameToId.get(feedName) != null;
                })
                .toList();

        // If we cannot resolve feeds for any of our events then exit.
        if (validEventIds.isEmpty()) {
            return;
        }

        processBatches(validEventIds, BATCH_SIZE, batch -> {
            // Filter out any existing links
            final Set<EventId> existingLinks = context
                    .select(ANNOTATION_DATA_LINK.STREAM_ID, ANNOTATION_DATA_LINK.EVENT_ID)
                    .from(ANNOTATION_DATA_LINK)
                    .where(ANNOTATION_DATA_LINK.FK_ANNOTATION_ID.eq(annotationId))
                    .and(DSL.row(ANNOTATION_DATA_LINK.STREAM_ID, ANNOTATION_DATA_LINK.EVENT_ID)
                            .in(batch.stream()
                                    .map(e -> DSL.row(e.getStreamId(), e.getEventId()))
                                    .collect(Collectors.toList())))
                    .fetchSet(record -> new EventId(
                            record.get(ANNOTATION_DATA_LINK.STREAM_ID),
                            record.get(ANNOTATION_DATA_LINK.EVENT_ID)
                    ));

            // Filter to only new links
            final List<EventId> newEventIds = batch.stream()
                    .filter(eventId -> !existingLinks.contains(eventId))
                    .toList();

            if (!newEventIds.isEmpty()) {
                // Build batch insert
                InsertValuesStep4<AnnotationDataLinkRecord, Long, Integer, Long, Long> insertStep =
                        context.insertInto(ANNOTATION_DATA_LINK,
                                ANNOTATION_DATA_LINK.FK_ANNOTATION_ID,
                                ANNOTATION_DATA_LINK.FEED_ID,
                                ANNOTATION_DATA_LINK.STREAM_ID,
                                ANNOTATION_DATA_LINK.EVENT_ID);

                final List<Entry> entries = new ArrayList<>(newEventIds.size());
                for (final EventId eventId : newEventIds) {
                    final String feedName = streamToFeed.get(eventId.getStreamId());
                    final Integer feedId = feedNameToId.get(feedName);
                    insertStep = insertStep.values(
                            annotationId,
                            feedId,
                            eventId.getStreamId(),
                            eventId.getEventId()
                    );

                    // Remember the entry to add.
                    entries.add(new Entry(now, userUuid, now, userUuid, annotationId, AnnotationEntryType.LINK_EVENT,
                            eventId.toString()));
                }

                // Execute with onDuplicateKeyUpdate
                final int result = insertStep
                        .onDuplicateKeyUpdate()
                        .set(ANNOTATION_DATA_LINK.FK_ANNOTATION_ID, ANNOTATION_DATA_LINK.FK_ANNOTATION_ID)
                        .set(ANNOTATION_DATA_LINK.FEED_ID, ANNOTATION_DATA_LINK.FEED_ID)
                        .set(ANNOTATION_DATA_LINK.STREAM_ID, ANNOTATION_DATA_LINK.STREAM_ID)
                        .set(ANNOTATION_DATA_LINK.EVENT_ID, ANNOTATION_DATA_LINK.EVENT_ID)
                        .execute();

                if (result > 0) {
                    // Create history entries for successful inserts
                    createEntries(context, entries);
                }
            }
        });
    }


    private void unlinkEvents(final String userUuid,
                              final Instant now,
                              final long annotationId,
                              final List<EventId> eventIds) {
        JooqUtil.transaction(connectionProvider, context ->
                removeEventLinks(context, userUuid, now, annotationId, eventIds));
    }

    private void removeEventLinks(final DSLContext context,
                                  final String userUuid,
                                  final Instant now,
                                  final long annotationId,
                                  final List<EventId> eventIds) {
        // If we have been given an empty list then exit.
        if (NullSafe.isEmptyCollection(eventIds)) {
            return;
        }

        processBatches(eventIds, BATCH_SIZE, batch -> {
            // Only remove existing links.
            final Set<EventId> existingLinks = context
                    .select(ANNOTATION_DATA_LINK.STREAM_ID, ANNOTATION_DATA_LINK.EVENT_ID)
                    .from(ANNOTATION_DATA_LINK)
                    .where(ANNOTATION_DATA_LINK.FK_ANNOTATION_ID.eq(annotationId))
                    .and(DSL.row(ANNOTATION_DATA_LINK.STREAM_ID, ANNOTATION_DATA_LINK.EVENT_ID)
                            .in(batch.stream()
                                    .map(e -> DSL.row(e.getStreamId(), e.getEventId()))
                                    .collect(Collectors.toList())))
                    .fetchSet(record -> new EventId(
                            record.get(ANNOTATION_DATA_LINK.STREAM_ID),
                            record.get(ANNOTATION_DATA_LINK.EVENT_ID)
                    ));

            // Filter to only existing links
            final List<EventId> existingIds = batch
                    .stream()
                    .filter(existingLinks::contains)
                    .toList();

            if (!existingIds.isEmpty()) {
                // Remove links.
                final int result = context
                        .deleteFrom(ANNOTATION_DATA_LINK)
                        .where(ANNOTATION_DATA_LINK.FK_ANNOTATION_ID.eq(annotationId))
                        .and(DSL.row(ANNOTATION_DATA_LINK.STREAM_ID, ANNOTATION_DATA_LINK.EVENT_ID)
                                .in(existingIds.stream()
                                        .map(e -> DSL.row(e.getStreamId(), e.getEventId()))
                                        .collect(Collectors.toList())))
                        .execute();

                if (result > 0) {
                    // Create history entries for successful deletes
                    final List<Entry> entries = existingIds.stream().map(eventId ->
                            new Entry(now, userUuid, now, userUuid, annotationId, AnnotationEntryType.UNLINK_EVENT,
                                    eventId.toString())).toList();
                    createEntries(context, entries);
                }
            }
        });
    }

    @Override
    public List<EventId> getLinkedEvents(final DocRef annotationRef) {
        final Optional<Long> optionalId = getId(annotationRef);
        return optionalId.map(this::getLinkedEvents)
                .orElse(Collections.emptyList());

    }

    private List<EventId> getLinkedEvents(final long annotationId) {
        return JooqUtil.contextResult(connectionProvider, context -> context
                        .select(ANNOTATION_DATA_LINK.STREAM_ID, ANNOTATION_DATA_LINK.EVENT_ID)
                        .from(ANNOTATION_DATA_LINK)
                        .where(ANNOTATION_DATA_LINK.FK_ANNOTATION_ID.eq(annotationId))
                        .orderBy(ANNOTATION_DATA_LINK.STREAM_ID, ANNOTATION_DATA_LINK.EVENT_ID)
                        .fetch())
                .map(record -> new EventId(record.get(ANNOTATION_DATA_LINK.STREAM_ID),
                        record.get(ANNOTATION_DATA_LINK.EVENT_ID)));
    }

    private void linkAnnotations(final String userUuid,
                                 final Instant now,
                                 final long annotationId,
                                 final List<Long> annotationIds) {
        JooqUtil.transaction(connectionProvider, context ->
                createAnnotationLinks(context, userUuid, now, annotationId, annotationIds));
    }

    private void createAnnotationLinks(final DSLContext context,
                                       final String userUuid,
                                       final Instant now,
                                       final long annotationId,
                                       final List<Long> annotationIds) {
        if (NullSafe.isEmptyCollection(annotationIds)) {
            return;
        }

        // Remove self-references
        final List<Long> candidateIds = annotationIds.stream()
                .filter(dstId -> dstId != annotationId)
                .distinct()
                .toList();

        if (candidateIds.isEmpty()) {
            return;
        }

        // Validate all destination annotations exist (prevents FK constraint violations)
        final Set<Long> existingAnnotations = context
                .select(ANNOTATION.ID)
                .from(ANNOTATION)
                .where(ANNOTATION.ID.in(candidateIds))
                .and(ANNOTATION.DELETED.isFalse())
                .fetchSet(ANNOTATION.ID);

        // Filter to only valid destination annotations
        final List<Long> validIds = candidateIds.stream()
                .filter(existingAnnotations::contains)
                .toList();

        if (validIds.isEmpty()) {
            LOGGER.debug(() -> LogUtil.message(
                    "No valid destination annotations found for linking from annotation {}",
                    annotationId));
            return;
        }

        // Log if some annotations were invalid
        if (validIds.size() < candidateIds.size() && LOGGER.isDebugEnabled()) {
            final List<Long> invalidIds = candidateIds.stream()
                    .filter(id -> !existingAnnotations.contains(id))
                    .toList();
            LOGGER.debug(() -> LogUtil.message(
                    "Skipping {} non-existent destination annotations when linking from {}: {}",
                    invalidIds.size(), annotationId, invalidIds));
        }

        processBatches(validIds, BATCH_SIZE, batch -> {
            // Check which links already exist
            final Set<Long> existingLinks = context
                    .select(ANNOTATION_LINK.FK_ANNOTATION_DST_ID)
                    .from(ANNOTATION_LINK)
                    .where(ANNOTATION_LINK.FK_ANNOTATION_SRC_ID.eq(annotationId))
                    .and(ANNOTATION_LINK.FK_ANNOTATION_DST_ID.in(batch))
                    .fetchSet(ANNOTATION_LINK.FK_ANNOTATION_DST_ID);

            // Filter to only new links
            final List<Long> newLinks = batch.stream()
                    .filter(dstId -> !existingLinks.contains(dstId))
                    .toList();

            if (!newLinks.isEmpty()) {
                // Build batch insert
                InsertValuesStep2<AnnotationLinkRecord, Long, Long> insertStep =
                        context.insertInto(ANNOTATION_LINK,
                                ANNOTATION_LINK.FK_ANNOTATION_SRC_ID,
                                ANNOTATION_LINK.FK_ANNOTATION_DST_ID);

                final List<Entry> entries = new ArrayList<>(newLinks.size());
                for (final Long dstId : newLinks) {
                    insertStep = insertStep.values(annotationId, dstId);
                    entries.add(new Entry(now, userUuid, now, userUuid, annotationId,
                            AnnotationEntryType.LINK_ANNOTATION, String.valueOf(dstId)));
                }

                // Execute with onDuplicateKeyUpdate as race condition safety net
                final int result = insertStep
                        .onDuplicateKeyUpdate()
                        .set(ANNOTATION_LINK.FK_ANNOTATION_SRC_ID, ANNOTATION_LINK.FK_ANNOTATION_SRC_ID)
                        .set(ANNOTATION_LINK.FK_ANNOTATION_DST_ID, ANNOTATION_LINK.FK_ANNOTATION_DST_ID)
                        .execute();

                if (result > 0) {
                    // Create history entries for successful inserts
                    createEntries(context, entries);
                }
            }
        });
    }

    private void unlinkAnnotations(final String userUuid,
                                   final Instant now,
                                   final long annotationId,
                                   final List<Long> annotationIds) {
        JooqUtil.transaction(connectionProvider, context ->
                removeAnnotationLinks(context, userUuid, now, annotationId, annotationIds));
    }

    private void removeAnnotationLinks(final DSLContext context,
                                       final String userUuid,
                                       final Instant now,
                                       final long annotationId,
                                       final List<Long> annotationIds) {
        // If we have been given an empty list then exit.
        if (NullSafe.isEmptyCollection(annotationIds)) {
            return;
        }

        processBatches(annotationIds, BATCH_SIZE, batch -> {
            // Only remove existing links.
            final Set<Long> existingLinks = context
                    .select(ANNOTATION_LINK.FK_ANNOTATION_DST_ID)
                    .from(ANNOTATION_LINK)
                    .where(ANNOTATION_LINK.FK_ANNOTATION_SRC_ID.eq(annotationId))
                    .and(ANNOTATION_LINK.FK_ANNOTATION_DST_ID.in(batch))
                    .fetchSet(record -> record.get(ANNOTATION_LINK.FK_ANNOTATION_DST_ID));

            // Filter to only existing links
            final List<Long> existingIds = batch
                    .stream()
                    .filter(existingLinks::contains)
                    .toList();

            if (!existingIds.isEmpty()) {
                // Remove links.
                final int result = context
                        .deleteFrom(ANNOTATION_LINK)
                        .where(ANNOTATION_LINK.FK_ANNOTATION_SRC_ID.eq(annotationId))
                        .and(ANNOTATION_LINK.FK_ANNOTATION_DST_ID.in(existingIds))
                        .execute();

                if (result > 0) {
                    // Create history entries for successful deletes
                    final List<Entry> entries = existingIds.stream().map(dstId ->
                            new Entry(now, userUuid, now, userUuid, annotationId, AnnotationEntryType.UNLINK_ANNOTATION,
                                    String.valueOf(dstId))).toList();
                    createEntries(context, entries);
                }
            }
        });
    }

    private void addAnnotationTable(final String userUuid,
                                    final Instant now,
                                    final long annotationId,
                                    final AddAnnotationTable addAnnotationTable) {
        JooqUtil.context(connectionProvider, context -> createEntry(
                context,
                annotationId,
                userUuid,
                now,
                AnnotationEntryType.ADD_TABLE_DATA,
                JsonUtil.writeValueAsString(addAnnotationTable.getTable())));
    }

    @Override
    public List<Long> getLinkedAnnotations(final DocRef annotationRef) {
        return JooqUtil.contextResult(connectionProvider, context -> {
            final Optional<Long> optionalId = getId(context, annotationRef);
            return optionalId.map(annotationId ->
                            getLinkedAnnotations(context, annotationId))
                    .orElse(Collections.emptyList());
        });
    }

    private List<Long> getLinkedAnnotations(final DSLContext context, final long annotationId) {
        return context
                .select(ANNOTATION_LINK.FK_ANNOTATION_DST_ID)
                .from(ANNOTATION_LINK)
                .where(ANNOTATION_LINK.FK_ANNOTATION_SRC_ID.eq(annotationId))
                .orderBy(ANNOTATION_LINK.FK_ANNOTATION_DST_ID)
                .fetch(ANNOTATION_LINK.FK_ANNOTATION_DST_ID);
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final ValuesConsumer consumer,
                       final Predicate<String> uuidPredicate) {
        final String[] fieldNames = fieldIndex.getFields();
        final Set<String> expressionFields = new HashSet<>(ExpressionUtil.fields(criteria.getExpression()));
        final Condition condition = expressionMapper.apply(criteria.getExpression())
                .and(ANNOTATION.DELETED.isFalse());

        final Set<Field<?>> dbFields = new HashSet<>(valueMapper.getDbFieldsByName(fieldNames));
        final Mapper<?>[] mappers = valueMapper.getMappersForFieldNames(fieldNames);
        dbFields.add(ANNOTATION.UUID);

        final Set<Field<?>> groupBy = new HashSet<>(dbFields);
        groupBy.remove(STATUS_FIELD);
        groupBy.remove(COLLECTION_FIELD);
        groupBy.remove(LABEL_FIELD);
        groupBy.remove(HISTORY_FIELD);

        JooqUtil.context(connectionProvider, context -> {
            SelectJoinStep<?> select = context.select(dbFields)
                    .from(ANNOTATION);

            if (expressionFields.contains(AnnotationFields.STREAM_ID) ||
                expressionFields.contains(AnnotationFields.EVENT_ID) ||
                expressionFields.contains(AnnotationFields.FEED) ||
                dbFields.contains(ANNOTATION_DATA_LINK.STREAM_ID) ||
                dbFields.contains(ANNOTATION_DATA_LINK.EVENT_ID) ||
                dbFields.contains(ANNOTATION_DATA_LINK.FEED_ID)) {
                select = select
                        .leftOuterJoin(ANNOTATION_DATA_LINK)
                        .on(ANNOTATION_DATA_LINK.FK_ANNOTATION_ID.eq(ANNOTATION.ID));
            }

            if (expressionFields.contains(AnnotationFields.STATUS) ||
                expressionFields.contains(AnnotationFields.LABEL) ||
                expressionFields.contains(AnnotationFields.COLLECTION) ||
                dbFields.contains(STATUS_FIELD) ||
                dbFields.contains(LABEL_FIELD) ||
                dbFields.contains(COLLECTION_FIELD)) {
                select = select
                        .leftOuterJoin(ANNOTATION_TAG_LINK)
                        .on(ANNOTATION_TAG_LINK.FK_ANNOTATION_ID.eq(ANNOTATION.ID));
            }

            // Status join.
            if (expressionFields.contains(AnnotationFields.STATUS) ||
                dbFields.contains(STATUS_FIELD)) {
                select = select
                        .leftOuterJoin(STATUS)
                        .on(STATUS.ID.eq(ANNOTATION_TAG_LINK.FK_ANNOTATION_TAG_ID)
                                .and(STATUS.TYPE_ID.eq(AnnotationTagType.STATUS.getPrimitiveValue())));
            }

            // Label join.
            if (expressionFields.contains(AnnotationFields.LABEL) ||
                dbFields.contains(LABEL_FIELD)) {
                select = select
                        .leftOuterJoin(LABEL)
                        .on(LABEL.ID.eq(ANNOTATION_TAG_LINK.FK_ANNOTATION_TAG_ID)
                                .and(LABEL.TYPE_ID.eq(AnnotationTagType.LABEL.getPrimitiveValue())));
            }

            // Collection join.
            if (expressionFields.contains(AnnotationFields.COLLECTION) ||
                dbFields.contains(COLLECTION_FIELD)) {
                select = select
                        .leftOuterJoin(COLLECTION)
                        .on(COLLECTION.ID.eq(ANNOTATION_TAG_LINK.FK_ANNOTATION_TAG_ID)
                                .and(COLLECTION.TYPE_ID.eq(AnnotationTagType.COLLECTION.getPrimitiveValue())));
            }

            // Comment join.
            if (expressionFields.contains(AnnotationFields.COMMENT) ||
                dbFields.contains(COMMENT_FIELD)) {
                select = select
                        .leftOuterJoin(COMMENT).on(
                                COMMENT.ID.eq(context
                                        .select(ANNOTATION_ENTRY.ID)
                                        .from(ANNOTATION_ENTRY)
                                        .where(ANNOTATION_ENTRY.FK_ANNOTATION_ID.eq(ANNOTATION.ID))
                                        .and(ANNOTATION_ENTRY.TYPE_ID
                                                .eq(AnnotationEntryType.COMMENT.getPrimitiveValue()))
                                        .and(ANNOTATION_ENTRY.DELETED.isFalse())
                                        .orderBy(ANNOTATION_ENTRY.ID.desc())
                                        .limit(1)));
            }

            // History join.
            if (expressionFields.contains(AnnotationFields.HISTORY) ||
                dbFields.contains(HISTORY_FIELD)) {
                select = select
                        .leftOuterJoin(HISTORY)
                        .on(HISTORY.FK_ANNOTATION_ID.eq(ANNOTATION.ID)
                                .and(HISTORY.TYPE_ID.eq(AnnotationEntryType.COMMENT.getPrimitiveValue())));
            }

            try (final Cursor<?> cursor = select
                    .where(condition)
                    .groupBy(groupBy)
                    .fetchLazy()) {

                while (cursor.hasNext()) {
                    final Result<?> result = cursor.fetchNext(BATCH_SIZE);
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
        return JooqUtil.contextResult(connectionProvider, context -> commonSelect(context)
                .where(ANNOTATION.ASSIGNED_TO_UUID.eq(userUuid))
                .fetch()
                .map(this::mapToAnnotation));
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

    private String getUserUuid(final UserRef userRef) {
        if (userRef == null) {
            return null;
        } else {
            return userRef.getUuid();
        }
    }

    @Override
    public boolean logicalDelete(final DocRef annotationRef,
                                 final UserRef currentUser) {
        final String userUuid = currentUser.getUuid();
        final String userName = currentUser.toDisplayString();
        final Instant now = Instant.now();
        final Optional<Long> optionalId = getId(annotationRef);
        return optionalId.map(id ->
                        logicalDelete(id, userName, userUuid, now, "Deleted by user"))
                .orElse(false);
    }

    @Override
    public LongList markDeletedByDataRetention() {
        return markDeletedByDataRetention(BATCH_SIZE);
    }

    @SuppressWarnings("SameParameterValue")
    LongList markDeletedByDataRetention(final int batchSize) {
        final Instant now = Instant.now();
        LOGGER.debug("markDeletedByDataRetention() - now: {}", now);
        boolean keepGoing = true;
        final LongList deletedIds = new LongArrayList(batchSize);
        while (keepGoing) {
            final LongList ids = getAnnotationsPastRetention(now, batchSize);
            LOGGER.debug(() -> LogUtil.message("markDeletedByDataRetention() - now: {}, batchSize, ids.size: {}",
                    now, batchSize, ids.size()));
            keepGoing = ids.size() == batchSize;
            ids.forEach(id -> {
                final boolean didDelete = logicalDelete(
                        id,
                        DATA_RETENTION_USER_NAME,
                        DATA_RETENTION_USER_NAME,  // Using a username for a UUID, not ideal
                        now,
                        "Deleted by data retention");
                if (didDelete) {
                    deletedIds.add(id);
                }
            });
        }
        LOGGER.debug(() -> LogUtil.message("markDeletedByDataRetention() - deletedIds: {}", deletedIds.size()));
        return deletedIds;
    }

    @SuppressWarnings("SameParameterValue")
    private LongList getAnnotationsPastRetention(final Instant now,
                                                 final int batchSize) {
        final LongList ids = JooqUtil.contextResult(connectionProvider, context -> {
            try {
                return JooqUtil.fetchIds(batchSize, () ->
                        context.select(ANNOTATION.ID)
                                .from(ANNOTATION)
                                .where(ANNOTATION.DELETED.isFalse())
                                .and(ANNOTATION.RETAIN_UNTIL_MS.isNotNull())
                                .and(ANNOTATION.RETAIN_UNTIL_MS.lt(now.toEpochMilli()))
                                .limit(batchSize)
                                .fetchLazy());
            } catch (final Exception e) {
                throw new RuntimeException(LogUtil.message("Error fetching annotations past retention," +
                                                           "now: {}, batchSize: {} - {}",
                        now, batchSize, LogUtil.exceptionMessage(e)), e);
            }
        });
        LOGGER.debug(() -> LogUtil.message("getAnnotationsPastRetention() - now: {}, batchSize: {}, ids.size: {}",
                now, batchSize, ids.size()));
        return ids;
    }

    @NullMarked
    private boolean logicalDelete(final long annotationId,
                                  final String userName,
                                  final String userUuid,
                                  final Instant now,
                                  @Nullable final String message) {
        return JooqUtil.transactionResult(connectionProvider, context -> {
            final int count = context
                    .update(ANNOTATION)
                    .set(ANNOTATION.DELETED, true)
                    .set(ANNOTATION.UPDATE_USER, userName)
                    .set(ANNOTATION.UPDATE_TIME_MS, now.toEpochMilli())
                    .where(ANNOTATION.ID.eq(annotationId))
                    .and(ANNOTATION.DELETED.eq(false))
                    .execute();

            // Remember that this annotation was marked deleted.
            if (count > 0) {
                createEntry(context,
                        annotationId,
                        userUuid,
                        now,
                        AnnotationEntryType.DELETE,
                        message);
            }

            return count > 0;
        });
    }

    @Override
    public LongList physicallyDelete(final Instant age) {
        return physicallyDelete(age, BATCH_SIZE);
    }

    LongList physicallyDelete(final Instant age, final int batchSize) {
        LOGGER.debug("physicallyDelete() - age: {}, batchSize: {}", age, batchSize);
        // We may delete more than batchSize, but it will do as a starting size
        final LongList deletedIds = new LongArrayList(batchSize);

        JooqUtil.transaction(connectionProvider, txnContext -> {

            // Lambda to fetch a batch of anno IDs to delete
            final Supplier<LongList> batchSupplier = () -> JooqUtil.fetchIds(
                    batchSize,
                    () -> txnContext.select(ANNOTATION.ID)
                            .from(ANNOTATION)
                            .where(ANNOTATION.DELETED.eq(true))
                            .and(ANNOTATION.UPDATE_TIME_MS.lt(age.toEpochMilli()))
                            .limit(batchSize)
                            .fetchLazy());

            // Lambda to do delete records from 4 tables where the id is in the batch
            final Consumer<LongList> batchConsumer = ids -> {
                // Saves us unboxing 4 times. Jooq doesn't support
                final List<Long> boxedIds = new ArrayList<>(ids);
                final int linkCount = txnContext.deleteFrom(ANNOTATION_DATA_LINK)
                        .where(ANNOTATION_DATA_LINK.FK_ANNOTATION_ID.in(boxedIds))
                        .execute();
                final int entryCount = txnContext.deleteFrom(ANNOTATION_ENTRY)
                        .where(ANNOTATION_ENTRY.FK_ANNOTATION_ID.in(boxedIds))
                        .execute();
                final int tagLinkCount = txnContext.deleteFrom(ANNOTATION_TAG_LINK)
                        .where(ANNOTATION_TAG_LINK.FK_ANNOTATION_ID.in(boxedIds))
                        .execute();
                final int annoCount = txnContext.deleteFrom(ANNOTATION)
                        .where(ANNOTATION.ID.in(boxedIds))
                        .execute();

                // MySQL can't do returning from a delete stmt, so ensure we deleted the expected number.
                if (annoCount != boxedIds.size()) {
                    throw new RuntimeException(LogUtil.message(
                            "Expecting to have deleted {} annotation records, actually deleted {}",
                            annoCount, boxedIds.size()));
                }
                final LongList batchDeletedIds = new LongArrayList(boxedIds);
                LOGGER.debug(() -> LogUtil.message(
                        "physicallyDelete() - age: {}, batchSize: {}, ids.size: {}, linkCount: {}, " +
                        "entryCount: {}, tagLinkCount: {}, batchProcessById.size: {}",
                        age, batchSize, ids.size(), linkCount, entryCount,
                        tagLinkCount, batchDeletedIds.size()));

                deletedIds.addAll(batchDeletedIds);
            };

            JooqUtil.batchProcessById(batchSize, batchSupplier, batchConsumer);
        });
        return deletedIds;
    }

    @Override
    public void clear() {
        LOGGER.debug("clear()");
        JooqUtil.transaction(connectionProvider, context -> {
            context.deleteFrom(ANNOTATION_DATA_LINK).execute();
            context.deleteFrom(ANNOTATION_TAG_LINK).execute();
            context.deleteFrom(ANNOTATION_ENTRY).execute();
            context.deleteFrom(ANNOTATION).execute();
        });
    }

    @Override
    public AnnotationEntry fetchAnnotationEntry(final DocRef annotationRef,
                                                final UserRef currentUser,
                                                final long entryId) {
        return JooqUtil.contextResult(connectionProvider, context -> {
            final Optional<Long> optionalId = getId(context, annotationRef);
            final long annotationId = optionalId.orElseThrow(() ->
                    new RuntimeException("Unable to fetch entry for unknown annotation"));

            return context.selectFrom(ANNOTATION_ENTRY)
                    .where(ANNOTATION_ENTRY.FK_ANNOTATION_ID.eq(annotationId))
                    .and(ANNOTATION_ENTRY.ID.eq(entryId))
                    .fetchOptional()
                    .map(this::mapToAnnotationEntry)
                    .orElseThrow(() -> new RuntimeException("Unable to find entry"));
        });
    }

    @Override
    public boolean changeAnnotationEntry(final DocRef annotationRef,
                                         final UserRef currentUser,
                                         final long entryId,
                                         final String data) {
        final Optional<Long> optionalId = getId(annotationRef);
        final long annotationId = optionalId.orElseThrow(() ->
                new RuntimeException("Unable to change entry for unknown annotation"));

        // Get the entry first.
        return JooqUtil.transactionResult(connectionProvider, context -> {
            final Optional<AnnotationEntryRecord> optional = context.selectFrom(ANNOTATION_ENTRY)
                    .where(ANNOTATION_ENTRY.FK_ANNOTATION_ID.eq(annotationId))
                    .and(ANNOTATION_ENTRY.ID.eq(entryId))
                    .fetchOptional();

            final AnnotationEntryRecord record = optional
                    .orElseThrow(() -> new RuntimeException("Unable to change missing entry for annotation"));

            final long now = System.currentTimeMillis();

            // Add an entry for the previous version.
            context.insertInto(ANNOTATION_ENTRY,
                            ANNOTATION_ENTRY.ENTRY_TIME_MS,
                            ANNOTATION_ENTRY.ENTRY_USER_UUID,
                            ANNOTATION_ENTRY.UPDATE_TIME_MS,
                            ANNOTATION_ENTRY.UPDATE_USER_UUID,
                            ANNOTATION_ENTRY.PARENT_ID,
                            ANNOTATION_ENTRY.FK_ANNOTATION_ID,
                            ANNOTATION_ENTRY.TYPE_ID,
                            ANNOTATION_ENTRY.DATA,
                            ANNOTATION_ENTRY.DELETED)
                    .values(record.get(ANNOTATION_ENTRY.ENTRY_TIME_MS),
                            record.get(ANNOTATION_ENTRY.ENTRY_USER_UUID),
                            now,
                            currentUser.getUuid(),
                            entryId,
                            annotationId,
                            record.get(ANNOTATION_ENTRY.TYPE_ID),
                            record.get(ANNOTATION_ENTRY.DATA),
                            true)
                    .execute();

            // Update the existing entry for the change.
            return context.update(ANNOTATION_ENTRY)
                           .set(ANNOTATION_ENTRY.UPDATE_TIME_MS, now)
                           .set(ANNOTATION_ENTRY.UPDATE_USER_UUID, currentUser.getUuid())
                           .set(ANNOTATION_ENTRY.DATA, data)
                           .where(ANNOTATION_ENTRY.ID.eq(entryId))
                           .execute() != 0;
        });
    }

    @Override
    public boolean logicalDeleteEntry(final DocRef annotationRef,
                                      final UserRef currentUser,
                                      final long entryId) {
        return JooqUtil.contextResult(connectionProvider, context -> {
            final Optional<Long> optionalId = getId(context, annotationRef);
            final long annotationId = optionalId.orElseThrow(() ->
                    new RuntimeException("Unable to delete entry for unknown annotation"));
            final long now = System.currentTimeMillis();

            return context.update(ANNOTATION_ENTRY)
                           .set(ANNOTATION_ENTRY.UPDATE_TIME_MS, now)
                           .set(ANNOTATION_ENTRY.UPDATE_USER_UUID, currentUser.getUuid())
                           .set(ANNOTATION_ENTRY.DELETED, true)
                           .where(ANNOTATION_ENTRY.FK_ANNOTATION_ID.eq(annotationId))
                           .and(ANNOTATION_ENTRY.ID.eq(entryId))
                           .execute() != 0;
        });
    }

    /**
     * Process items in batches of the specified size.
     * <p>
     * The batch processor receives a view of the list via {@link List#subList(int, int)},
     * which is efficient but should not be modified.
     *
     * @param items          The items to process (must not be modified during processing)
     * @param batchSize      The maximum size of each batch
     * @param batchProcessor Consumer that processes each batch
     * @param <T>            The type of items being processed
     */
    private <T> void processBatches(final List<T> items,
                                    final int batchSize,
                                    final Consumer<List<T>> batchProcessor) {
        for (int offset = 0; offset < items.size(); offset += batchSize) {
            final int end = Math.min(offset + batchSize, items.size());
            final List<T> batch = items.subList(offset, end);
            batchProcessor.accept(batch);
        }
    }

    private record Entry(
            Instant entryTime,
            String entryUserUuid,
            Instant updateTime,
            String updateUserUuid,
            long annotationId,
            AnnotationEntryType type,
            @Nullable String entryData) {

    }
}
