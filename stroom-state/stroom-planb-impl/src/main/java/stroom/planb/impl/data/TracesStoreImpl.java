package stroom.planb.impl.data;

import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.TracesResultPage;
import stroom.pathways.shared.TracesStore;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.db.trace.TraceDb;
import stroom.planb.shared.PlanBDoc;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
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

@Singleton
public class TracesStoreImpl implements TracesStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TracesStoreImpl.class);

    private final PlanBDocCache planBDocCache;
    private final Provider<PlanBConfig> configProvider;
    private final ShardManager shardManager;
    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<NodeInfo> nodeInfoProvider;
    private final Provider<WebTargetFactory> webTargetFactoryProvider;

    @Inject
    public TracesStoreImpl(final PlanBDocCache planBDocCache,
                           final Provider<PlanBConfig> configProvider,
                           final ShardManager shardManager,
                           final Provider<NodeService> nodeServiceProvider,
                           final Provider<NodeInfo> nodeInfoProvider,
                           final Provider<WebTargetFactory> webTargetFactoryProvider) {
        this.planBDocCache = planBDocCache;
        this.configProvider = configProvider;
        this.shardManager = shardManager;
        this.nodeServiceProvider = nodeServiceProvider;
        this.nodeInfoProvider = nodeInfoProvider;
        this.webTargetFactoryProvider = webTargetFactoryProvider;
    }

    @Override
    public TracesResultPage findTraces(final FindTraceCriteria criteria) {
        final String name = criteria.getDataSourceRef().getName();
        final PlanBDoc doc = planBDocCache.get(name);
        if (doc == null) {
            LOGGER.warn(() -> "No Plan B doc found for '" + name + "'");
            throw new RuntimeException("No Plan B doc found for '" + name + "'");
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
        return shardManager.get(criteria.getDataSourceRef().getName(), reader -> {
            if (reader instanceof final TraceDb traceDb) {
                return traceDb.findTraces(criteria);
            }
            throw new IllegalStateException("Unexpected value: " + reader);
        });
    }

    @Override
    public Trace getTrace(final GetTraceRequest request) {
        final String name = request.getDataSourceRef().getName();
        final PlanBDoc doc = planBDocCache.get(name);
        if (doc == null) {
            LOGGER.warn(() -> "No Plan B doc found for '" + name + "'");
            throw new RuntimeException("No Plan B doc found for '" + name + "'");
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
        return shardManager.get(request.getDataSourceRef().getName(), reader -> {
            if (reader instanceof final TraceDb traceDb) {
                return traceDb.getTrace(request);
            }
            throw new IllegalStateException("Unexpected value: " + reader);
        });
    }
}
