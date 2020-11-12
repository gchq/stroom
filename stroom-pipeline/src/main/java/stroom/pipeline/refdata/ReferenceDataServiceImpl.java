package stroom.pipeline.refdata;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValInteger;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ValString;
import stroom.data.shared.StreamTypeNames;
import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.DocRefField;
import stroom.datasource.api.v2.FieldTypes;
import stroom.datasource.api.v2.IdField;
import stroom.datasource.api.v2.IntegerField;
import stroom.datasource.api.v2.LongField;
import stroom.datasource.api.v2.TextField;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.feed.api.FeedStore;
import stroom.pipeline.refdata.RefDataLookupRequest.ReferenceLoader;
import stroom.pipeline.refdata.store.RefDataStore;
import stroom.pipeline.refdata.store.RefDataStoreFactory;
import stroom.pipeline.refdata.store.RefDataValueConverter;
import stroom.pipeline.refdata.store.RefDataValueProxyConsumerFactory;
import stroom.pipeline.refdata.store.RefDataValueProxyConsumerFactory.Factory;
import stroom.pipeline.refdata.store.RefStoreEntry;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.common.v2.DateExpressionParser;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.pipeline.scope.PipelineScopeRunnable;
import stroom.util.shared.PermissionException;
import stroom.util.time.StroomDuration;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotFoundException;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ReferenceDataServiceImpl implements ReferenceDataService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReferenceDataServiceImpl.class);

    private static final DocRef REF_STORE_PSEUDO_DOC_REF = new DocRef(
            "Searchable",
            "Reference Data Store",
            "Reference Data Store (This Node Only)");

    private static final List<Condition> SUPPORTED_STRING_CONDITIONS = List.of(Condition.EQUALS);
    private static final List<Condition> SUPPORTED_DOC_REF_CONDITIONS = List.of(Condition.IS_DOC_REF, Condition.EQUALS);

    private static final AbstractField KEY_FIELD = new TextField(
            "Key", true, SUPPORTED_STRING_CONDITIONS);
    private static final AbstractField VALUE_FIELD = new TextField(
            "Value", true, SUPPORTED_STRING_CONDITIONS);
    private static final AbstractField VALUE_REF_COUNT_FIELD = new IntegerField(
            "Value Reference Count", false);
    private static final AbstractField MAP_NAME_FIELD = new TextField(
            "Map Name", true, SUPPORTED_STRING_CONDITIONS);
    private static final AbstractField CREATE_TIME_FIELD = new DateField(
            "Create Time", true);
    private static final AbstractField EFFECTIVE_TIME_FIELD = new DateField(
            "Effective Time", true);
    private static final AbstractField LAST_ACCESSED_TIME_FIELD = new DateField(
            "Last Accessed Time", true);
    private static final AbstractField PIPELINE_FIELD = new DocRefField(PipelineDoc.DOCUMENT_TYPE,
            "Reference Loader Pipeline", true, SUPPORTED_DOC_REF_CONDITIONS);
    private static final AbstractField PROCESSING_STATE_FIELD = new TextField(
            "Processing State", false);
    private static final AbstractField STREAM_ID_FIELD = new IdField(
            "Stream ID", false);
    private static final AbstractField STREAM_NO_FIELD = new LongField(
            "Sub-Stream Number", false);
    private static final AbstractField PIPELINE_VERSION_FIELD = new TextField(
            "Pipeline Version", false);

    private static final List<AbstractField> FIELDS = List.of(
            KEY_FIELD,
            VALUE_FIELD,
            VALUE_REF_COUNT_FIELD,
            MAP_NAME_FIELD,
            CREATE_TIME_FIELD,
            EFFECTIVE_TIME_FIELD,
            LAST_ACCESSED_TIME_FIELD,
            PIPELINE_FIELD,
            PROCESSING_STATE_FIELD,
            STREAM_ID_FIELD,
            STREAM_NO_FIELD,
            PIPELINE_VERSION_FIELD);

    private static final DataSource DATA_SOURCE = new DataSource(FIELDS);

    private static final Map<String, AbstractField> FIELD_NAME_TO_FIELD_MAP = FIELDS.stream()
            .collect(Collectors.toMap(AbstractField::getName, Function.identity()));

//    private static final Map<String, Function<RefStoreEntry, Val>> FIELD_TO_VAL_EXTRACTOR_MAP = Map.ofEntries(
//            Map.entry(KEY_FIELD.getName(), refStoreEntry ->
//                    ValString.create(refStoreEntry.getKey())),
//            Map.entry(VALUE_FIELD.getName(), refStoreEntry ->
//                    ValString.create(refStoreEntry.getValue())),
//            Map.entry(MAP_NAME_FIELD.getName(), refStoreEntry ->
//                    ValString.create(refStoreEntry.getMapDefinition().getMapName())),
//            Map.entry(CREATE_TIME_FIELD.getName(), refStoreEntry ->
//                    ValLong.create(refStoreEntry.getRefDataProcessingInfo().getCreateTimeEpochMs())),
//            Map.entry(EFFECTIVE_TIME_FIELD.getName(), refStoreEntry ->
//                    ValLong.create(refStoreEntry.getRefDataProcessingInfo().getEffectiveTimeEpochMs())),
//            Map.entry(LAST_ACCESSED_TIME_FIELD.getName(), refStoreEntry ->
//                    ValLong.create(refStoreEntry.getRefDataProcessingInfo().getLastAccessedTimeEpochMs())),
//            Map.entry(PIPELINE_FIELD.getName(), refStoreEntry ->
//                    ValString.create(refStoreEntry.getMapDefinition().getRefStreamDefinition().getPipelineDocRef().toInfoString())),
//            Map.entry(PROCESSING_STATE_FIELD.getName(), refStoreEntry ->
//                    ValString.create(refStoreEntry.getRefDataProcessingInfo().getProcessingState().getDisplayName())),
//            Map.entry(STREAM_ID_FIELD.getName(), refStoreEntry ->
//                    ValLong.create(refStoreEntry.getMapDefinition().getRefStreamDefinition().getStreamId())),
//            Map.entry(STREAM_NO_FIELD.getName(), refStoreEntry ->
//                    ValLong.create(refStoreEntry.getMapDefinition().getRefStreamDefinition().getStreamNo())),
//            Map.entry(PIPELINE_VERSION_FIELD.getName(), refStoreEntry ->
//                    ValString.create(refStoreEntry.getMapDefinition().getRefStreamDefinition().getPipelineVersion())));

    private static final Map<String, Function<RefStoreEntry, Object>> FIELD_TO_EXTRACTOR_MAP = Map.ofEntries(
            Map.entry(KEY_FIELD.getName(),
                    RefStoreEntry::getKey),
            Map.entry(VALUE_FIELD.getName(),
                    RefStoreEntry::getValue),
            Map.entry(VALUE_REF_COUNT_FIELD.getName(),
                    RefStoreEntry::getValueReferenceCount),
            Map.entry(MAP_NAME_FIELD.getName(), refStoreEntry ->
                    refStoreEntry.getMapDefinition().getMapName()),
            Map.entry(CREATE_TIME_FIELD.getName(), refStoreEntry ->
                    refStoreEntry.getRefDataProcessingInfo().getCreateTimeEpochMs()),
            Map.entry(EFFECTIVE_TIME_FIELD.getName(), refStoreEntry ->
                    refStoreEntry.getRefDataProcessingInfo().getEffectiveTimeEpochMs()),
            Map.entry(LAST_ACCESSED_TIME_FIELD.getName(), refStoreEntry ->
                    refStoreEntry.getRefDataProcessingInfo().getLastAccessedTimeEpochMs()),
            Map.entry(PIPELINE_FIELD.getName(), refStoreEntry ->
                    refStoreEntry.getMapDefinition().getRefStreamDefinition().getPipelineDocRef()),
            Map.entry(PROCESSING_STATE_FIELD.getName(), refStoreEntry ->
                    refStoreEntry.getRefDataProcessingInfo().getProcessingState().getDisplayName()),
            Map.entry(STREAM_ID_FIELD.getName(), refStoreEntry ->
                    refStoreEntry.getMapDefinition().getRefStreamDefinition().getStreamId()),
            Map.entry(STREAM_NO_FIELD.getName(), refStoreEntry ->
                    refStoreEntry.getMapDefinition().getRefStreamDefinition().getStreamNo()),
            Map.entry(PIPELINE_VERSION_FIELD.getName(), refStoreEntry ->
                    refStoreEntry.getMapDefinition().getRefStreamDefinition().getPipelineVersion()));

    private final RefDataStore refDataStore;
    private final SecurityContext securityContext;
    private final FeedStore feedStore;
    private final Provider<ReferenceData> referenceDataProvider;
    private final RefDataValueConverter refDataValueConverter;
    private final PipelineScopeRunnable pipelineScopeRunnable;
    private final TaskContextFactory taskContextFactory;
    private final RefDataValueProxyConsumerFactory.Factory refDataValueProxyConsumerFactoryFactory;

    @Inject
    public ReferenceDataServiceImpl(final RefDataStoreFactory refDataStoreFactory,
                                    final SecurityContext securityContext,
                                    final FeedStore feedStore,
                                    final Provider<ReferenceData> referenceDataProvider,
                                    final RefDataValueConverter refDataValueConverter,
                                    final PipelineScopeRunnable pipelineScopeRunnable,
                                    final TaskContextFactory taskContextFactory,
                                    final Factory refDataValueProxyConsumerFactoryFactory) {
        this.refDataStore = refDataStoreFactory.getOffHeapStore();
        this.securityContext = securityContext;
        this.feedStore = feedStore;
        this.referenceDataProvider = referenceDataProvider;
        this.refDataValueConverter = refDataValueConverter;
        this.pipelineScopeRunnable = pipelineScopeRunnable;
        this.taskContextFactory = taskContextFactory;
        this.refDataValueProxyConsumerFactoryFactory = refDataValueProxyConsumerFactoryFactory;
    }

    @Override
    public List<RefStoreEntry> entries(final int limit) {
        return withPermissionCheck(() -> {
            final List<RefStoreEntry> entries;
            try {
                entries = refDataStore.list(limit);
            } catch (Exception e) {
                LOGGER.error("Error listing reference data",e);
                throw e;
            }
            return entries;
        });
    }

    @Override
    public String lookup(final RefDataLookupRequest refDataLookupRequest) {
        if (refDataLookupRequest == null) {
            throw new BadRequestException("Missing request object");
        }

        // TODO @AT ensure user has rights to
        //    use/read each feed
        //    use each pipe

        // TODO @AT This is a lot of cross over between ReferenceData and ReferenceDataServiceImpl

        return securityContext.secureResult(PermissionNames.VIEW_DATA_PERMISSION, () ->
                taskContextFactory.contextResult("Reference Data Lookup (API)",
                        taskContext ->
                                LOGGER.logDurationIfDebugEnabled(
                                        () ->
                                                performLookup(refDataLookupRequest),
                                        LogUtil.message("Performing lookup for {}", refDataLookupRequest))))
                .get();
    }

    @Override
    public void purge(final StroomDuration purgeAge) {
        securityContext.secure(PermissionNames.MANAGE_CACHE_PERMISSION, () ->
                taskContextFactory.context("Reference Data Purge",
                        taskContext ->
                                LOGGER.logDurationIfDebugEnabled(
                                        () ->
                                                performPurge(purgeAge),
                                        LogUtil.message("Performing Purge for entries older than {}", purgeAge)))
                        .run());

    }

    private void performPurge(final StroomDuration purgeAge) {
        refDataStore.purgeOldData(purgeAge);
    }

    private String performLookup(final RefDataLookupRequest refDataLookupRequest) {
        try {
            final LookupIdentifier lookupIdentifier = LookupIdentifier.of(
                    refDataLookupRequest.getMapName(),
                    refDataLookupRequest.getKey(),
                    refDataLookupRequest.getEffectiveTimeEpochMs());

            final List<PipelineReference> pipelineReferences = convertReferenceLoaders(
                    refDataLookupRequest.getReferenceLoaders());

            final ReferenceDataResult referenceDataResult = new ReferenceDataResult();

            LOGGER.logDurationIfDebugEnabled(() ->
                            pipelineScopeRunnable.scopeRunnable(() ->
                                    referenceDataProvider.get().ensureReferenceDataAvailability(
                                            pipelineReferences,
                                            lookupIdentifier,
                                            referenceDataResult)),
                    "Ensuring data availability");

            final Configuration configuration = Configuration.newConfiguration();
            final PipelineConfiguration pipelineConfiguration = configuration.makePipelineConfiguration();
            final StringWriter stringWriter = new StringWriter();
            final Receiver stringReceiver = refDataValueConverter.buildStringReceiver(
                    stringWriter, pipelineConfiguration);

            final RefDataValueProxyConsumerFactory refDataValueProxyConsumerFactory =
                    refDataValueProxyConsumerFactoryFactory.create(stringReceiver, pipelineConfiguration);

            // Do the lookup and consume the value into the StringWriter
            referenceDataResult.getRefDataValueProxy()
                    .ifPresent(refDataValueProxy ->
                            refDataValueProxy.consumeValue(refDataValueProxyConsumerFactory));

            if (stringWriter.getBuffer().length() == 0) {
                throw new NotFoundException(LogUtil.message("No value for map: {}, key: {}, time {}",
                        refDataLookupRequest.getMapName(),
                        refDataLookupRequest.getKey(),
                        Instant.ofEpochMilli(refDataLookupRequest.getEffectiveTimeEpochMs()).toString()));
            }

            return stringWriter.toString();
        } catch (Exception e) {
            LOGGER.error("Error looking up {}", refDataLookupRequest, e);
            throw e;
        }
    }

    private List<PipelineReference> convertReferenceLoaders(final List<ReferenceLoader> referenceLoaders) {
        if (referenceLoaders == null) {
            return Collections.emptyList();
        } else {
            return referenceLoaders.stream()
                    .map(referenceLoader -> {
                        final DocRef feedDocRef = getFeedDocRef(referenceLoader);

                        return new PipelineReference(
                                referenceLoader.getLoaderPipeline(),
                                feedDocRef,
                                StreamTypeNames.REFERENCE);
                    })
                    .collect(Collectors.toList());
        }
    }

    private DocRef getFeedDocRef(final ReferenceLoader referenceLoader) {
        if (referenceLoader == null) {
            throw new BadRequestException("Null referenceLoader");
        } else {
            if (referenceLoader.getReferenceFeed().getUuid() != null
                    && referenceLoader.getReferenceFeed().getName() != null) {

                return referenceLoader.getReferenceFeed();
            } else if (referenceLoader.getReferenceFeed().getName() != null) {
                // Feed names are unique
                return feedStore.findByName(referenceLoader.getReferenceFeed().getName())
                        .stream()
                        .findFirst()
                        .orElseThrow(() ->
                                new BadRequestException("Unknown feed " + referenceLoader.getReferenceFeed()));
            } else {
                throw new BadRequestException("Need to provide a name or a UUID and name for each referenceLoader referenceFeed");
            }
        }
    }


    private <T> T withPermissionCheck(final Supplier<T> supplier) {
        // TODO @AT Need some kind of fine grained doc permission check on the pipe associated with each entry
        //   but this will do for a first stab
        if (securityContext.isAdmin()) {
            return supplier.get();
        } else {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to view reference data");
        }
    }

    private void withPermissionCheck(final Runnable runnable) {
        // TODO @AT Need some kind of fine grained doc permission check on the pipe associated with each entry
        //   but this will do for a first stab
        if (securityContext.isAdmin()) {
            runnable.run();
        } else {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to view reference data");
        }
    }

    @Override
    public DocRef getDocRef() {
        return REF_STORE_PSEUDO_DOC_REF;
    }

    @Override
    public DataSource getDataSource() {
        return DATA_SOURCE;
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final AbstractField[] fields,
                       final Consumer<Val[]> consumer) {

        withPermissionCheck(() -> {
            LOGGER.logDurationIfInfoEnabled(
                    () -> doSearch(criteria, fields, consumer),
                    "Querying ref store");
        });
    }

    private void doSearch(final ExpressionCriteria criteria,
                          final AbstractField[] fields,
                          final Consumer<Val[]> consumer) {
        // TODO @AT This is a temporary very crude impl to see if it works.
        //  The search code ought to be pushed down to the offHeapStore so it can query the many DBs
        //  selectively and in a MUCH more efficient way, e.g. using start/stop keys on the kv store scan.

        // TODO @AT Do we need to do something with param replacement? e.g.:
        //     final Map<String, String> paramMap = ExpressionParamUtil.createParamMap(query.getParams());
        //     expression = ExpressionUtil.replaceExpressionParameters(expression, paramMap);
        //   But we don't have a paramMap.

        // TODO @AT Need to check if we have any common code for this sort of ExpOp, ExpTerm, Val munging

        // TODO @AT Need to run the query as a task so it can be monitored from the UI.

        final List<RefStoreEntry> entries = entries(10_000);

        final Predicate<RefStoreEntry> predicate = buildEntryPredicate(criteria);

        try {
            entries.stream()
                    .filter(refStoreEntry -> {
                                final boolean result = predicate.test(refStoreEntry);
                                return result;
                            })
                    .forEach(refStoreEntry -> {
                        final Val[] valArr = new Val[fields.length];

                        for (int i = 0; i < fields.length; i++) {
                            final Object value = FIELD_TO_EXTRACTOR_MAP.get(fields[i].getName())
                                    .apply(refStoreEntry);
                            valArr[i] = convertToVal(value, fields[i]);;
                        }
                        consumer.accept(valArr);
                    });
        } catch (Exception e) {
            LOGGER.error("Error querying entry list", e);
            throw e;
        }
    }


    private Predicate<RefStoreEntry> buildEntryPredicate(final ExpressionCriteria expressionCriteria) {
        try {
            if (expressionCriteria != null) {
                final Predicate<RefStoreEntry> predicate = convertExpressionItem(expressionCriteria.getExpression());
                if (predicate == null) {
                    if (expressionCriteria.getExpression() != null
                            && Op.NOT.equals(expressionCriteria.getExpression().op())) {
                        return refStoreEntry -> false;
                    } else {
                        return refStoreEntry -> true;
                    }
                } else {
                    return predicate;
                }
            } else {
                return refStoreEntry -> true;
            }
        } catch (Exception e) {
            LOGGER.error("Error building predicate for {}", expressionCriteria, e);
            throw e;
        }
    }

    private Predicate<RefStoreEntry> convertExpressionItem(final ExpressionItem expressionItem) {
        if (expressionItem != null && expressionItem.enabled()) {
            if (expressionItem instanceof ExpressionOperator) {
                return convertExpressionOperator((ExpressionOperator) expressionItem);
            } else if (expressionItem instanceof ExpressionTerm) {
                return convertExpressionTerm((ExpressionTerm) expressionItem);
            } else {
                throw new RuntimeException("Unknown class " + expressionItem.getClass().getName());
            }
        } else {
            return null;
        }
    }

    private Predicate<RefStoreEntry> convertExpressionOperator(final ExpressionOperator expressionOperator) {
        // Stroom allows NOT {} expressions to have more than one child and so
        // NOT { x=1, y=1 }
        // is treated like an implicit AND, i.e.
        // AND { NOT {x=1}, NOT {y=1}

        if (expressionOperator.getChildren() != null) {
            final List<Predicate<RefStoreEntry>> childPredicates = expressionOperator.getChildren()
                    .stream()
                    .map(this::convertExpressionItem)
                    .collect(Collectors.toList());
            return buildOperatorPredicate(childPredicates, expressionOperator);
        } else {
            return null;
        }
    }

    private <T> Predicate<T> buildOperatorPredicate(
            final List<Predicate<T>> childPredicates,
            final ExpressionOperator expressionOperator) {

        final List<Predicate<T>> effectivePredicates = childPredicates.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (expressionOperator.op().equals(Op.AND)) {
            return buildAndPredicate(effectivePredicates);
        } else if (expressionOperator.op().equals(Op.OR)) {
            return buildOrPredicate(effectivePredicates);
        } else if (expressionOperator.op().equals(Op.NOT)) {
            return buildNotPredicate(effectivePredicates);
        } else {
            throw new RuntimeException("Unexpected op " + expressionOperator.op());
        }
    }

    private <T> Predicate<T> buildAndPredicate(final List<Predicate<T>> childPredicates) {

        if (childPredicates != null && !childPredicates.isEmpty()) {
            return val -> {
                boolean compoundResult = true;

                // expecting all list items to be non null
                for (final Predicate<T> childPredicate : childPredicates) {
                    boolean testResult = childPredicate.test(val);

                    compoundResult = compoundResult && testResult;

                    // Found one FALSE so drop out early
                    if (!compoundResult) {
                        break;
                    }
                }
                return compoundResult;
            };
        } else {
            // empty AND() so no effectively no predicate
            return null;
        }
    }

    private <T> Predicate<T> buildOrPredicate(final List<Predicate<T>> childPredicates) {

        if (childPredicates != null && !childPredicates.isEmpty()) {
            return val -> {
                boolean compoundResult = false;

                // expecting all list items to be non null
                for (final Predicate<T> childPredicate : childPredicates) {
                    boolean testResult = childPredicate.test(val);

                    compoundResult = compoundResult || testResult;

                    // Found one TRUE so drop out early
                    if (compoundResult) {
                        break;
                    }
                }
                return compoundResult;
            };
        } else {
            // empty AND() so no effectively no predicate
            return null;
        }
    }

    private <T> Predicate<T> buildNotPredicate(final List<Predicate<T>> childPredicates) {

        if (childPredicates != null && !childPredicates.isEmpty()) {
            return val -> {
                Boolean compoundResult = null;

                // expecting all list items to be non null
                for (final Predicate<T> childPredicate : childPredicates) {
                    // treat NOT(x, y) as AND(NOT(x), NOT(y))
                    boolean testResult = !childPredicate.test(val);

                    if (compoundResult == null) {
                        compoundResult = testResult;
                    } else {
                        compoundResult = compoundResult && testResult;
                    }

                    // Found one FALSE so drop out early
                    if (!compoundResult) {
                        break;
                    }
                }
                if (compoundResult != null) {
                    return compoundResult;
                } else {
                    // No children i.e. empty NOT()
                    return false;
                }
            };
        } else {
            // empty AND() so no effectively no predicate
            return null;
        }
    }

    private Predicate<RefStoreEntry> convertExpressionTerm(final ExpressionTerm expressionTerm) {

        // name => field
        // field => fieldType
        AbstractField abstractField = FIELD_NAME_TO_FIELD_MAP.get(expressionTerm.getField());

        if (abstractField.getType().equals(FieldTypes.TEXT)) {
            return buildTextFieldPredicate(
                    expressionTerm,
                    refStoreEntry -> (String) FIELD_TO_EXTRACTOR_MAP.get(expressionTerm.getField()).apply(refStoreEntry));
        } else if (abstractField.getType().equals(FieldTypes.LONG)) {
            return buildLongFieldPredicate(
                    expressionTerm,
                    refStoreEntry -> (Long) FIELD_TO_EXTRACTOR_MAP.get(expressionTerm.getField()).apply(refStoreEntry));
        } else if (abstractField.getType().equals(FieldTypes.DATE)) {
            return buildDateFieldPredicate(
                    expressionTerm,
                    refStoreEntry -> (Long) FIELD_TO_EXTRACTOR_MAP.get(expressionTerm.getField()).apply(refStoreEntry));
        } else if (abstractField.getType().equals(FieldTypes.DOC_REF)) {
            return buildDocRefFieldPredicate(
                    expressionTerm,
                    refStoreEntry -> (DocRef) FIELD_TO_EXTRACTOR_MAP.get(expressionTerm.getField()).apply(refStoreEntry));
        } else {
            throw new RuntimeException("Unsupported term " + expressionTerm);
        }

//        if (expressionTerm.getField().equals(KEY_FIELD.getName())) {
//            // TODO @AT Implement
//            return buildTextFieldPredicate(expressionTerm, )
//        } else {
//            // TODO @AT Implement
//            return refStoreEntry -> true;
//        }
    }

    private Predicate<RefStoreEntry> buildTextFieldPredicate(final ExpressionTerm expressionTerm,
                                                             final Function<RefStoreEntry, String> valueExtractor) {
        // TODO @AT Handle wildcarding in term
        if (expressionTerm.getCondition().equals(Condition.EQUALS)) {
            return rec -> {
                final String termValue = expressionTerm.getValue();
                final String entryValue = valueExtractor.apply(rec);
                return Objects.equals(termValue, entryValue);
            };
        } else if (expressionTerm.getCondition().equals(Condition.CONTAINS)) {
            return rec -> {
                final String termValue = expressionTerm.getValue();
                final String entryValue = valueExtractor.apply(rec);
                return entryValue.contains(termValue);
            };
        } else {
            throw new RuntimeException("Unexpected condition " + expressionTerm.getCondition());
        }
    }

    private Predicate<RefStoreEntry> buildLongFieldPredicate(final ExpressionTerm expressionTerm,
                                                             final Function<RefStoreEntry, Long> valueExtractor) {
        final Long termValue = Long.valueOf(expressionTerm.getValue());
        if (expressionTerm.getCondition().equals(Condition.EQUALS)) {
            return rec -> Objects.equals(valueExtractor.apply(rec), termValue);
        } else if (expressionTerm.getCondition().equals(Condition.GREATER_THAN)) {
            return rec -> valueExtractor.apply(rec) > termValue;
        } else if (expressionTerm.getCondition().equals(Condition.GREATER_THAN_OR_EQUAL_TO)) {
            return rec -> valueExtractor.apply(rec) >= termValue;
        } else if (expressionTerm.getCondition().equals(Condition.LESS_THAN)) {
            return rec -> valueExtractor.apply(rec) < termValue;
        } else if (expressionTerm.getCondition().equals(Condition.LESS_THAN_OR_EQUAL_TO)) {
            return rec -> valueExtractor.apply(rec) <= termValue;
        } else {
            throw new RuntimeException("Unexpected condition " + expressionTerm.getCondition());
        }
    }

    private Predicate<RefStoreEntry> buildDateFieldPredicate(final ExpressionTerm expressionTerm,
                                                             final Function<RefStoreEntry, Long> valueExtractor) {
        // TODO @AT Handle stuff like 'today() -1d'
        // TODO @AT Need to get now() once for the query
        final Long termValue = getDate(expressionTerm.getField(), expressionTerm.getValue(), Instant.now().toEpochMilli());
        if (expressionTerm.getCondition().equals(Condition.EQUALS)) {
            return rec -> Objects.equals(valueExtractor.apply(rec), termValue);
        } else if (expressionTerm.getCondition().equals(Condition.GREATER_THAN)) {
            return rec -> valueExtractor.apply(rec) > termValue;
        } else if (expressionTerm.getCondition().equals(Condition.GREATER_THAN_OR_EQUAL_TO)) {
            return rec -> valueExtractor.apply(rec) >= termValue;
        } else if (expressionTerm.getCondition().equals(Condition.LESS_THAN)) {
            return rec -> valueExtractor.apply(rec) < termValue;
        } else if (expressionTerm.getCondition().equals(Condition.LESS_THAN_OR_EQUAL_TO)) {
            return rec -> valueExtractor.apply(rec) <= termValue;
        } else {
            throw new RuntimeException("Unexpected condition " + expressionTerm.getCondition());
        }
    }

    private Predicate<RefStoreEntry> buildDocRefFieldPredicate(final ExpressionTerm expressionTerm,
                                                               final Function<RefStoreEntry, DocRef> valueExtractor) {
        final DocRef termValue = new DocRef.Builder()
                .uuid(expressionTerm.getValue())
                .build();
        if (expressionTerm.getCondition().equals(Condition.IS_DOC_REF)) {
            return rec -> docRefsEqualOnUuid(valueExtractor.apply(rec), termValue);
        } else if (expressionTerm.getCondition().equals(Condition.EQUALS)) {
            return rec -> docRefsEqualOnName(valueExtractor.apply(rec), termValue);
        } else {
            throw new RuntimeException("Unexpected condition " + expressionTerm.getCondition());
        }
    }

    private boolean docRefsEqualOnUuid(final DocRef docRef1, final DocRef docRef2) {
        if (docRef1 == null && docRef2 == null) {
            return false;
        } if (docRef1 == null) {
            return false;
        } if (docRef2 == null) {
            return false;
        } else {
            return Objects.equals(docRef1.getUuid(), docRef2.getUuid());
        }
    }

    private boolean docRefsEqualOnName(final DocRef docRef1, final DocRef docRef2) {
        if (docRef1 == null && docRef2 == null) {
            return false;
        } if (docRef1 == null) {
            return false;
        } if (docRef2 == null) {
            return false;
        } else {
            return Objects.equals(docRef1.getName(), docRef2.getName());
        }
    }

    private Val convertToVal(final Object object, final AbstractField field) {
        switch (field.getType()) {
            case FieldTypes.TEXT:
                return ValString.create((String) object);
            case FieldTypes.INTEGER:
                return ValInteger.create((Integer) object);
            case FieldTypes.LONG:
            case FieldTypes.ID:
            case FieldTypes.DATE:
                return ValLong.create((long) object);
            case FieldTypes.DOC_REF:
                return ValString.create(((DocRef) object).toInfoString());
            default:
                throw new RuntimeException("Unexpected field type " + field.getType());
        }
    }

    private long getDate(final String fieldName, final String value, final long nowEpochMs) {
        try {
            // TODO @AT Get the timezone from the user's local?
            return DateExpressionParser.parse(value, ZoneOffset.UTC.getId(), nowEpochMs)
                    .map(dt -> dt.toInstant().toEpochMilli())
                    .orElseThrow(() -> new RuntimeException("Expected a standard date value for field \"" + fieldName
                            + "\" but was given string \"" + value + "\""));
        } catch (final RuntimeException e) {
            throw new RuntimeException("Expected a standard date value for field \"" + fieldName
                    + "\" but was given string \"" + value + "\"");
        }
    }

}
