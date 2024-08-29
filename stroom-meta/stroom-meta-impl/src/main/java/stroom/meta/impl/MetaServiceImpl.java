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

package stroom.meta.impl;

import stroom.data.retention.api.DataRetentionRuleAction;
import stroom.data.retention.api.DataRetentionTracker;
import stroom.data.retention.shared.DataRetentionDeleteSummary;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.retention.shared.FindDataRetentionImpactCriteria;
import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.EffectiveMetaDataCriteria;
import stroom.meta.api.EffectiveMetaSet;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaSecurityFilter;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.SelectionSummary;
import stroom.meta.shared.SimpleMeta;
import stroom.meta.shared.Status;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Builder;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.common.v2.FieldInfoResultPageBuilder;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.searchable.api.Searchable;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.PermissionNames;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.shared.string.CIKey;
import stroom.util.time.TimePeriod;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class MetaServiceImpl implements MetaService, Searchable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MetaServiceImpl.class);

    private static final DocRef META_STORE_PSEUDO_DOC_REF = new DocRef("Searchable", "Meta Store", "Meta Store");
    private static final List<String> FEED_FIELDS = List.of(MetaFields.FIELD_FEED);
    private static final List<String> ALL_FEED_FIELDS = List.of(MetaFields.FIELD_FEED, MetaFields.FIELD_PARENT_FEED);

    private final MetaDao metaDao;
    private final MetaFeedDao metaFeedDao;
    private final MetaValueDao metaValueDao;
    private final MetaRetentionTrackerDao metaRetentionTrackerDao;
    private final Provider<MetaServiceConfig> metaServiceConfigProvider;
    private final DocRefInfoService docRefInfoService;
    private final Provider<StreamAttributeMapRetentionRuleDecorator> decoratorProvider;
    private final Optional<MetaSecurityFilter> metaSecurityFilter;
    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;
    private final UserQueryRegistry userQueryRegistry;
    private final TaskManager taskManager;

    @Inject
    MetaServiceImpl(final MetaDao metaDao,
                    final MetaFeedDao metaFeedDao,
                    final MetaValueDao metaValueDao,
                    final MetaRetentionTrackerDao metaRetentionTrackerDao,
                    final Provider<MetaServiceConfig> metaServiceConfigProvider,
                    final DocRefInfoService docRefInfoService,
                    final Provider<StreamAttributeMapRetentionRuleDecorator> decoratorProvider,
                    final Optional<MetaSecurityFilter> metaSecurityFilter,
                    final SecurityContext securityContext,
                    final TaskContextFactory taskContextFactory,
                    final UserQueryRegistry userQueryRegistry,
                    final TaskManager taskManager) {
        this.metaDao = metaDao;
        this.metaFeedDao = metaFeedDao;
        this.metaValueDao = metaValueDao;
        this.metaRetentionTrackerDao = metaRetentionTrackerDao;
        this.metaServiceConfigProvider = metaServiceConfigProvider;
        this.docRefInfoService = docRefInfoService;
        this.decoratorProvider = decoratorProvider;
        this.metaSecurityFilter = metaSecurityFilter;
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
        this.userQueryRegistry = userQueryRegistry;
        this.taskManager = taskManager;
    }

    @Override
    public Long getMaxId() {
        return metaDao.getMaxId();
    }

    @Override
    public Meta create(final MetaProperties metaProperties) {
        return metaDao.create(metaProperties);
    }

    @Override
    public Meta getMeta(final long id) {
        return getMeta(id, false);
    }

    @Override
    public Meta getMeta(final long id, final boolean anyStatus) {
        final ExpressionOperator secureExpression = addPermissionConstraints(getIdExpression(id, anyStatus),
                DocumentPermissionNames.READ,
                FEED_FIELDS);
        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria(secureExpression);
        findMetaCriteria.setPageRequest(PageRequest.oneRow());
        final List<Meta> list = find(findMetaCriteria).getValues();
        if (list == null || list.size() == 0) {
            return null;
        }
        return list.get(0);
    }

    @Override
    public Meta updateStatus(final Meta meta, final Status currentStatus, final Status newStatus) {
        Objects.requireNonNull(meta, "Null data");

        final long now = System.currentTimeMillis();
        final int result = updateStatus(
                meta.getId(),
                currentStatus,
                newStatus,
                now,
                DocumentPermissionNames.UPDATE);
        if (result > 0) {
            return meta
                    .copy()
                    .status(newStatus)
                    .statusMs(now)
                    .build();
        } else {
            final Meta existingMeta = getMeta(meta.getId(), true);
            if (existingMeta == null) {
                throw new RuntimeException("Meta with id=" + meta.getId() + " does not exist");
            }

            if (currentStatus != existingMeta.getStatus()) {
                throw new RuntimeException("Unexpected status " +
                        existingMeta.getStatus() +
                        " (expected " +
                        currentStatus +
                        ")");
            }

            return null;
        }
    }

    private int updateStatus(final long id,
                             final Status currentStatus,
                             final Status newStatus,
                             final long statusTime,
                             final String permission) {
        final ExpressionOperator expression = getIdExpression(id, true);
        final ExpressionOperator secureExpression = addPermissionConstraints(expression, permission, FEED_FIELDS);
        final FindMetaCriteria criteria = new FindMetaCriteria(secureExpression);

        return metaDao.updateStatus(criteria, currentStatus, newStatus, statusTime, true);
    }

    @Override
    public int updateStatus(final FindMetaCriteria criteria, final Status currentStatus, final Status newStatus) {
        return securityContext.secureResult(() -> {
            // Decide which permission is needed for this update as logical deletes require delete permissions.
            final String permission = Status.DELETED.equals(newStatus)
                    ? DocumentPermissionNames.DELETE
                    : DocumentPermissionNames.UPDATE;
            ExpressionOperator expression = criteria.getExpression();

            final Predicate<ExpressionTerm> termPredicate = term ->
                    MetaFields.ID.getFldName().equals(term.getField())
                            && term.hasCondition(Condition.EQUALS, Condition.IN);

            // The UI may give us one of:
            // * EQUALS on one unique id (user checked one stream)
            // * IN for N unique IDs (user checked >1 stream)
            // * A complex user provided filter expression containing who knows what
            // For the latter we need to use a batch wise approach that is less likely to lock other rows.
            final boolean usesUniqueIds = expression != null
                    && expression.getChildren().size() == 1
                    && expression.containsTerm(termPredicate);

            expression = addPermissionConstraints(expression,
                    permission,
                    FEED_FIELDS);
            criteria.setExpression(expression);

            return metaDao.updateStatus(
                    criteria,
                    currentStatus,
                    newStatus,
                    System.currentTimeMillis(),
                    usesUniqueIds);
        });
    }

    @Override
    public void addAttributes(final Meta meta, final AttributeMap attributes) {
        metaValueDao.addAttributes(meta, attributes);
    }

    @Override
    public int delete(final long id) {
        return securityContext.secureResult(PermissionNames.DELETE_DATA_PERMISSION, () ->
                doLogicalDelete(id, true));
    }

    @Override
    public int delete(final List<DataRetentionRuleAction> ruleActions,
                      final TimePeriod period) {
        return securityContext.secureResult(PermissionNames.DELETE_DATA_PERMISSION, () -> {
            if (ruleActions != null && !ruleActions.isEmpty()) {
                return metaDao.logicalDelete(ruleActions, period);
            } else {
                return 0;
            }
        });
    }

    @Override
    public int delete(final long id, final boolean lockCheck) {
        return securityContext.secureResult(PermissionNames.DELETE_DATA_PERMISSION,
                () -> doLogicalDelete(id, lockCheck));
    }

    private int doLogicalDelete(final long id, final boolean lockCheck) {
        if (lockCheck) {
            final Meta meta = getMeta(id, true);

            // Don't bother to try and set the status of deleted data to be deleted.
            if (Status.DELETED.equals(meta.getStatus())) {
                return 0;
            }

            // Don't delete if the data is not unlocked and we are checking for unlocked.
            if (!Status.UNLOCKED.equals(meta.getStatus())) {
                return 0;
            }
        }

        // Ensure the user has permission to delete this data.
        final long now = System.currentTimeMillis();

        return updateStatus(
                id,
                null,
                Status.DELETED,
                now,
                DocumentPermissionNames.DELETE);
    }

    @Override
    public DocRef getDocRef() {
        return META_STORE_PSEUDO_DOC_REF;
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        return FieldInfoResultPageBuilder.builder(criteria).addAll(MetaFields.getAllFields()).build();
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return Optional.empty();
    }

    @Override
    public QueryField getTimeField() {
        return MetaFields.CREATE_TIME;
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final ValuesConsumer consumer) {
        LOGGER.logDurationIfTraceEnabled(() -> {
            final ExpressionOperator expression = addPermissionConstraints(criteria.getExpression(),
                    DocumentPermissionNames.READ,
                    FEED_FIELDS);
            criteria.setExpression(expression);
            metaDao.search(criteria, fieldIndex, consumer);
        }, "Searching meta");
    }

    @Override
    public ResultPage<Meta> find(final FindMetaCriteria criteria) {
        return LOGGER.logDurationIfTraceEnabled(
                () -> {
                    final boolean fetchRelationships = criteria.isFetchRelationships();
                    final PageRequest pageRequest = criteria.getPageRequest();
                    if (fetchRelationships) {
                        criteria.setPageRequest(null);
                    }

                    final ResultPage<Meta> resultPage = secureFind(criteria);

                    // Only return back children or parents?
                    if (fetchRelationships) {
                        final List<Meta> workingList = resultPage.getValues();

                        List<Meta> results = new ArrayList<>();

                        for (final Meta stream : workingList) {
                            Meta parent = stream;
                            Meta lastParent = parent;

                            // Walk up to the root of the tree
                            while (parent.getParentMetaId() != null && (parent = findParent(parent)) != null) {
                                lastParent = parent;
                            }

                            // Add the match
                            results.add(lastParent);

                            // Add the children
                            ResultPage<Meta> children = findChildren(criteria, Collections.singletonList(lastParent));
                            while (children.size() > 0) {
                                results.addAll(children.getValues());
                                children = findChildren(criteria, children.getValues());
                            }
                        }

                        final long maxSize = results.size();
                        if (pageRequest != null && pageRequest.getOffset() != null) {
                            // Move by an offset?
                            if (pageRequest.getOffset() > 0) {
                                results = results.subList(pageRequest.getOffset(), results.size());
                            }
                        }
                        if (pageRequest != null && pageRequest.getLength() != null) {
                            if (results.size() > pageRequest.getLength()) {
                                results = results.subList(0, pageRequest.getLength() + 1);
                            }
                        }
                        criteria.setPageRequest(pageRequest);
                        return ResultPage.createCriterialBasedList(results, criteria, maxSize);
                    } else {
                        return resultPage;
                    }
                },
                "Finding meta");
    }

    private ResultPage<Meta> secureFind(final FindMetaCriteria criteria) {
        final ExpressionOperator expression = addPermissionConstraints(
                criteria.getExpression(),
                DocumentPermissionNames.READ,
                FEED_FIELDS);
        criteria.setExpression(expression);
        return metaDao.find(criteria);
    }

    private ResultPage<Meta> findChildren(final FindMetaCriteria parentCriteria, final List<Meta> streamList) {
        final Set<String> excludedFields = Set.of(MetaFields.ID.getFldName(), MetaFields.PARENT_ID.getFldName());
        final Builder builder = copyExpression(parentCriteria.getExpression(), excludedFields);

        final String parentIds = streamList.stream()
                .map(meta -> String.valueOf(meta.getId()))
                .collect(Collectors.joining(","));
        builder.addTerm(MetaFields.PARENT_ID.getFldName(), ExpressionTerm.Condition.IN, parentIds);

        return simpleFind(builder.build());
    }

    private Meta findParent(final Meta meta) {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addIdTerm(MetaFields.ID, ExpressionTerm.Condition.EQUALS, meta.getParentMetaId())
                .build();
        final ResultPage<Meta> parentList = simpleFind(expression);
        if (parentList != null && parentList.size() > 0) {
            return parentList.getFirst();
        }
        return Meta
                .builder()
                .id(meta.getParentMetaId())
                .build();
    }

    private ResultPage<Meta> simpleFind(final ExpressionOperator expression) {
        final FindMetaCriteria criteria = new FindMetaCriteria(expression);
        final ExpressionOperator secureExpression = addPermissionConstraints(expression,
                DocumentPermissionNames.READ,
                FEED_FIELDS);
        criteria.setExpression(secureExpression);
        return metaDao.find(criteria);
    }

    private Builder copyExpression(final ExpressionOperator expressionOperator, final Set<String> excludedFields) {
        final Builder builder = ExpressionOperator
                .builder()
                .op(expressionOperator.op())
                .enabled(expressionOperator.enabled());
        if (expressionOperator.getChildren() != null) {
            expressionOperator.getChildren().forEach(expressionItem -> {
                if (expressionItem instanceof ExpressionTerm) {
                    final ExpressionTerm expressionTerm = (ExpressionTerm) expressionItem;
                    if (!excludedFields.contains(expressionTerm.getField())) {
                        builder.addTerm(expressionTerm);
                    }
                } else if (expressionItem instanceof ExpressionOperator) {
                    final ExpressionOperator operator = (ExpressionOperator) expressionItem;
                    builder.addOperator(copyExpression(operator, excludedFields).build());
                }
            });
        }
        return builder;
    }

    @Override
    public EffectiveMetaSet findEffectiveData(final EffectiveMetaDataCriteria criteria) {
        LOGGER.debug("findEffectiveData({})", criteria);

        return securityContext.asProcessingUserResult(() ->
                metaDao.getEffectiveStreams(criteria));
    }

    @Override
    public Set<String> getFeeds() {
        return new HashSet<>(metaFeedDao.list());
    }

    @Override
    public Set<String> getTypes() {
        return metaServiceConfigProvider.get().getMetaTypes();
    }

    @Override
    public Set<String> getRawTypes() {
        return metaServiceConfigProvider.get().getRawMetaTypes();
    }

    @Override
    public int getLockCount() {
        return metaDao.getLockCount();
    }

    @Override
    public ResultPage<MetaRow> findRows(final FindMetaCriteria criteria) {
        return securityContext.useAsReadResult(() -> {
            final ResultPage<Meta> resultPage = find(criteria);
            final List<MetaRow> result = decorate(resultPage.getValues());
            return new ResultPage<>(result, ResultPage.createPageResponse(result, resultPage.getPageResponse()));
        });
    }

    @Override
    public ResultPage<MetaRow> findDecoratedRows(final FindMetaCriteria criteria) {
        try {
            final ResultPage<MetaRow> list = findRows(criteria);

            LOGGER.logDurationIfTraceEnabled(
                    () -> {
                        final StreamAttributeMapRetentionRuleDecorator decorator = decoratorProvider.get();
                        list.getValues().forEach(metaRow ->
                                decorator.addMatchingRetentionRuleInfo(
                                        metaRow.getMeta(),
                                        metaRow.getAttributes()));
                    },
                    "Adding data retention rules");

            return list;
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public List<MetaRow> findRelatedData(final long id, final boolean anyStatus) {
        // Get the starting row.
        final FindMetaCriteria findDataCriteria = new FindMetaCriteria(getIdExpression(id, anyStatus));
        ResultPage<Meta> rows = find(findDataCriteria);
        final List<Meta> result = new ArrayList<>(rows.getValues());

        if (rows.size() > 0) {
            Meta row = rows.getFirst();
            LOGGER.logDurationIfTraceEnabled(
                    () -> addChildren(row, anyStatus, result),
                    "Adding children");

            LOGGER.logDurationIfTraceEnabled(
                    () -> addParents(row, anyStatus, result),
                    "Adding parents");
        }

        result.sort(Comparator.comparing(Meta::getId));
        return decorate(result);
    }

    @Override
    public ResultPage<Meta> findReprocess(final FindMetaCriteria criteria) {
        final ExpressionOperator expression = addPermissionConstraints(criteria.getExpression(),
                DocumentPermissionNames.READ,
                ALL_FEED_FIELDS);
        criteria.setExpression(expression);
        return metaDao.findReprocess(criteria);
    }

    private List<MetaRow> decorate(final List<Meta> metaList) {
        return LOGGER.logDurationIfTraceEnabled(
                () -> {
                    if (NullSafe.isEmptyCollection(metaList)) {
                        return Collections.emptyList();
                    }

                    LOGGER.debug("Loading attribute map from DB");
                    final Map<Long, Map<CIKey, String>> attributeMaps = metaValueDao.getAttributes(metaList);
                    final List<MetaRow> metaRowList = new ArrayList<>(metaList.size());
                    for (final Meta meta : metaList) {
                        final Map<CIKey, String> attributes = attributeMaps.getOrDefault(
                                meta.getId(),
                                new HashMap<>());
                        metaRowList.add(new MetaRow(
                                meta,
                                getPipelineName(meta),
                                CIKey.convertToStringMap(attributes)));
                    }
                    return metaRowList;
                },
                "Decorating meta");
    }

    @Override
    public SelectionSummary getSelectionSummary(final FindMetaCriteria criteria) {
        final ExpressionOperator expression = addPermissionConstraints(criteria.getExpression(),
                DocumentPermissionNames.READ,
                FEED_FIELDS);
        criteria.setExpression(expression);
        return metaDao.getSelectionSummary(criteria);
    }

    @Override
    public SelectionSummary getReprocessSelectionSummary(final FindMetaCriteria criteria) {
        final ExpressionOperator expression = addPermissionConstraints(criteria.getExpression(),
                DocumentPermissionNames.READ,
                ALL_FEED_FIELDS);
        criteria.setExpression(expression);
        return metaDao.getReprocessSelectionSummary(criteria);
    }

    private String getPipelineName(final Meta meta) {
        if (meta.getPipelineUuid() != null) {
            return docRefInfoService
                    .name(new DocRef("Pipeline", meta.getPipelineUuid()))
                    .orElse(null);
        }
        return null;
    }

    private void addChildren(final Meta parent, final boolean anyStatus, final List<Meta> result) {
        final List<Meta> children = find(new FindMetaCriteria(getParentIdExpression(parent.getId(),
                anyStatus))).getValues();
        children.forEach(child -> {
            result.add(child);
            addChildren(child, anyStatus, result);
        });
    }

    private void addParents(final Meta child, final boolean anyStatus, final List<Meta> result) {
        if (child.getParentMetaId() != null) {
            final List<Meta> parents = find(new FindMetaCriteria(getIdExpression(child.getParentMetaId(),
                    anyStatus))).getValues();
            if (NullSafe.hasItems(parents)) {
                parents.forEach(parent -> {
                    result.add(parent);
                    addParents(parent, anyStatus, result);
                });
            } else {
                // Add a dummy parent data as we don't seem to be able to get the real parent.
                // This might be because it is deleted or the user does not have access permissions.
                final Meta meta = Meta
                        .builder()
                        .id(child.getParentMetaId())
                        .build();
                result.add(meta);
            }
        }
    }

    private ExpressionOperator getIdExpression(final long id, final boolean anyStatus) {
        if (anyStatus) {
            return ExpressionOperator.builder()
                    .addIdTerm(MetaFields.ID, ExpressionTerm.Condition.EQUALS, id)
                    .build();
        }

        return ExpressionOperator.builder()
                .addIdTerm(MetaFields.ID, ExpressionTerm.Condition.EQUALS, id)
                .addTextTerm(MetaFields.STATUS, ExpressionTerm.Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
    }

    private ExpressionOperator getParentIdExpression(final long id, final boolean anyStatus) {
        if (anyStatus) {
            return ExpressionOperator.builder()
                    .addIdTerm(MetaFields.PARENT_ID, ExpressionTerm.Condition.EQUALS, id)
                    .build();
        }

        return ExpressionOperator.builder()
                .addIdTerm(MetaFields.PARENT_ID, ExpressionTerm.Condition.EQUALS, id)
                .addTextTerm(MetaFields.STATUS, ExpressionTerm.Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
    }

    private ExpressionOperator addPermissionConstraints(final ExpressionOperator expression,
                                                        final String permission,
                                                        final List<String> fields) {
        return metaSecurityFilter.map(msf -> {
            final ExpressionOperator filter = msf
                    .getExpression(permission, fields)
                    .orElse(null);

            if (expression == null) {
                return filter;
            }

            if (filter != null) {
                final ExpressionOperator.Builder builder = ExpressionOperator.builder();
                builder.addOperator(expression);
                builder.addOperator(filter);
                return builder.build();
            }

            return expression;
        }).orElse(expression);
    }

    @Override
    public List<DataRetentionTracker> getRetentionTrackers() {
        return metaRetentionTrackerDao.getTrackers();
    }

    @Override
    public void deleteTrackers(final String rulesVersion) {
        metaRetentionTrackerDao.deleteTrackers(rulesVersion);
    }

    @Override
    public void setTracker(final DataRetentionTracker dataRetentionTracker) {
        metaRetentionTrackerDao.createOrUpdate(dataRetentionTracker);
    }

    @Override
    public List<DataRetentionDeleteSummary> getRetentionDeleteSummary(final String queryId,
                                                                      final DataRetentionRules rules,
                                                                      final FindDataRetentionImpactCriteria criteria) {
        return securityContext.secureResult(PermissionNames.MANAGE_POLICIES_PERMISSION, () -> {

            final String userId = securityContext.getSubjectId();

            List<DataRetentionDeleteSummary> summaries;
            try {
                final CompletableFuture<List<DataRetentionDeleteSummary>> future = CompletableFuture.supplyAsync(
                        taskContextFactory.contextResult(
                                "Data retention Delete Summary Query", taskContext -> {

                                    LOGGER.debug("Starting task {}", taskContext.getTaskId());
                                    userQueryRegistry.registerQuery(userId, queryId, taskContext.getTaskId());

                                    // TODO remove, here for dev testing to add a big delay for cancellation testing
//                            try {
//                                Thread.sleep(10_000_000);
//                            } catch (InterruptedException e) {
//                                Thread.currentThread().interrupt();
//                            }

                                    return metaDao.getRetentionDeletionSummary(rules, criteria);
                                }));

                try {
                    // Wait for completion
                    summaries = future.get();
                } catch (InterruptedException e) {
                    LOGGER.debug("Thread interrupted");
                    summaries = Collections.emptyList();
                } catch (CancellationException e) {
                    LOGGER.debug("Query cancelled");
                    summaries = Collections.emptyList();
                } catch (ExecutionException e) {
                    if (e.getCause() != null) {
                        if (e.getCause() instanceof RuntimeException) {
                            throw (RuntimeException) e.getCause();
                        } else {
                            throw new RuntimeException(e);
                        }
                    } else {
                        throw new RuntimeException(e);
                    }
                }
            } finally {
                userQueryRegistry.deRegisterQuery(userId, queryId);
            }
            return summaries;
        });
    }

    @Override
    public boolean cancelRetentionDeleteSummary(final String queryId) {
        return securityContext.secureResult(PermissionNames.MANAGE_POLICIES_PERMISSION, () ->
                userQueryRegistry.terminateQuery(securityContext.getSubjectId(), queryId, taskManager));
    }

    @Override
    public List<SimpleMeta> getLogicallyDeleted(final Instant deleteThreshold,
                                                final int batchSize,
                                                final Set<Long> metaIdExcludeSet) {
        return metaDao.getLogicallyDeleted(deleteThreshold, batchSize, metaIdExcludeSet);
    }

    @Override
    public List<SimpleMeta> findBatch(final long minId, final Long maxId, final int batchSize) {
        return metaDao.findBatch(minId, maxId, batchSize);
    }

    @Override
    public Set<Long> exists(final Set<Long> ids) {
        return metaDao.exists(ids);
    }

    @Override
    public List<String> getProcessorUuidList(final FindMetaCriteria criteria) {
        return metaDao.getProcessorUuidList(criteria);
    }

    @Override
    public Set<Long> findLockedMeta(final Collection<Long> metaIdCollection) {
        return metaDao.findLockedMeta(metaIdCollection);
    }
}
