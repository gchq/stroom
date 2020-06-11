package stroom.meta.impl;

import stroom.dashboard.expression.v1.Val;
import stroom.data.retention.api.DataRetentionRuleAction;
import stroom.data.retention.api.DataRetentionTracker;
import stroom.data.retention.shared.DataRetentionDeleteSummary;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.retention.shared.FindDataRetentionImpactCriteria;
import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.docstore.shared.DocRefUtil;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapFactory;
import stroom.meta.api.EffectiveMetaDataCriteria;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaSecurityFilter;
import stroom.meta.api.MetaService;
import stroom.meta.shared.DataRetentionFields;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaInfoSection;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.SelectionSummary;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Builder;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.searchable.api.Searchable;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.PermissionNames;
import stroom.util.date.DateUtil;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;
import stroom.util.time.TimePeriod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class MetaServiceImpl implements MetaService, Searchable {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetaServiceImpl.class);

    private static final DocRef META_STORE_PSEUDO_DOC_REF = new DocRef("Searchable", "Meta Store", "Meta Store");
    private static final List<String> FEED_FIELDS = List.of(MetaFields.FIELD_FEED);
    private static final List<String> ALL_FEED_FIELDS = List.of(MetaFields.FIELD_FEED, MetaFields.FIELD_PARENT_FEED);

    private final MetaDao metaDao;
    private final MetaFeedDao metaFeedDao;
    private final MetaTypeDao metaTypeDao;
    private final MetaValueDao metaValueDao;
    private final MetaRetentionTrackerDao metaRetentionTrackerDao;
    private final DocRefInfoService docRefInfoService;
    private final Provider<StreamAttributeMapRetentionRuleDecorator> decoratorProvider;
    private final Optional<AttributeMapFactory> attributeMapFactory;
    private final Optional<MetaSecurityFilter> metaSecurityFilter;
    private final SecurityContext securityContext;

    @Inject
    MetaServiceImpl(final MetaDao metaDao,
                    final MetaFeedDao metaFeedDao,
                    final MetaTypeDao metaTypeDao,
                    final MetaValueDao metaValueDao,
                    final MetaRetentionTrackerDao metaRetentionTrackerDao,
                    final DocRefInfoService docRefInfoService,
                    final Provider<StreamAttributeMapRetentionRuleDecorator> decoratorProvider,
                    final Optional<AttributeMapFactory> attributeMapFactory,
                    final Optional<MetaSecurityFilter> metaSecurityFilter,
                    final SecurityContext securityContext) {
        this.metaDao = metaDao;
        this.metaFeedDao = metaFeedDao;
        this.metaTypeDao = metaTypeDao;
        this.metaValueDao = metaValueDao;
        this.metaRetentionTrackerDao = metaRetentionTrackerDao;
        this.docRefInfoService = docRefInfoService;
        this.decoratorProvider = decoratorProvider;
        this.attributeMapFactory = attributeMapFactory;
        this.metaSecurityFilter = metaSecurityFilter;
        this.securityContext = securityContext;
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
        final ExpressionOperator secureExpression = addPermissionConstraints(getIdExpression(id, anyStatus), DocumentPermissionNames.READ, FEED_FIELDS);
        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria(secureExpression);
        findMetaCriteria.setPageRequest(new PageRequest(0L, 1));
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
        final int result = updateStatus(meta.getId(), currentStatus, newStatus, now, DocumentPermissionNames.UPDATE);
        if (result > 0) {
            return new Meta.Builder(meta)
                    .status(newStatus)
                    .statusMs(now)
                    .build();
        } else {
            final Meta existingMeta = getMeta(meta.getId());
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

    private int updateStatus(final long id, final Status currentStatus, final Status newStatus, final long statusTime, final String permission) {
        final ExpressionOperator expression = getIdExpression(id, true);
        final ExpressionOperator secureExpression = addPermissionConstraints(expression, permission, FEED_FIELDS);
        final FindMetaCriteria criteria = new FindMetaCriteria(secureExpression);
        return metaDao.updateStatus(criteria, currentStatus, newStatus, statusTime);
    }

    @Override
    public int updateStatus(final FindMetaCriteria criteria, final Status currentStatus, final Status newStatus) {
        return securityContext.secureResult(() -> {
            // Decide which permission is needed for this update as logical deletes require delete permissions.
            String permission = DocumentPermissionNames.UPDATE;
            if (Status.DELETED.equals(newStatus)) {
                permission = DocumentPermissionNames.DELETE;
            }

            final ExpressionOperator expression = addPermissionConstraints(criteria.getExpression(), permission, FEED_FIELDS);
            criteria.setExpression(expression);

            return metaDao.updateStatus(criteria, currentStatus, newStatus, System.currentTimeMillis());
        });
    }

    @Override
    public void addAttributes(final Meta meta, final AttributeMap attributes) {
        metaValueDao.addAttributes(meta, attributes);
    }

    @Override
    public int delete(final long id) {
        return securityContext.secureResult(PermissionNames.DELETE_DATA_PERMISSION, () -> doLogicalDelete(id, true));
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
        return securityContext.secureResult(PermissionNames.DELETE_DATA_PERMISSION, () -> doLogicalDelete(id, lockCheck));
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
        return updateStatus(id, null, Status.DELETED, now, DocumentPermissionNames.DELETE);
    }

    @Override
    public DocRef getDocRef() {
        return META_STORE_PSEUDO_DOC_REF;
    }

    @Override
    public DataSource getDataSource() {
        return new DataSource(MetaFields.getAllFields());
    }

    @Override
    public void search(final ExpressionCriteria criteria, final AbstractField[] fields, final Consumer<Val[]> consumer) {
        final ExpressionOperator expression = addPermissionConstraints(criteria.getExpression(), DocumentPermissionNames.READ, FEED_FIELDS);
        criteria.setExpression(expression);
        metaDao.search(criteria, fields, consumer);
    }

    @Override
    public ResultPage<Meta> find(final FindMetaCriteria criteria) {
        final boolean fetchRelationships = criteria.isFetchRelationships();
        final PageRequest pageRequest = criteria.getPageRequest();
        if (fetchRelationships) {
            criteria.setPageRequest(null);
        }

//        final IdSet idSet = criteria.getSelectedIdSet();
//        // If for some reason we have been asked to match nothing then return nothing.
//        if (idSet != null && idSet.getMatchNull() != null && idSet.getMatchNull()) {
//            return ResultPage.createPageResultList(Collections.emptyList(), criteria.getPageRequest(), null);
//        }

        ResultPage<Meta> resultPage = secureFind(criteria);

//        final Condition condition = createCondition(criteria, DocumentPermissionNames.READ);
//
//        int offset = 0;
//        int numberOfRows = 1000000;
//
//        if (pageRequest != null) {
//            offset = pageRequest.getOffset().intValue();
//            numberOfRows = pageRequest.getLength();
//        }
//
//        List<Meta> results = find(condition, offset, numberOfRows);

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
                    results = results.subList(pageRequest.getOffset().intValue(), results.size());
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
    }

    private ResultPage<Meta> secureFind(final FindMetaCriteria criteria) {
        final ExpressionOperator expression = addPermissionConstraints(criteria.getExpression(), DocumentPermissionNames.READ, FEED_FIELDS);
        criteria.setExpression(expression);
        return metaDao.find(criteria);
    }

    private ResultPage<Meta> findChildren(final FindMetaCriteria parentCriteria, final List<Meta> streamList) {
        final Set<String> excludedFields = Set.of(MetaFields.ID.getName(), MetaFields.PARENT_ID.getName());
        final Builder builder = copyExpression(parentCriteria.getExpression(), excludedFields);

        final String parentIds = streamList.stream()
                .map(meta -> String.valueOf(meta.getId()))
                .collect(Collectors.joining(","));
        builder.addTerm(MetaFields.PARENT_ID.getName(), ExpressionTerm.Condition.IN, parentIds);

        return simpleFind(builder.build());
    }

    private Meta findParent(final Meta meta) {
        final ExpressionOperator expression = new ExpressionOperator.Builder()
                .addTerm(MetaFields.ID, ExpressionTerm.Condition.EQUALS, meta.getParentMetaId())
                .build();
        final ResultPage<Meta> parentList = simpleFind(expression);
        if (parentList != null && parentList.size() > 0) {
            return parentList.getFirst();
        }
        return new Meta.Builder()
                .id(meta.getParentMetaId())
                .build();
    }

    private ResultPage<Meta> simpleFind(final ExpressionOperator expression) {
        final FindMetaCriteria criteria = new FindMetaCriteria(expression);
        final ExpressionOperator secureExpression = addPermissionConstraints(expression, DocumentPermissionNames.READ, FEED_FIELDS);
        criteria.setExpression(secureExpression);
        return metaDao.find(criteria);
    }

    private Builder copyExpression(final ExpressionOperator expressionOperator, final Set<String> excludedFields) {
        final Builder builder = new Builder(expressionOperator.enabled(), expressionOperator.op());
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
    public Set<Meta> findEffectiveData(final EffectiveMetaDataCriteria criteria) {
        // See if we can find a data that exists before the earliest specified time.
        final Optional<Long> optionalId = getMaxEffectiveDataIdBeforePeriod(criteria);

        final Set<Meta> set = new HashSet<>();
        if (optionalId.isPresent()) {
            // Get the data that occurs just before or ast the start of the period.
            final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                    .addTerm(MetaFields.ID, ExpressionTerm.Condition.EQUALS, optionalId.get())
                    .build();
            // There is no need to apply security here are is has been applied when finding the data id above.
            final FindMetaCriteria findMetaCriteria = new FindMetaCriteria(expression);
            findMetaCriteria.setPageRequest(new PageRequest(0L, 1000));
            set.addAll(secureFind(findMetaCriteria).getValues());
        }

        // Now add all data that occurs within the requested period.
        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaFields.EFFECTIVE_TIME, ExpressionTerm.Condition.GREATER_THAN, DateUtil.createNormalDateTimeString(criteria.getEffectivePeriod().getFromMs()))
                .addTerm(MetaFields.EFFECTIVE_TIME, ExpressionTerm.Condition.LESS_THAN, DateUtil.createNormalDateTimeString(criteria.getEffectivePeriod().getToMs()))
                .addTerm(MetaFields.FEED_NAME, ExpressionTerm.Condition.EQUALS, criteria.getFeed())
                .addTerm(MetaFields.TYPE_NAME, ExpressionTerm.Condition.EQUALS, criteria.getType())
                .addTerm(MetaFields.STATUS, ExpressionTerm.Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();

        final ExpressionOperator secureExpression = addPermissionConstraints(expression, DocumentPermissionNames.READ, FEED_FIELDS);
        final FindMetaCriteria findMetaCriteria = new FindMetaCriteria(secureExpression);
        findMetaCriteria.setPageRequest(new PageRequest(0L, 1000));
        set.addAll(secureFind(findMetaCriteria).getValues());

        return set;
    }

    private Optional<Long> getMaxEffectiveDataIdBeforePeriod(final EffectiveMetaDataCriteria criteria) {
        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaFields.EFFECTIVE_TIME, ExpressionTerm.Condition.LESS_THAN_OR_EQUAL_TO, DateUtil.createNormalDateTimeString(criteria.getEffectivePeriod().getFromMs()))
                .addTerm(MetaFields.FEED_NAME, ExpressionTerm.Condition.EQUALS, criteria.getFeed())
                .addTerm(MetaFields.TYPE_NAME, ExpressionTerm.Condition.EQUALS, criteria.getType())
                .addTerm(MetaFields.STATUS, ExpressionTerm.Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();

        final ExpressionOperator secureExpression = addPermissionConstraints(expression, DocumentPermissionNames.READ, FEED_FIELDS);
        return metaDao.getMaxId(new FindMetaCriteria(secureExpression));
    }

//    @Override
//    public Long getMaxDataIdWithCreationBeforePeriod(final Long timestampMs) {
//        if (timestampMs == null)
//            return null;
//        final ExpressionOperator expression = new ExpressionOperator.Builder(Op.AND)
//                .addTerm(MetaFields.CREATE_TIME, ExpressionTerm.Condition.LESS_THAN_OR_EQUAL_TO, DateUtil.createNormalDateTimeString(timestampMs))
//                .addTerm(MetaFields.STATUS, ExpressionTerm.Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
//                .build();
//
//        final ExpressionOperator secureExpression = addPermissionConstraints(expression, DocumentPermissionNames.READ);
//        return metaDao.getMaxId(new FindMetaCriteria(secureExpression)).orElseThrow(() -> new NullPointerException("No current id exists"));
//    }

    @Override
    public List<String> getFeeds() {
        return metaFeedDao.list();
    }

    @Override
    public List<String> getTypes() {
        return metaTypeDao.list();
    }

    @Override
    public int getLockCount() {
        return metaDao.getLockCount();
    }

    @Override
    public ResultPage<MetaRow> findRows(final FindMetaCriteria criteria) {
        return securityContext.useAsReadResult(() -> {
            // Cache Call


//            final FindMetaCriteria findMetaCriteria = new FindMetaCriteria();
//            findMetaCriteria.copyFrom(criteria);
//            findMetaCriteria.setSort(MetaFields.CREATE_TIME.getName(), Direction.DESCENDING, false);

//            findDataCriteria.setFetchSet(new HashSet<>());

            // Share the page criteria
            final ResultPage<Meta> resultPage = find(criteria);
            final List<MetaRow> result = decorate(resultPage.getValues());
//            if (resultPage.size() > 0) {
//                // We need to decorate data with retention rules as a processing user.
//                final List<StreamDataRow> result = securityContext.asProcessingUserResult(() -> {
//                    // Create a data retention rule decorator for adding data retention information to returned data attribute maps.
//                    List<DataRetentionRule> rules = Collections.emptyList();
//
//                    final DataRetentionService dataRetentionService = dataRetentionServiceProvider.get();
//                    if (dataRetentionService != null) {
//                        final DataRetentionPolicy dataRetentionPolicy = dataRetentionService.load();
//                        if (dataRetentionPolicy != null && dataRetentionPolicy.getRules() != null) {
//                            rules = dataRetentionPolicy.getRules();
//                        }
//                        final AttributeMapRetentionRuleDecorator ruleDecorator = new AttributeMapRetentionRuleDecorator(dictionaryStore, rules);

            // Query the database for the attribute values
//                        if (criteria.isUseCache()) {


//                        } else {
//                            LOGGER.info("Loading attribute map from filesystem");
//                            loadAttributeMapFromFileSystem(criteria, result, result, ruleDecorator);
//                        }
//                    }
//                });


//            }

            return new ResultPage<>(result, ResultPage.createPageResponse(result, resultPage.getPageResponse()));
        });
    }

    @Override
    public ResultPage<MetaRow> findDecoratedRows(final FindMetaCriteria criteria) {
        try {
            final ResultPage<MetaRow> list = findRows(criteria);
            final StreamAttributeMapRetentionRuleDecorator decorator = decoratorProvider.get();
            list.getValues().forEach(metaRow ->
                    decorator.addMatchingRetentionRuleInfo(metaRow.getMeta(), metaRow.getAttributes()));

            return list;
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
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
            addChildren(row, anyStatus, result);
            addParents(row, anyStatus, result);
        }

        result.sort(Comparator.comparing(Meta::getId));
        return decorate(result);
    }

    @Override
    public ResultPage<Meta> findReprocess(final FindMetaCriteria criteria) {
        final ExpressionOperator expression = addPermissionConstraints(criteria.getExpression(), DocumentPermissionNames.READ, ALL_FEED_FIELDS);
        criteria.setExpression(expression);
        return metaDao.findReprocess(criteria);
    }

    private List<MetaRow> decorate(final List<Meta> metaList) {
        if (metaList == null || metaList.size() == 0) {
            return Collections.emptyList();
        }

        LOGGER.debug("Loading attribute map from DB");
        final Map<Long, Map<String, String>> attributeMap = metaValueDao.getAttributes(metaList);
        final List<MetaRow> metaRowList = new ArrayList<>(metaList.size());
        for (final Meta meta : metaList) {
            final Map<String, String> attributes = attributeMap.getOrDefault(meta.getId(), new HashMap<>());
            metaRowList.add(new MetaRow(meta, getPipelineName(meta), attributes));
        }
        return metaRowList;
    }

    @Override
    public SelectionSummary getSelectionSummary(final FindMetaCriteria criteria) {
        final ExpressionOperator expression = addPermissionConstraints(criteria.getExpression(), DocumentPermissionNames.READ, FEED_FIELDS);
        criteria.setExpression(expression);
        return metaDao.getSelectionSummary(criteria);
    }

    @Override
    public SelectionSummary getReprocessSelectionSummary(final FindMetaCriteria criteria) {
        final ExpressionOperator expression = addPermissionConstraints(criteria.getExpression(), DocumentPermissionNames.READ, ALL_FEED_FIELDS);
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
        final List<Meta> children = find(new FindMetaCriteria(getParentIdExpression(parent.getId(), anyStatus))).getValues();
        children.forEach(child -> {
            result.add(child);
            addChildren(child, anyStatus, result);
        });
    }

    private void addParents(final Meta child, final boolean anyStatus, final List<Meta> result) {
        if (child.getParentMetaId() != null) {
            final List<Meta> parents = find(new FindMetaCriteria(getIdExpression(child.getParentMetaId(), anyStatus))).getValues();
            if (parents != null && parents.size() > 0) {
                parents.forEach(parent -> {
                    result.add(parent);
                    addParents(parent, anyStatus, result);
                });
            } else {
                // Add a dummy parent data as we don't seem to be able to get the real parent.
                // This might be because it is deleted or the user does not have access permissions.
                final Meta meta = new Meta.Builder()
                        .id(child.getParentMetaId())
                        .build();
                result.add(meta);
            }
        }
    }

    private ExpressionOperator getIdExpression(final long id, final boolean anyStatus) {
        if (anyStatus) {
            return new ExpressionOperator.Builder(Op.AND)
                    .addTerm(MetaFields.ID, ExpressionTerm.Condition.EQUALS, id)
                    .build();
        }

        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaFields.ID, ExpressionTerm.Condition.EQUALS, id)
                .addTerm(MetaFields.STATUS, ExpressionTerm.Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .build();
    }

    private ExpressionOperator getParentIdExpression(final long id, final boolean anyStatus) {
        if (anyStatus) {
            return new ExpressionOperator.Builder(Op.AND)
                    .addTerm(MetaFields.PARENT_ID, ExpressionTerm.Condition.EQUALS, id)
                    .build();
        }

        return new ExpressionOperator.Builder(Op.AND)
                .addTerm(MetaFields.PARENT_ID, ExpressionTerm.Condition.EQUALS, id)
                .addTerm(MetaFields.STATUS, ExpressionTerm.Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
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
                final ExpressionOperator.Builder builder = new ExpressionOperator.Builder(Op.AND);
                builder.addOperator(expression);
                builder.addOperator(filter);
                return builder.build();
            }

            return expression;
        }).orElse(expression);
    }

    @Override
    public List<MetaInfoSection> fetchFullMetaInfo(final long id) {
        final Meta meta = getMeta(id, true);
        final List<MetaInfoSection> sections = new ArrayList<>();

        final Map<String, String> attributeMap = attributeMapFactory.map(amf -> amf.getAttributes(meta)).orElse(null);
        if (attributeMap == null) {
            final List<MetaInfoSection.Entry> entries = new ArrayList<>(1);
            entries.add(new MetaInfoSection.Entry("Deleted Stream Id", String.valueOf(meta.getId())));
            sections.add(new MetaInfoSection("Stream", entries));

        } else {
            sections.add(new MetaInfoSection("Stream", getStreamEntries(meta)));

            final List<MetaInfoSection.Entry> entries = new ArrayList<>();
            final List<String> sortedKeys = attributeMap.keySet().stream().sorted(Comparator.naturalOrder()).collect(Collectors.toList());
            sortedKeys.forEach(key -> entries.add(new MetaInfoSection.Entry(key, attributeMap.get(key))));
            sections.add(new MetaInfoSection("Attributes", entries));

            // Add additional data retention information.
            sections.add(new MetaInfoSection("Retention", getDataRententionEntries(meta, attributeMap)));
        }

        return sections;
    }

    @Override
    public Optional<DataRetentionTracker> getRetentionTracker() {
        return metaRetentionTrackerDao.getTracker();
    }

    @Override
    public void setTracker(final DataRetentionTracker dataRetentionTracker) {
        metaRetentionTrackerDao.createOrUpdate(dataRetentionTracker);
    }

    @Override
    public List<DataRetentionDeleteSummary> getRetentionDeleteSummary(final DataRetentionRules rules,
                                                                      final FindDataRetentionImpactCriteria criteria) {
        // Here for dev testing to add a delay
//        try {
//            Thread.sleep(1_300);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        return securityContext.secureResult(PermissionNames.MANAGE_POLICIES_PERMISSION, () ->
                metaDao.getRetentionDeletionSummary(rules, criteria));
    }

    private List<MetaInfoSection.Entry> getStreamEntries(final Meta meta) {
        final List<MetaInfoSection.Entry> entries = new ArrayList<>();

        entries.add(new MetaInfoSection.Entry("Stream Id", String.valueOf(meta.getId())));
        entries.add(new MetaInfoSection.Entry("Status", meta.getStatus().getDisplayValue()));
        entries.add(new MetaInfoSection.Entry("Status Ms", getDateTimeString(meta.getStatusMs())));
        entries.add(new MetaInfoSection.Entry("Parent Data Id", String.valueOf(meta.getParentMetaId())));
        entries.add(new MetaInfoSection.Entry("Created", getDateTimeString(meta.getCreateMs())));
        entries.add(new MetaInfoSection.Entry("Effective", getDateTimeString(meta.getEffectiveMs())));
        entries.add(new MetaInfoSection.Entry("Stream Type", meta.getTypeName()));
        entries.add(new MetaInfoSection.Entry("Feed", meta.getFeedName()));

        if (meta.getProcessorUuid() != null) {
            entries.add(new MetaInfoSection.Entry("Processor", meta.getProcessorUuid()));
        }
        if (meta.getPipelineUuid() != null) {
            final String pipelineName = getPipelineName(meta);
            final String pipeline = DocRefUtil.createSimpleDocRefString(new DocRef(PipelineDoc.DOCUMENT_TYPE, meta.getPipelineUuid(), pipelineName));
            entries.add(new MetaInfoSection.Entry("Processor Pipeline", pipeline));
        }
        return entries;
    }

    private String getDateTimeString(final long ms) {
        return DateUtil.createNormalDateTimeString(ms) + " (" + ms + ")";
    }

    private List<MetaInfoSection.Entry> getDataRententionEntries(final Meta meta, final Map<String, String> attributeMap) {
        final List<MetaInfoSection.Entry> entries = new ArrayList<>();

        // Add additional data retention information.
        final StreamAttributeMapRetentionRuleDecorator decorator = decoratorProvider.get();
        decorator.addMatchingRetentionRuleInfo(meta, attributeMap);

        entries.add(new MetaInfoSection.Entry(DataRetentionFields.RETENTION_AGE, attributeMap.get(DataRetentionFields.RETENTION_AGE)));
        entries.add(new MetaInfoSection.Entry(DataRetentionFields.RETENTION_UNTIL, attributeMap.get(DataRetentionFields.RETENTION_UNTIL)));
        entries.add(new MetaInfoSection.Entry(DataRetentionFields.RETENTION_RULE, attributeMap.get(DataRetentionFields.RETENTION_RULE)));

        return entries;
    }

    @Override
    public List<String> getProcessorUuidList(final FindMetaCriteria criteria) {
        return metaDao.getProcessorUuidList(criteria);
    }
}
