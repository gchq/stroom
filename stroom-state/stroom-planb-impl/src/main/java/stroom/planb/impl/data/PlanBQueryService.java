package stroom.planb.impl.data;

import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.pipeline.refdata.store.StringValue;
import stroom.planb.impl.PlanBConfig;
import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.db.PlanBValue;
import stroom.planb.impl.db.RangedState;
import stroom.planb.impl.db.RangedStateDb;
import stroom.planb.impl.db.RangedStateRequest;
import stroom.planb.impl.db.Session;
import stroom.planb.impl.db.SessionDb;
import stroom.planb.impl.db.SessionRequest;
import stroom.planb.impl.db.State;
import stroom.planb.impl.db.StateDb;
import stroom.planb.impl.db.StateRequest;
import stroom.planb.impl.db.StateValue;
import stroom.planb.impl.db.TemporalRangedState;
import stroom.planb.impl.db.TemporalRangedStateDb;
import stroom.planb.impl.db.TemporalRangedStateRequest;
import stroom.planb.impl.db.TemporalState;
import stroom.planb.impl.db.TemporalState.Key;
import stroom.planb.impl.db.TemporalStateDb;
import stroom.planb.impl.db.TemporalStateRequest;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.SnapshotSettings;
import stroom.planb.shared.StateType;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

@Singleton
public class PlanBQueryService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PlanBQueryService.class);

    private final PlanBDocCache planBDocCache;
    private final Provider<PlanBConfig> configProvider;
    private final ShardManager shardManager;
    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<NodeInfo> nodeInfoProvider;
    private final Provider<WebTargetFactory> webTargetFactoryProvider;

    @Inject
    public PlanBQueryService(final PlanBDocCache planBDocCache,
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

    public TemporalState lookup(final GetRequest request) {
        final PlanBDoc doc = planBDocCache.get(request.getMapName());
        if (doc == null) {
            LOGGER.warn(() -> "No Plan B doc found for '" + request.getMapName() + "'");
            throw new RuntimeException("No Plan B doc found for '" + request.getMapName() + "'");
        }
        final SnapshotSettings snapshotSettings = NullSafe.getOrElseGet(
                doc,
                PlanBDoc::getSettings,
                AbstractPlanBSettings::getSnapshotSettings,
                SnapshotSettings::new);
        final boolean local = snapshotSettings.isUseSnapshotsForLookup() || !shardManager.isSnapshotNode();
        final PlanBValue value = getPlanBValue(request, local);
        return convertToTemporalState(request.getKeyName(), value);
    }

    public Val getVal(final GetRequest request) {
        final PlanBDoc doc = planBDocCache.get(request.getMapName());
        if (doc == null) {
            LOGGER.warn(() -> "No Plan B doc found for '" + request.getMapName() + "'");
            throw new RuntimeException("No Plan B doc found for '" + request.getMapName() + "'");
        }
        final SnapshotSettings snapshotSettings = NullSafe.getOrElseGet(
                doc,
                PlanBDoc::getSettings,
                AbstractPlanBSettings::getSnapshotSettings,
                SnapshotSettings::new);
        final boolean local = snapshotSettings.isUseSnapshotsForGet() || !shardManager.isSnapshotNode();
        final PlanBValue value = getPlanBValue(request, local);
        return convertToVal(value, () -> StateType.SESSION.equals(doc.getStateType())
                ? ValBoolean.create(false)
                : ValNull.INSTANCE);
    }

    public PlanBValue getPlanBValue(final GetRequest request) {
        final PlanBDoc doc = planBDocCache.get(request.getMapName());
        if (doc == null) {
            LOGGER.warn(() -> "No PlanB doc found for '" + request.getMapName() + "'");
            throw new RuntimeException("No PlanB doc found for '" + request.getMapName() + "'");
        }
        return getLocalValue(request.getMapName(), request.getKeyName(), request.getEventTime());
    }

    private PlanBValue getPlanBValue(final GetRequest request,
                                    final boolean local) {
        if (local) {
            // If we are allowing snapshots or if this node stores the data then query locally.
            return getLocalValue(request.getMapName(), request.getKeyName(), request.getEventTime());

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
                    PlanBRemoteQueryResource.BASE_PATH, PlanBRemoteQueryResource.GET_VALUE_PATH);
            try {
                // A different node to make a rest call to the required node
                WebTarget webTarget = webTargetFactoryProvider.get().create(url);
                final Response response = webTarget
                        .request(MediaType.APPLICATION_JSON)
                        .post(Entity.json(request));
                if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                    throw new NotFoundException(response);
                } else if (response.getStatus() != Status.OK.getStatusCode()) {
                    throw new WebApplicationException(response);
                }

                return response.readEntity(PlanBValue.class);
            } catch (final Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }
    }

    public PlanBValue getLocalValue(final String mapName,
                                    final String keyName,
                                    final long eventTimeMs) {
        return shardManager.get(mapName, reader -> switch (reader) {
            case final StateDb db -> db.getState(new StateRequest(keyName.getBytes(StandardCharsets.UTF_8)));
            case final TemporalStateDb db ->
                    db.getState(new TemporalStateRequest(keyName.getBytes(StandardCharsets.UTF_8), eventTimeMs));
            case final RangedStateDb db -> db.getState(new RangedStateRequest(Long.parseLong(keyName)));
            case final TemporalRangedStateDb db ->
                    db.getState(new TemporalRangedStateRequest(Long.parseLong(keyName), eventTimeMs));
            case final SessionDb db ->
                    db.getState(new SessionRequest(keyName.getBytes(StandardCharsets.UTF_8), eventTimeMs));
            default -> throw new IllegalStateException("Unexpected value: " + reader);
        });
    }

    private TemporalState convertToTemporalState(final String keyName,
                                                 final PlanBValue planBValue) {
        return switch (planBValue) {
            case null -> null;
            case final State state -> new TemporalState(Key
                    .builder()
                    .name(state.key().getBytes())
                    .effectiveTime(0)
                    .build(), state.val());
            case final TemporalState temporalState -> temporalState;
            case final RangedState rangedState -> new TemporalState(Key
                    .builder()
                    .name(keyName)
                    .effectiveTime(0)
                    .build(),
                    rangedState.val());
            case final TemporalRangedState temporalRangedState -> new TemporalState(Key
                    .builder()
                    .name(keyName)
                    .effectiveTime(0)
                    .build(),
                    temporalRangedState.val());
            case final Session session -> new TemporalState(Key
                    .builder()
                    .name(keyName)
                    .effectiveTime(0)
                    .build(),
                    StateValue
                            .builder()
                            .typeId(StringValue.TYPE_ID)
                            .byteBuffer(ByteBuffer.wrap(session.getKey()))
                            .build());
            default -> throw new IllegalStateException("Unexpected value: " + planBValue);
        };

    }

    private Val convertToVal(final PlanBValue planBValue,
                             final Supplier<Val> otherwise) {
        return switch (planBValue) {
            case null -> otherwise.get();
            case final State state -> ValString.create(state.val().toString());
            case final TemporalState temporalState -> ValString.create(temporalState.val().toString());
            case final RangedState rangedState -> ValString.create(rangedState.val().toString());
            case final TemporalRangedState temporalRangedState ->
                    ValString.create(temporalRangedState.val().toString());
            case final Session ignored -> ValBoolean.create(true);
            default -> throw new IllegalStateException("Unexpected value: " + planBValue);
        };
    }
}
