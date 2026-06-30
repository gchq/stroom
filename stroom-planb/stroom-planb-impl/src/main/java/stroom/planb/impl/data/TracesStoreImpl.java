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

package stroom.planb.impl.data;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.TracesDoc;
import stroom.pathways.shared.TracesResultPage;
import stroom.pathways.shared.TracesStore;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.db.trace.TraceDb;
import stroom.planb.shared.PlanBDocument;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.task.api.ExecutorProvider;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResourcePaths;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Singleton
public class TracesStoreImpl implements TracesStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TracesStoreImpl.class);

    private final PlanBDocCache planBDocCache;
    private final Provider<PlanBConfig> configProvider;
    private final ShardManager shardManager;
    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<NodeInfo> nodeInfoProvider;
    private final Provider<WebTargetFactory> webTargetFactoryProvider;
    private final Provider<Map<DocumentTypeName, DocumentActionHandler>> documentActionHandlersProvider;
    private final SecurityContext securityContext;
    private final Executor executor;

    @Inject
    public TracesStoreImpl(final PlanBDocCache planBDocCache,
                           final Provider<PlanBConfig> configProvider,
                           final ShardManager shardManager,
                           final Provider<NodeService> nodeServiceProvider,
                           final Provider<NodeInfo> nodeInfoProvider,
                           final Provider<WebTargetFactory> webTargetFactoryProvider,
                           final Provider<Map<DocumentTypeName, DocumentActionHandler>> documentActionHandlersProvider,
                           final SecurityContext securityContext,
                           final ExecutorProvider executorProvider) {
        this.planBDocCache = planBDocCache;
        this.configProvider = configProvider;
        this.shardManager = shardManager;
        this.nodeServiceProvider = nodeServiceProvider;
        this.nodeInfoProvider = nodeInfoProvider;
        this.webTargetFactoryProvider = webTargetFactoryProvider;
        this.documentActionHandlersProvider = documentActionHandlersProvider;
        this.securityContext = securityContext;
        this.executor = executorProvider.get();
    }

    @Override
    public TracesResultPage findTraces(final FindTraceCriteria criteria) {
        final DocRef docRef = criteria.getDataSourceRef();
        final PlanBDocument doc = getPlanBDoc(docRef);

        if (doc == null) {
            LOGGER.warn(() -> "No Plan B doc found for '" + docRef.getName() + "'");
            throw new RuntimeException("No Plan B doc found for '" + docRef.getName() + "'");
        }
        final boolean local = !shardManager.isSnapshotNode();
        return findTraces(criteria, local);
    }

    private TracesResultPage findTraces(final FindTraceCriteria criteria,
                                        final boolean local) {
        if (local) {
            // If we are allowing snapshots or if this node stores the data then query locally.
            return getLocalTraces(criteria);

        } else {
            // Otherwise perform a remote query.
            final List<String> nodes = NullSafe.list(configProvider.get().getNodeList());
            if (nodes.isEmpty()) {
                throw new RuntimeException("No Plan B storage nodes are configured");
            }

            final String nodeName = nodes.getFirst();
            final String url = NodeCallUtil
                                       .getBaseEndpointUrl(nodeInfoProvider.get(), nodeServiceProvider.get(), nodeName)
                               + ResourcePaths.buildAuthenticatedApiPath(
                    TracesRemoteQueryResource.BASE_PATH, TracesRemoteQueryResource.GET_TRACES_PATH);
            try {
                // A different node to make a rest call to the required node
                final WebTarget webTarget = webTargetFactoryProvider.get().create(url);
                final Response response = webTarget
                        .request(MediaType.APPLICATION_JSON)
                        .post(Entity.json(criteria));
                if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                    throw new NotFoundException(response);
                } else if (response.getStatus() != Status.OK.getStatusCode()) {
                    throw new WebApplicationException(response);
                }

                return response.readEntity(TracesResultPage.class);
            } catch (final Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }
    }

    public TracesResultPage getLocalTraces(final FindTraceCriteria criteria) {
        final DocRef docRef = criteria.getDataSourceRef();
        final PlanBDocument doc = getPlanBDoc(docRef);

        if (doc == null) {
            LOGGER.warn(() -> "No Plan B doc found for '" + docRef.getName() + "'");
            throw new RuntimeException("No Plan B doc found for '" + docRef.getName() + "'");
        }

        if (doc.getSharedPath() != null && doc.getShardCount() > 0) {
            final UserIdentity userIdentity = securityContext.getUserIdentity();
            final List<CompletableFuture<TracesResultPage>> futures = new ArrayList<>();

            final FindTraceCriteria shardCriteria = new FindTraceCriteria(
                    PageRequest.unlimited(),
                    criteria.getSortList(),
                    criteria.getDataSourceRef(),
                    criteria.getFilter(),
                    criteria.getPathway(),
                    criteria.getTemporalOrderingTolerance());

            for (int i = 0; i < doc.getShardCount(); i++) {
                final int shardIndex = i;
                futures.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        return securityContext.asUserResult(userIdentity, () ->
                                shardManager.get(doc.getName(), shardIndex, reader -> {
                                    if (reader instanceof final TraceDb traceDb) {
                                        return traceDb.findTraces(shardCriteria);
                                    }
                                    throw new IllegalStateException("Unexpected value: " + reader);
                                }));
                    } catch (final Exception e) {
                        LOGGER.error("Error querying shard " + shardIndex + " for doc " + doc.getName(), e);
                        return null;
                    }
                }, executor));
            }

            final List<TraceRoot> allTraceRoots = new ArrayList<>();
            int total = 0;
            boolean exact = true;

            // Wait for all shard queries to complete concurrently
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            for (final CompletableFuture<TracesResultPage> future : futures) {
                try {
                    final TracesResultPage page = future.get();
                    if (page != null) {
                        if (page.getValues() != null) {
                            allTraceRoots.addAll(page.getValues());
                        }
                        if (page.getPageResponse() != null) {
                            total += page.getPageResponse().getTotal();
                            if (!page.getPageResponse().isExact()) {
                                exact = false;
                            }
                        }
                    }
                } catch (final Exception e) {
                    LOGGER.error("Failed to retrieve query result page from future", e);
                }
            }

            // Sort lexicographically by traceId
            allTraceRoots.sort(Comparator.comparing(TraceRoot::getTraceId));

            // Apply pagination manually
            final int offset = criteria.getPageRequest() != null ? criteria.getPageRequest().getOffset() : 0;
            final int length = criteria.getPageRequest() != null
                    ? criteria.getPageRequest().getLength()
                    : Integer.MAX_VALUE;

            final List<TraceRoot> paginatedList;
            if (offset >= allTraceRoots.size()) {
                paginatedList = Collections.emptyList();
            } else {
                final int toIndex = Math.min(offset + length, allTraceRoots.size());
                paginatedList = allTraceRoots.subList(offset, toIndex);
            }

            final stroom.util.shared.PageResponse pageResponse = new stroom.util.shared.PageResponse(
                    (long) offset,
                    paginatedList.size(),
                    (long) total,
                    exact
            );
            return new TracesResultPage(paginatedList, pageResponse);
        } else {
            return shardManager.get(criteria.getDataSourceRef().getName(), reader -> {
                if (reader instanceof final TraceDb traceDb) {
                    return traceDb.findTraces(criteria);
                }
                throw new IllegalStateException("Unexpected value: " + reader);
            });
        }
    }

    @Override
    public Trace getTrace(final GetTraceRequest request) {
        final DocRef docRef = request.getDataSourceRef();
        final PlanBDocument doc = getPlanBDoc(docRef);

        if (doc == null) {
            LOGGER.warn(() -> "No Plan B doc found for '" + docRef.getName() + "'");
            throw new RuntimeException("No Plan B doc found for '" + docRef.getName() + "'");
        }
        final boolean local = !shardManager.isSnapshotNode();
        return findTrace(request, local);
    }

    private Trace findTrace(final GetTraceRequest request,
                            final boolean local) {
        if (local) {
            // If we are allowing snapshots or if this node stores the data then query locally.
            return getLocalTrace(request);

        } else {
            // Otherwise perform a remote query.
            final List<String> nodes = NullSafe.list(configProvider.get().getNodeList());
            if (nodes.isEmpty()) {
                throw new RuntimeException("No Plan B storage nodes are configured");
            }

            final String nodeName = nodes.getFirst();
            final String url = NodeCallUtil
                                       .getBaseEndpointUrl(nodeInfoProvider.get(), nodeServiceProvider.get(), nodeName)
                               + ResourcePaths.buildAuthenticatedApiPath(
                    TracesRemoteQueryResource.BASE_PATH, TracesRemoteQueryResource.GET_TRACE_PATH);
            try {
                // A different node to make a rest call to the required node
                final WebTarget webTarget = webTargetFactoryProvider.get().create(url);
                final Response response = webTarget
                        .request(MediaType.APPLICATION_JSON)
                        .post(Entity.json(request));
                if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                    throw new NotFoundException(response);
                } else if (response.getStatus() != Status.OK.getStatusCode()) {
                    throw new WebApplicationException(response);
                }

                return response.readEntity(Trace.class);
            } catch (final Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }
    }

    public Trace getLocalTrace(final GetTraceRequest request) {
        return shardManager.get(request.getDataSourceRef().getName(), request.getTraceId(), reader -> {
            if (reader instanceof final TraceDb traceDb) {
                return traceDb.getTrace(request);
            }
            throw new IllegalStateException("Unexpected value: " + reader);
        });
    }

    private PlanBDocument getPlanBDoc(final DocRef docRef) {
        if (docRef == null) {
            return null;
        }
        if (TracesDoc.TYPE.equals(docRef.getType())) {
            try {
                final DocumentActionHandler<?> handler = documentActionHandlersProvider.get()
                        .get(new DocumentTypeName(TracesDoc.TYPE));
                if (handler == null) {
                    throw new IllegalStateException("No handler found for type: " + TracesDoc.TYPE);
                }
                return (PlanBDocument) handler.readDocument(docRef);
            } catch (final Exception e) {
                LOGGER.error("Failed to read TracesDoc " + docRef, e);
                throw new RuntimeException("Failed to read TracesDoc '" + docRef.getName() + "'", e);
            }
        } else {
            return planBDocCache.get(docRef.getName());
        }
    }
}
