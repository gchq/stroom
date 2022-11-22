package stroom.pipeline.refdata;

import stroom.bytebuffer.ByteBufferPool;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValInteger;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ValString;
import stroom.dashboard.expression.v1.ValuesConsumer;
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
import stroom.node.api.FindNodeCriteria;
import stroom.node.api.NodeService;
import stroom.pipeline.refdata.RefDataLookupRequest.ReferenceLoader;
import stroom.pipeline.refdata.store.ProcessingInfoResponse;
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
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.pipeline.scope.PipelineScopeRunnable;
import stroom.util.rest.RestUtil;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResourcePaths;
import stroom.util.time.StroomDuration;

import com.google.common.base.Strings;
import net.sf.saxon.Configuration;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.Receiver;

import java.io.StringWriter;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.SyncInvoker;

public class ReferenceDataServiceImpl implements ReferenceDataService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReferenceDataServiceImpl.class);

    private static final DocRef REF_STORE_PSEUDO_DOC_REF = new DocRef(
            "Searchable",
            "Reference Data Store",
            "Reference Data Store (This Node Only)");

    private static final List<Condition> SUPPORTED_STRING_CONDITIONS = List.of(Condition.EQUALS);
    private static final List<Condition> SUPPORTED_DOC_REF_CONDITIONS = List.of(Condition.IS_DOC_REF, Condition.EQUALS);

    private static final TextField KEY_FIELD = new TextField(
            "Key", true, SUPPORTED_STRING_CONDITIONS);
    private static final TextField VALUE_FIELD = new TextField(
            "Value", true, SUPPORTED_STRING_CONDITIONS);
    private static final IntegerField VALUE_REF_COUNT_FIELD = new IntegerField(
            "Value Reference Count", false);
    private static final TextField MAP_NAME_FIELD = new TextField(
            "Map Name", true, SUPPORTED_STRING_CONDITIONS);
    private static final DateField CREATE_TIME_FIELD = new DateField(
            "Create Time", true);
    private static final DateField EFFECTIVE_TIME_FIELD = new DateField(
            "Effective Time", true);
    private static final DateField LAST_ACCESSED_TIME_FIELD = new DateField(
            "Last Accessed Time", true);
    private static final DocRefField PIPELINE_FIELD = new DocRefField(PipelineDoc.DOCUMENT_TYPE,
            "Reference Loader Pipeline", true, SUPPORTED_DOC_REF_CONDITIONS);
    private static final TextField PROCESSING_STATE_FIELD = new TextField(
            "Processing State", false);
    private static final IdField STREAM_ID_FIELD = new IdField(
            "Stream ID", false);
    private static final LongField PART_NO_FIELD = new LongField(
            "Part Number", false);
    private static final TextField PIPELINE_VERSION_FIELD = new TextField(
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
            PART_NO_FIELD,
            PIPELINE_VERSION_FIELD);

    private static final DataSource DATA_SOURCE = DataSource
            .builder()
            .fields(FIELDS)
            .timeField(CREATE_TIME_FIELD)
            .build();

    private static final Map<String, AbstractField> FIELD_NAME_TO_FIELD_MAP = FIELDS.stream()
            .collect(Collectors.toMap(AbstractField::getName, Function.identity()));

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
            Map.entry(PART_NO_FIELD.getName(), refStoreEntry ->
                    refStoreEntry.getMapDefinition().getRefStreamDefinition().getPartIndex() + 1),
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
    private final ByteBufferPool byteBufferPool;
    private final NodeService nodeService;

    @Inject
    public ReferenceDataServiceImpl(final RefDataStoreFactory refDataStoreFactory,
                                    final SecurityContext securityContext,
                                    final FeedStore feedStore,
                                    final Provider<ReferenceData> referenceDataProvider,
                                    final RefDataValueConverter refDataValueConverter,
                                    final PipelineScopeRunnable pipelineScopeRunnable,
                                    final TaskContextFactory taskContextFactory,
                                    final Factory refDataValueProxyConsumerFactoryFactory,
                                    final ByteBufferPool byteBufferPool,
                                    final NodeService nodeService) {
        this.refDataStore = refDataStoreFactory.getOffHeapStore();
        this.securityContext = securityContext;
        this.feedStore = feedStore;
        this.referenceDataProvider = referenceDataProvider;
        this.refDataValueConverter = refDataValueConverter;
        this.pipelineScopeRunnable = pipelineScopeRunnable;
        this.taskContextFactory = taskContextFactory;
        this.refDataValueProxyConsumerFactoryFactory = refDataValueProxyConsumerFactoryFactory;
        this.byteBufferPool = byteBufferPool;
        this.nodeService = nodeService;
    }

    @Override
    public List<RefStoreEntry> entries(final int limit) {
        return entries(limit, null, null);
    }

    @Override
    public List<RefStoreEntry> entries(final int limit,
                                       final Long refStreamId,
                                       final String mapName) {
        return withPermissionCheck(() -> {
            final List<RefStoreEntry> entries;
            try {
                Predicate<RefStoreEntry> predicate = entry -> true;

                if (refStreamId != null) {
                    predicate = predicate.and(refStoreEntry ->
                            refStoreEntry.getMapDefinition()
                                    .getRefStreamDefinition()
                                    .getStreamId() == refStreamId);
                }

                if (!Strings.isNullOrEmpty(mapName)) {
                    predicate = predicate.and(refStoreEntry ->
                            mapName.equals(refStoreEntry.getMapDefinition().getMapName()));
                }

                entries = refDataStore.list(limit, predicate);
            } catch (Exception e) {
                LOGGER.error("Error listing reference data", e);
                throw e;
            }
            return entries;
        });
    }

    @Override
    public List<ProcessingInfoResponse> refStreamInfo(final int limit) {
        return refStreamInfo(limit, null, null);
    }

    @Override
    public List<ProcessingInfoResponse> refStreamInfo(final int limit,
                                                      final Long refStreamId,
                                                      final String mapName) {

        return withPermissionCheck(() -> {
            final List<ProcessingInfoResponse> entries;
            try {
                Predicate<ProcessingInfoResponse> predicate = entry -> true;

                if (refStreamId != null) {
                    predicate = predicate.and(refStreamProcessingInfo ->
                            refStreamProcessingInfo.getRefStreamDefinition()
                                    .getStreamId() == refStreamId);
                }

                if (!Strings.isNullOrEmpty(mapName)) {
                    predicate = predicate.and(refStreamProcessingInfo ->
                            refStreamProcessingInfo.getMaps().containsKey(mapName));
                }

                entries = refDataStore.listProcessingInfo(limit, predicate);
            } catch (Exception e) {
                LOGGER.error("Error listing ref stream processing info data", e);
                throw e;
            }
            return entries;
        });
    }

    @Override
    public String lookup(final RefDataLookupRequest refDataLookupRequest) {
        if (refDataLookupRequest == null) {
            throw RestUtil.badRequest("Missing request object");
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
    public void purge(final StroomDuration purgeAge, final String nodeName) {

        securityContext.secure(PermissionNames.MANAGE_CACHE_PERMISSION, () -> {

            final List<String> nodeNames = getNodeList(nodeName);
            final Set<String> failedNodes = new ConcurrentSkipListSet<>();
            final AtomicReference<Throwable> exception = new AtomicReference<>();

            final String nodeNameStr = nodeNames.size() == 1
                    ? "node " + nodeName
                    : "all nodes";
            final String taskName = "Reference Data Purge on "
                    + nodeNameStr
                    + " (purge age: " + purgeAge.toString() + ")";

            taskContextFactory.context(
                    taskName,
                    parentTaskContext -> {
                        @SuppressWarnings("unchecked") final CompletableFuture<Void>[] futures = nodeNames.stream()
                                .map(nodeName2 -> {

                                    final Runnable runnable = taskContextFactory.childContext(
                                            parentTaskContext,
                                            "Reference Data Purge on node " + nodeName2,
                                            taskContext -> {
                                                nodeService.remoteRestCall(
                                                        nodeName2,
                                                        () -> ResourcePaths.buildAuthenticatedApiPath(
                                                                ReferenceDataResource.BASE_PATH,
                                                                ReferenceDataResource.PURGE_BY_AGE_SUB_PATH,
                                                                purgeAge.getValueAsStr()),
                                                        () ->
                                                                purgeLocally(purgeAge),
                                                        SyncInvoker::delete,
                                                        Collections.singletonMap(
                                                                ReferenceDataResource.QUERY_PARAM_NODE_NAME,
                                                                nodeName2));
                                            });

                                    return CompletableFuture
                                            .runAsync(runnable)
                                            .exceptionally(throwable -> {
                                                failedNodes.add(nodeName2);
                                                exception.set(throwable);
                                                LOGGER.error(
                                                        "Error purging reference data store on node [{}]: {}. " +
                                                                "Enable DEBUG for stacktrace",
                                                        nodeName2,
                                                        throwable.getMessage());
                                                LOGGER.debug("Error purging ref data store on node [{}]",
                                                        nodeName2, throwable);
                                                return null;
                                            });
                                })
                                .toArray(CompletableFuture[]::new);

                        CompletableFuture.allOf(futures).join();

                        if (!failedNodes.isEmpty()) {
                            throw new RuntimeException(LogUtil.message(
                                    "Error puring ref data store ({}) on node(s) [{}]. See logs for details",
                                    purgeAge,
                                    String.join(",", failedNodes)),
                                    exception.get());
                        }
                    }).run();
        });
    }

    private void purgeLocally(final StroomDuration purgeAge) {
        LOGGER.logDurationIfDebugEnabled(
                () ->
                        refDataStore.purgeOldData(purgeAge),
                LogUtil.message("Performing Purge for entries older than {}", purgeAge));

    }

    @Override
    public void purge(final long refStreamId, final String nodeName) {

        securityContext.secure(PermissionNames.MANAGE_CACHE_PERMISSION, () -> {
            final List<String> nodeNames = getNodeList(nodeName);
            final Set<String> failedNodes = new ConcurrentSkipListSet<>();
            final AtomicReference<Throwable> exception = new AtomicReference<>();

            final String nodeNameStr = nodeNames.size() == 1
                    ? "node " + nodeName
                    : "all nodes";
            final String taskName = "Reference Data Purge on "
                    + nodeNameStr
                    + " (Stream: " + refStreamId + ")";

            taskContextFactory.context(
                    taskName,
                    parentTaskContext -> {

                        @SuppressWarnings("unchecked") final CompletableFuture<Void>[] futures = nodeNames.stream()
                                .map(nodeName2 -> {

                                    final Runnable runnable = taskContextFactory.childContext(
                                            parentTaskContext,
                                            "Reference Data Purge on node " + nodeName2,
                                            taskContext -> {
                                                nodeService.remoteRestCall(
                                                        nodeName2,
                                                        () -> ResourcePaths.buildAuthenticatedApiPath(
                                                                ReferenceDataResource.BASE_PATH,
                                                                ReferenceDataResource.PURGE_BY_STREAM_SUB_PATH,
                                                                Long.toString(refStreamId)),
                                                        () ->
                                                                purgeLocally(refStreamId),
                                                        SyncInvoker::delete,
                                                        Collections.singletonMap(
                                                                ReferenceDataResource.QUERY_PARAM_NODE_NAME,
                                                                nodeName2));
                                            });

                                    return CompletableFuture
                                            .runAsync(runnable)
                                            .exceptionally(throwable -> {
                                                failedNodes.add(nodeName2);
                                                exception.set(throwable);
                                                LOGGER.error(
                                                        "Error purging reference data store on node [{}]: {}. " +
                                                                "Enable DEBUG for stacktrace",
                                                        nodeName2,
                                                        throwable.getMessage());
                                                LOGGER.debug("Error purging ref data store on node [{}]",
                                                        nodeName2, throwable);
                                                return null;
                                            });
                                })
                                .toArray(CompletableFuture[]::new);

                        CompletableFuture.allOf(futures).join();

                        if (!failedNodes.isEmpty()) {
                            throw new RuntimeException(LogUtil.message(
                                    "Error puring ref data store ({}) on node(s) [{}]. See logs for details",
                                    refStreamId,
                                    String.join(",", failedNodes)),
                                    exception.get());
                        }

                    }).run();
        });
    }

    public void purgeLocally(final long refStreamId) {
        LOGGER.logDurationIfDebugEnabled(
                () -> refDataStore.purge(refStreamId),
                LogUtil.message("Performing Purge for ref stream {}", refStreamId));
    }

    @Override
    public void clearBufferPool(final String nodeName) {
        securityContext.secure(PermissionNames.MANAGE_CACHE_PERMISSION, () -> {
            final List<String> nodeNames = getNodeList(nodeName);

            final Set<String> failedNodes = new ConcurrentSkipListSet<>();
            final AtomicReference<Throwable> exception = new AtomicReference<>();

            final String nodeNameStr = nodeNames.size() == 1
                    ? "node " + nodeName
                    : "all nodes";
            final String taskName = "Clearing Byte Buffer Pool on "
                    + nodeNameStr;

            taskContextFactory.context(
                    nodeNameStr,
                    parentTaskContext -> {
                        @SuppressWarnings("unchecked") final CompletableFuture<Void>[] futures = nodeNames.stream()
                                .map(nodeName2 -> {

                                    final Runnable runnable = taskContextFactory.childContext(
                                            parentTaskContext,
                                            "Clearing Byte Buffer Pool on node " + nodeName2,
                                            taskContext -> {
                                                nodeService.remoteRestCall(
                                                        nodeName2,
                                                        () -> ResourcePaths.buildAuthenticatedApiPath(
                                                                ReferenceDataResource.BASE_PATH,
                                                                ReferenceDataResource.CLEAR_BUFFER_POOL_PATH),
                                                        byteBufferPool::clear,
                                                        SyncInvoker::delete,
                                                        Collections.singletonMap(
                                                                ReferenceDataResource.QUERY_PARAM_NODE_NAME,
                                                                nodeName2));
                                            });

                                    return CompletableFuture
                                            .runAsync(runnable)
                                            .exceptionally(throwable -> {
                                                failedNodes.add(nodeName2);
                                                exception.set(throwable);
                                                LOGGER.error(
                                                        "Error clearing byte buffer pool on node [{}]: {}. " +
                                                                "Enable DEBUG for stacktrace",
                                                        nodeName2,
                                                        throwable.getMessage());
                                                LOGGER.debug("Error clearing byte buffer pool on node [{}]",
                                                        nodeName2, throwable);
                                                return null;
                                            });
                                })
                                .toArray(CompletableFuture[]::new);

                        CompletableFuture.allOf(futures).join();

                        if (!failedNodes.isEmpty()) {
                            throw new RuntimeException(LogUtil.message(
                                    "Error clearing byte buffer pool on node(s) [{}]. See logs for details",
                                    String.join(",", failedNodes)), exception.get());
                        }
                    }).run();
        });
    }

    private List<String> getNodeList(final String nodeName) {
        return nodeName == null
                ? nodeService.findNodeNames(new FindNodeCriteria())
                : Collections.singletonList(nodeName);
    }

    private String performLookup(final RefDataLookupRequest refDataLookupRequest) {
        try {
            final LookupIdentifier lookupIdentifier = LookupIdentifier.of(
                    refDataLookupRequest.getMapName(),
                    refDataLookupRequest.getKey(),
                    refDataLookupRequest.getOptEffectiveTimeAsEpochMs()
                            .orElse(Instant.now().toEpochMilli()));

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
                        refDataLookupRequest.getOptEffectiveTimeAsEpochMs()
                                .map(Instant::ofEpochMilli)
                                .map(Objects::toString)
                                .orElse("null")));
            }

            return stringWriter.toString();
        } catch (Exception e) {
            // Errors for unknown keys are to be expected
            if (!(e instanceof NotFoundException)) {
                LOGGER.error("Error looking up {}", refDataLookupRequest, e);
            }
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

                        // TODO validate the stream type name
                        final String streamType = Objects.requireNonNullElse(
                                referenceLoader.getStreamType(),
                                StreamTypeNames.REFERENCE);

                        return new PipelineReference(
                                referenceLoader.getLoaderPipeline(),
                                feedDocRef,
                                streamType);
                    })
                    .collect(Collectors.toList());
        }
    }

    private DocRef getFeedDocRef(final ReferenceLoader referenceLoader) {
        if (referenceLoader == null) {
            throw RestUtil.badRequest("Null referenceLoader");
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
                                RestUtil.badRequest("Unknown feed " + referenceLoader.getReferenceFeed()));
            } else {
                throw RestUtil.badRequest(
                        "Need to provide a name or a UUID and name for each referenceLoader referenceFeed");
            }
        }
    }


    private <T> T withPermissionCheck(final Supplier<T> supplier) {
        // TODO @AT Need some kind of fine grained doc permission check on the pipe associated with each entry
        //   but this will do for a first stab
        if (securityContext.isAdmin()) {
            return supplier.get();
        } else {
            throw new PermissionException(securityContext.getUserId(),
                    "You do not have permission to view reference data");
        }
    }

    private void withPermissionCheck(final Runnable runnable) {
        // TODO @AT Need some kind of fine grained doc permission check on the pipe associated with each entry
        //   but this will do for a first stab
        if (securityContext.isAdmin()) {
            runnable.run();
        } else {
            throw new PermissionException(securityContext.getUserId(),
                    "You do not have permission to view reference data");
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
                       final ValuesConsumer consumer) {

        withPermissionCheck(() -> LOGGER.logDurationIfInfoEnabled(
                () -> taskContextFactory.context("Querying reference data store", taskContext ->
                                doSearch(criteria, fields, consumer, taskContext))
                        .run(),
                "Querying ref store"));
    }

    private void doSearch(final ExpressionCriteria criteria,
                          final AbstractField[] fields,
                          final ValuesConsumer consumer,
                          final TaskContext taskContext) {
        // TODO @AT This is a temporary very crude impl to see if it works.
        //  The search code ought to be pushed down to the offHeapStore so it can query the many DBs
        //  selectively and in a MUCH more efficient way, e.g. using start/stop keys on the kv store scan.

        // TODO @AT Do we need to do something with param replacement? e.g.:
        //     final Map<String, String> paramMap = ExpressionParamUtil.createParamMap(query.getParams());
        //     expression = ExpressionUtil.replaceExpressionParameters(expression, paramMap);
        //   But we don't have a paramMap.

        // TODO @AT Need to check if we have any common code for this sort of ExpOp, ExpTerm, Val munging

        // TODO @AT Need to run the query as a task so it can be monitored from the UI.

        // TODO @AT need to get rid of the up front limit. Instead we need a method on the refstore to
        //  allow us consume a stream of entries within a read txn. The limit can then be set after the
        //  filtering has happened.
        final Predicate<RefStoreEntry> predicate = buildEntryPredicate(criteria);

        final long skipCount = Optional.ofNullable(criteria)
                .flatMap(criteria2 -> Optional.ofNullable(criteria.getPageRequest()))
                .flatMap(pageRequest -> Optional.ofNullable(pageRequest.getOffset()))
                .orElse(0);

        final int limit = Optional.ofNullable(criteria)
                .flatMap(criteria2 -> Optional.ofNullable(criteria.getPageRequest()))
                .flatMap(pageRequest -> Optional.ofNullable(pageRequest.getLength()))
                .orElse(Integer.MAX_VALUE);

        LOGGER.debug("Searching ref entries with criteria {}, skipCount {}, limit {}",
                criteria, skipCount, limit);

        refDataStore.consumeEntryStream(stream -> {
            stream
                    .filter(predicate)
                    .skip(skipCount)
                    .limit(limit)
                    .forEach(refStoreEntry -> {
                        if (taskContext.isTerminated()) {
                            throw new RuntimeException("Aborting search due to task termination");
                        }

                        final Val[] valArr = new Val[fields.length];

                        // Useful for slowing down the search in dev to test termination
//                        try {
//                            Thread.sleep(50);
//                        } catch (InterruptedException e) {
//                            Thread.currentThread().interrupt();
//                        }

                        for (int i = 0; i < fields.length; i++) {
                            AbstractField field = fields[i];
                            // May be a custom field that we obvs can't extract
                            if (field != null) {
                                final Object value = FIELD_TO_EXTRACTOR_MAP.get(fields[i].getName())
                                        .apply(refStoreEntry);
                                valArr[i] = convertToVal(value, fields[i]);
                            }
                        }
                        consumer.add(valArr);
                    });
            return null;
        });
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
            return buildTextFieldPredicate(expressionTerm, refStoreEntry ->
                    (String) FIELD_TO_EXTRACTOR_MAP.get(expressionTerm.getField()).apply(refStoreEntry));
        } else if (abstractField.getType().equals(FieldTypes.LONG)) {
            return buildLongFieldPredicate(expressionTerm, refStoreEntry ->
                    (Long) FIELD_TO_EXTRACTOR_MAP.get(expressionTerm.getField()).apply(refStoreEntry));
        } else if (abstractField.getType().equals(FieldTypes.DATE)) {
            return buildDateFieldPredicate(expressionTerm, refStoreEntry ->
                    (Long) FIELD_TO_EXTRACTOR_MAP.get(expressionTerm.getField()).apply(refStoreEntry));
        } else if (abstractField.getType().equals(FieldTypes.DOC_REF)) {
            return buildDocRefFieldPredicate(expressionTerm, refStoreEntry ->
                    (DocRef) FIELD_TO_EXTRACTOR_MAP.get(expressionTerm.getField()).apply(refStoreEntry));
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
            return rec ->
                    Objects.equals(valueExtractor.apply(rec), termValue);
        } else if (expressionTerm.getCondition().equals(Condition.GREATER_THAN)) {
            return rec ->
                    valueExtractor.apply(rec) > termValue;
        } else if (expressionTerm.getCondition().equals(Condition.GREATER_THAN_OR_EQUAL_TO)) {
            return rec ->
                    valueExtractor.apply(rec) >= termValue;
        } else if (expressionTerm.getCondition().equals(Condition.LESS_THAN)) {
            return rec ->
                    valueExtractor.apply(rec) < termValue;
        } else if (expressionTerm.getCondition().equals(Condition.LESS_THAN_OR_EQUAL_TO)) {
            return rec ->
                    valueExtractor.apply(rec) <= termValue;
        } else {
            throw new RuntimeException("Unexpected condition " + expressionTerm.getCondition());
        }
    }

    private Predicate<RefStoreEntry> buildDateFieldPredicate(final ExpressionTerm expressionTerm,
                                                             final Function<RefStoreEntry, Long> valueExtractor) {
        // TODO @AT Handle stuff like 'today() -1d'
        // TODO @AT Need to get now() once for the query
        final Long termValue = getDate(expressionTerm.getField(),
                expressionTerm.getValue(),
                Instant.now().toEpochMilli());
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
        final DocRef termValue = DocRef.builder()
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
        }
        if (docRef1 == null) {
            return false;
        }
        if (docRef2 == null) {
            return false;
        } else {
            return Objects.equals(docRef1.getUuid(), docRef2.getUuid());
        }
    }

    private boolean docRefsEqualOnName(final DocRef docRef1, final DocRef docRef2) {
        if (docRef1 == null && docRef2 == null) {
            return false;
        }
        if (docRef1 == null) {
            return false;
        }
        if (docRef2 == null) {
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
            return DateExpressionParser.parse(value, nowEpochMs)
                    .map(dt -> dt.toInstant().toEpochMilli())
                    .orElseThrow(() -> new RuntimeException("Expected a standard date value for field \"" + fieldName
                            + "\" but was given string \"" + value + "\""));
        } catch (final RuntimeException e) {
            throw new RuntimeException("Expected a standard date value for field \"" + fieldName
                    + "\" but was given string \"" + value + "\"");
        }
    }

}
