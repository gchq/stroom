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

package stroom.pathways.impl;

import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.GetSpansRequest;
import stroom.pathways.shared.GetTraceOverviewRequest;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.TraceHistogram;
import stroom.pathways.shared.TraceHistogramRequest;
import stroom.pathways.shared.TraceOverview;
import stroom.pathways.shared.TraceSpanPage;
import stroom.pathways.shared.TracesResultPage;
import stroom.pathways.shared.TracesStore;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.planb.impl.PlanBConfig;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.shared.NullSafe;
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

import java.util.List;

/**
 * Query implementation used on a snapshot node (a node not in {@code PlanBConfig.nodeList}) for a REST-legacy
 * store. It holds no data of its own, so each operation is proxied over REST to the first configured storage
 * node, whose {@link TracesRemoteQueryResource} runs it locally against the authoritative shard.
 */
@Singleton
class ProxyTracesStore implements TracesStore {

    private final Provider<PlanBConfig> configProvider;
    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<NodeInfo> nodeInfoProvider;
    private final Provider<WebTargetFactory> webTargetFactoryProvider;

    @Inject
    ProxyTracesStore(final Provider<PlanBConfig> configProvider,
                     final Provider<NodeService> nodeServiceProvider,
                     final Provider<NodeInfo> nodeInfoProvider,
                     final Provider<WebTargetFactory> webTargetFactoryProvider) {
        this.configProvider = configProvider;
        this.nodeServiceProvider = nodeServiceProvider;
        this.nodeInfoProvider = nodeInfoProvider;
        this.webTargetFactoryProvider = webTargetFactoryProvider;
    }

    @Override
    public TracesResultPage findTraces(final FindTraceCriteria criteria) {
        return queryStorageNode(TracesRemoteQueryResource.GET_TRACES_PATH, criteria, TracesResultPage.class);
    }

    @Override
    public Trace getTrace(final GetTraceRequest request) {
        return queryStorageNode(TracesRemoteQueryResource.GET_TRACE_PATH, request, Trace.class);
    }

    @Override
    public TraceSpanPage getSpans(final GetSpansRequest request) {
        return queryStorageNode(TracesRemoteQueryResource.GET_SPANS_PATH, request, TraceSpanPage.class);
    }

    @Override
    public TraceOverview getTraceOverview(final GetTraceOverviewRequest request) {
        return queryStorageNode(
                TracesRemoteQueryResource.GET_TRACE_OVERVIEW_PATH, request, TraceOverview.class);
    }

    @Override
    public TraceHistogram getTraceHistogram(final TraceHistogramRequest request) {
        return queryStorageNode(
                TracesRemoteQueryResource.GET_TRACE_HISTOGRAM_PATH, request, TraceHistogram.class);
    }

    // Proxies a query to the first configured Plan B storage node (this node holds no data of its own).
    private <T> T queryStorageNode(final String path,
                                   final Object request,
                                   final Class<T> responseType) {
        final List<String> nodes = NullSafe.list(configProvider.get().getNodeList());
        if (nodes.isEmpty()) {
            throw new RuntimeException("No Plan B storage nodes are configured");
        }
        final String nodeName = nodes.getFirst();
        final String url = NodeCallUtil
                .getBaseEndpointUrl(nodeInfoProvider.get(), nodeServiceProvider.get(), nodeName)
                + ResourcePaths.buildAuthenticatedApiPath(TracesRemoteQueryResource.BASE_PATH, path);
        try {
            final WebTarget webTarget = webTargetFactoryProvider.get().create(url);
            final Response response = webTarget
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(request));
            if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                throw new NotFoundException(response);
            } else if (response.getStatus() != Status.OK.getStatusCode()) {
                throw new WebApplicationException(response);
            }
            return response.readEntity(responseType);
        } catch (final Throwable e) {
            throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
        }
    }
}
