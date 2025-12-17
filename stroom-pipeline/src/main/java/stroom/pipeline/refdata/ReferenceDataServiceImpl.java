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

package stroom.pipeline.refdata;

import stroom.bytebuffer.ByteBufferPool;
import stroom.data.shared.StreamTypeNames;
import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
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
import stroom.pipeline.shared.ReferenceDataFields;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.common.v2.FieldInfoResultPageFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskTerminatedException;
import stroom.util.PredicateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.pipeline.scope.PipelineScopeRunnable;
import stroom.util.rest.RestUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.ResultPage;
import stroom.util.time.StroomDuration;

import com.google.common.base.Strings;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.client.SyncInvoker;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ReferenceDataServiceImpl implements ReferenceDataService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReferenceDataServiceImpl.class);
    private static final Map<String, QueryField> FIELD_NAME_TO_FIELD_MAP = ReferenceDataFields.FIELDS.stream()
            .collect(Collectors.toMap(QueryField::getFldName, Function.identity()));

    private static final Map<String, Function<RefStoreEntry, Object>> FIELD_TO_EXTRACTOR_MAP = Map.ofEntries(
            Map.entry(ReferenceDataFields.FEED_NAME_FIELD.getFldName(),
                    RefStoreEntry::getFeedName),
            Map.entry(ReferenceDataFields.KEY_FIELD.getFldName(),
                    RefStoreEntry::getKey),
            Map.entry(ReferenceDataFields.VALUE_FIELD.getFldName(),
                    RefStoreEntry::getValue),
            Map.entry(ReferenceDataFields.VALUE_REF_COUNT_FIELD.getFldName(),
                    RefStoreEntry::getValueReferenceCount),
            Map.entry(ReferenceDataFields.MAP_NAME_FIELD.getFldName(), refStoreEntry ->
                    refStoreEntry.getMapDefinition().getMapName()),
            Map.entry(ReferenceDataFields.CREATE_TIME_FIELD.getFldName(), refStoreEntry ->
                    refStoreEntry.getRefDataProcessingInfo().getCreateTimeEpochMs()),
            Map.entry(ReferenceDataFields.EFFECTIVE_TIME_FIELD.getFldName(), refStoreEntry ->
                    refStoreEntry.getRefDataProcessingInfo().getEffectiveTimeEpochMs()),
            Map.entry(ReferenceDataFields.LAST_ACCESSED_TIME_FIELD.getFldName(), refStoreEntry ->
                    refStoreEntry.getRefDataProcessingInfo().getLastAccessedTimeEpochMs()),
            Map.entry(ReferenceDataFields.PIPELINE_FIELD.getFldName(), refStoreEntry ->
                    refStoreEntry.getMapDefinition().getRefStreamDefinition().getPipelineDocRef()),
            Map.entry(ReferenceDataFields.PROCESSING_STATE_FIELD.getFldName(), refStoreEntry ->
                    refStoreEntry.getRefDataProcessingInfo().getProcessingState().getDisplayName()),
            Map.entry(ReferenceDataFields.STREAM_ID_FIELD.getFldName(), refStoreEntry ->
                    refStoreEntry.getMapDefinition().getRefStreamDefinition().getStreamId()),
            Map.entry(ReferenceDataFields.PART_NO_FIELD.getFldName(), refStoreEntry ->
                    refStoreEntry.getMapDefinition().getRefStreamDefinition().getPartIndex() + 1),
            Map.entry(ReferenceDataFields.PIPELINE_VERSION_FIELD.getFldName(), refStoreEntry ->
                    refStoreEntry.getMapDefinition().getRefStreamDefinition().getPipelineVersion()));

    private final RefDataStore refDataStore;
    private final RefDataStoreFactory refDataStoreFactory;
    private final SecurityContext securityContext;
    private final FeedStore feedStore;
    private final Provider<ReferenceData> referenceDataProvider;
    private final RefDataValueConverter refDataValueConverter;
    private final PipelineScopeRunnable pipelineScopeRunnable;
    private final TaskContextFactory taskContextFactory;
    private final RefDataValueProxyConsumerFactory.Factory refDataValueProxyConsumerFactoryFactory;
    private final ByteBufferPool byteBufferPool;
    private final NodeService nodeService;
    private final WordListProvider wordListProvider;
    private final DocRefInfoService docRefInfoService;
    private final FieldInfoResultPageFactory fieldInfoResultPageFactory;

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
                                    final NodeService nodeService,
                                    final WordListProvider wordListProvider,
                                    final DocRefInfoService docRefInfoService,
                                    final FieldInfoResultPageFactory fieldInfoResultPageFactory) {
        this.refDataStore = refDataStoreFactory.getOffHeapStore();
        this.refDataStoreFactory = refDataStoreFactory;
        this.securityContext = securityContext;
        this.feedStore = feedStore;
        this.referenceDataProvider = referenceDataProvider;
        this.refDataValueConverter = refDataValueConverter;
        this.pipelineScopeRunnable = pipelineScopeRunnable;
        this.taskContextFactory = taskContextFactory;
        this.refDataValueProxyConsumerFactoryFactory = refDataValueProxyConsumerFactoryFactory;
        this.byteBufferPool = byteBufferPool;
        this.nodeService = nodeService;
        this.wordListProvider = wordListProvider;
        this.docRefInfoService = docRefInfoService;
        this.fieldInfoResultPageFactory = fieldInfoResultPageFactory;
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
            } catch (final Exception e) {
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
            } catch (final Exception e) {
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

        return securityContext.secureResult(AppPermission.VIEW_DATA_PERMISSION, () ->
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

        securityContext.secure(AppPermission.MANAGE_CACHE_PERMISSION, () -> {

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
                                            taskContext -> nodeService.remoteRestCall(
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
                                                            nodeName2)));

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

    @Override
    public void purge(final String feedName,
                      final StroomDuration purgeAge,
                      final String nodeName) {
        securityContext.secure(AppPermission.MANAGE_CACHE_PERMISSION, () -> {

            final List<String> nodeNames = getNodeList(nodeName);
            final Set<String> failedNodes = new ConcurrentSkipListSet<>();
            final AtomicReference<Throwable> exception = new AtomicReference<>();

            final String nodeNameStr = nodeNames.size() == 1
                    ? "node " + nodeName
                    : "all nodes";
            final String taskName = "Reference Data Purge on "
                                    + nodeNameStr
                                    + " (feed: " + feedName + ", purge age: " + purgeAge.toString() + ")";

            taskContextFactory.context(
                    taskName,
                    parentTaskContext -> {
                        @SuppressWarnings("unchecked") final CompletableFuture<Void>[] futures = nodeNames.stream()
                                .map(nodeName2 -> {

                                    final Runnable runnable = taskContextFactory.childContext(
                                            parentTaskContext,
                                            "Reference Data Purge on node " + nodeName2,
                                            taskContext -> nodeService.remoteRestCall(
                                                    nodeName2,
                                                    () -> ResourcePaths.buildAuthenticatedApiPath(
                                                            ReferenceDataResource.BASE_PATH,
                                                            ReferenceDataResource.PURGE_BY_AGE_SUB_PATH,
                                                            purgeAge.getValueAsStr()),
                                                    () ->
                                                            purgeLocally(feedName, purgeAge),
                                                    SyncInvoker::delete,
                                                    Collections.singletonMap(
                                                            ReferenceDataResource.QUERY_PARAM_NODE_NAME,
                                                            nodeName2)));

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

    private void purgeLocally(final String feedName,
                              final StroomDuration purgeAge) {
        LOGGER.logDurationIfDebugEnabled(
                () -> {
                    final RefDataStore offHeapStore = refDataStoreFactory.getOffHeapStore(feedName);
                    Objects.requireNonNull(offHeapStore, () -> "Null ref store for feed " + feedName);
                    offHeapStore.purgeOldData(purgeAge);
                },
                LogUtil.message("Performing Purge for entries older than {} in feed {}", purgeAge, feedName));
    }

    @Override
    public void purge(final long refStreamId, final String nodeName) {

        securityContext.secure(AppPermission.MANAGE_CACHE_PERMISSION, () -> {
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
        securityContext.secure(AppPermission.MANAGE_CACHE_PERMISSION, () -> {
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

            final ReferenceDataResult referenceDataResult = new ReferenceDataResult(
                    lookupIdentifier);

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
        } catch (final Exception e) {
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
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to view reference data");
        }
    }

    private void withPermissionCheck(final Runnable runnable) {
        // TODO @AT Need some kind of fine grained doc permission check on the pipe associated with each entry
        //   but this will do for a first stab
        if (securityContext.isAdmin()) {
            runnable.run();
        } else {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to view reference data");
        }
    }

    @Override
    public String getDataSourceType() {
        return ReferenceDataFields.REF_STORE_PSEUDO_DOC_REF.getType();
    }

    @Override
    public List<DocRef> getDataSourceDocRefs() {
        return Collections.singletonList(ReferenceDataFields.REF_STORE_PSEUDO_DOC_REF);
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        if (!ReferenceDataFields.REF_STORE_PSEUDO_DOC_REF.equals(criteria.getDataSourceRef())) {
            return ResultPage.empty();
        }
        return fieldInfoResultPageFactory.create(criteria, getFields());
    }

    private List<QueryField> getFields() {
        return ReferenceDataFields.FIELDS;
    }

    @Override
    public int getFieldCount(final DocRef docRef) {
        return NullSafe.size(getFields());
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final DateTimeSettings dateTimeSettings,
                       final ValuesConsumer consumer) {
        withPermissionCheck(() -> LOGGER.logDurationIfInfoEnabled(
                () -> taskContextFactory.context("Querying reference data store", taskContext ->
                                doSearch(criteria, fieldIndex, consumer, taskContext))
                        .run(),
                "Querying ref store"));
    }

    private void doSearch(final ExpressionCriteria criteria,
                          final FieldIndex fieldIndex,
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
        final Predicate<RefStoreEntry> filter = buildEntryPredicate(criteria);

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

        final AtomicLong allItemsCounter = new AtomicLong(0);
        final AtomicLong consumedCounter = new AtomicLong(0);
        final Predicate<RefStoreEntry> takeWhilePredicate = refStoreEntry ->
                consumedCounter.incrementAndGet() <= limit;
        final BooleanSupplier skipTest = skipCount == 0
                ? () -> true
                : () -> allItemsCounter.incrementAndGet() > skipCount;

        refDataStore.consumeEntries(filter, takeWhilePredicate, refStoreEntry -> {
            if (taskContext.isTerminated() || Thread.currentThread().isInterrupted()) {
                throw new TaskTerminatedException();
            }
            if (skipTest.getAsBoolean()) {
                final String[] fields = fieldIndex.getFields();
                final Val[] valArr = new Val[fields.length];

                // Useful for slowing down the search in dev to test termination
                //ThreadUtil.sleepIgnoringInterrupts(50);

                for (int i = 0; i < fields.length; i++) {
                    final String fieldName = fields[i];
                    final QueryField field = FIELD_NAME_TO_FIELD_MAP.get(fieldName);
                    // May be a custom field that we obvs can't extract
                    if (field != null) {
                        final Object value = FIELD_TO_EXTRACTOR_MAP.get(field.getFldName())
                                .apply(refStoreEntry);
                        valArr[i] = convertToVal(value, field);
                    }
                }
                consumer.accept(Val.of(valArr));
            }
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
        } catch (final Exception e) {
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
                    final boolean testResult = childPredicate.test(val);

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
                    final boolean testResult = childPredicate.test(val);

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
                    final boolean testResult = !childPredicate.test(val);

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
        final QueryField abstractField = FIELD_NAME_TO_FIELD_MAP.get(expressionTerm.getField());

        return switch (abstractField.getFldType()) {
            case TEXT -> buildTextFieldPredicate(expressionTerm, refStoreEntry ->
                    (String) FIELD_TO_EXTRACTOR_MAP.get(expressionTerm.getField()).apply(refStoreEntry));
            case LONG -> buildLongFieldPredicate(expressionTerm, refStoreEntry ->
                    (Long) FIELD_TO_EXTRACTOR_MAP.get(expressionTerm.getField()).apply(refStoreEntry));
            case DATE -> buildDateFieldPredicate(expressionTerm, refStoreEntry ->
                    (Long) FIELD_TO_EXTRACTOR_MAP.get(expressionTerm.getField()).apply(refStoreEntry));
            case DOC_REF -> buildDocRefFieldPredicate(expressionTerm, refStoreEntry ->
                    (DocRef) FIELD_TO_EXTRACTOR_MAP.get(expressionTerm.getField()).apply(refStoreEntry));
            default -> throw new RuntimeException("Unsupported term " + expressionTerm);
        };

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
        final String termValue = NullSafe.get(expressionTerm.getValue(), String::trim);
        if (termValue == null || termValue.isEmpty()) {
            return val -> false;
        } else {
            final Predicate<String> strPredicate = switch (expressionTerm.getCondition()) {
                case EQUALS -> PredicateUtil.createWildCardedFilterPredicate(
                        termValue, true, true);
                case NOT_EQUALS -> PredicateUtil.createWildCardedFilterPredicate(
                        termValue, true, true).negate();
                case CONTAINS -> PredicateUtil.createWildCardedFilterPredicate(
                        termValue, false, true);
                case IN -> PredicateUtil.createWildCardedInPredicate(termValue, true);
                case IN_DICTIONARY -> {
                    final String[] words = wordListProvider.getWords(expressionTerm.getDocRef());
                    yield PredicateUtil.createWildCardedInPredicate(words, true);
                }
                default -> throw new RuntimeException("Unexpected condition " + expressionTerm.getCondition());
            };
            return rec -> {
                final String entryValue = valueExtractor.apply(rec);
                return strPredicate.test(entryValue);
            };
        }
    }

    private Predicate<RefStoreEntry> buildLongFieldPredicate(final ExpressionTerm expressionTerm,
                                                             final Function<RefStoreEntry, Long> valueExtractor) {
        final Long termValue = Long.valueOf(expressionTerm.getValue());
        return switch (expressionTerm.getCondition()) {
            case EQUALS -> rec ->
                    Objects.equals(valueExtractor.apply(rec), termValue);
            case NOT_EQUALS -> rec ->
                    !Objects.equals(valueExtractor.apply(rec), termValue);
            case GREATER_THAN -> rec ->
                    valueExtractor.apply(rec) > termValue;
            case GREATER_THAN_OR_EQUAL_TO -> rec ->
                    valueExtractor.apply(rec) >= termValue;
            case LESS_THAN -> rec ->
                    valueExtractor.apply(rec) < termValue;
            case LESS_THAN_OR_EQUAL_TO -> rec ->
                    valueExtractor.apply(rec) <= termValue;
            default -> throw new RuntimeException("Unexpected condition " + expressionTerm.getCondition());
        };
    }

    private Predicate<RefStoreEntry> buildDateFieldPredicate(final ExpressionTerm expressionTerm,
                                                             final Function<RefStoreEntry, Long> valueExtractor) {
        // TODO @AT Handle stuff like 'today() -1d'
        // TODO @AT Need to get now() once for the query
        final Long termValue = DateExpressionParser.getMs(expressionTerm.getField(), expressionTerm.getValue());
        return switch (expressionTerm.getCondition()) {
            case EQUALS -> rec ->
                    Objects.equals(valueExtractor.apply(rec), termValue);
            case NOT_EQUALS -> rec ->
                    !Objects.equals(valueExtractor.apply(rec), termValue);
            case GREATER_THAN -> rec ->
                    valueExtractor.apply(rec) > termValue;
            case GREATER_THAN_OR_EQUAL_TO -> rec ->
                    valueExtractor.apply(rec) >= termValue;
            case LESS_THAN -> rec ->
                    valueExtractor.apply(rec) < termValue;
            case LESS_THAN_OR_EQUAL_TO -> rec ->
                    valueExtractor.apply(rec) <= termValue;
            default -> throw new RuntimeException("Unexpected condition " + expressionTerm.getCondition());
        };
    }

    private Predicate<RefStoreEntry> buildDocRefFieldPredicate(final ExpressionTerm expressionTerm,
                                                               final Function<RefStoreEntry, DocRef> valueExtractor) {
        final DocRef termValue = DocRef.builder()
                .uuid(expressionTerm.getValue())
                .build();
        if (expressionTerm.getCondition().equals(Condition.IS_DOC_REF)) {
            return rec -> docRefsEqualOnUuid(valueExtractor.apply(rec), termValue);
        } else if (expressionTerm.getCondition().equals(Condition.EQUALS)) {
            final Predicate<String> namePredicate = PredicateUtil.createWildCardedFilterPredicate(
                    expressionTerm.getValue(), true, true);
            return rec -> {
                // docRef has no name at this point, so we need to find it
                final DocRef docRef = valueExtractor.apply(rec);
                if (docRef == null) {
                    return false;
                } else {
                    return docRefInfoService.name(docRef)
                            .map(namePredicate::test)
                            .orElse(false);
                }
            };
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

    private Val convertToVal(final Object object, final QueryField field) {
        return switch (field.getFldType()) {
            case TEXT -> ValString.create((String) object);
            case INTEGER -> ValInteger.create((Integer) object);
            case LONG, ID -> ValLong.create((long) object);
            case DATE -> ValDate.create((long) object);
            case DOC_REF -> getPipelineNameAsVal((DocRef) object);
            default -> throw new RuntimeException("Unexpected field type " + field.getFldType());
        };
    }

    private Val getPipelineNameAsVal(final DocRef docRef) {
        if (docRef == null) {
            return ValNull.INSTANCE;
        } else {
            String val = docRef.getUuid();
            if (docRefInfoService != null) {
                val = docRefInfoService.name(docRef).orElse(docRef.getUuid());
            }
            return ValString.create(val);
        }
    }
}
