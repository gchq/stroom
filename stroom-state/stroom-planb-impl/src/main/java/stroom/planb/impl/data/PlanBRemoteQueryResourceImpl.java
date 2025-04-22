package stroom.planb.impl.data;

import stroom.planb.impl.PlanBDocCache;
import stroom.planb.impl.db.PlanBValue;
import stroom.planb.shared.PlanBDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class PlanBRemoteQueryResourceImpl implements PlanBRemoteQueryResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PlanBRemoteQueryResourceImpl.class);

    private final PlanBDocCache planBDocCache;
    private final PlanBQueryService planBQueryService;

    @Inject
    public PlanBRemoteQueryResourceImpl(final PlanBDocCache planBDocCache,
                                        final PlanBQueryService planBQueryService) {
        this.planBDocCache = planBDocCache;
        this.planBQueryService = planBQueryService;
    }

    @Override
    public PlanBValue getValue(final GetRequest request) {
        final PlanBDoc doc = planBDocCache.get(request.getMapName());
        if (doc == null) {
            LOGGER.warn(() -> "No PlanB doc found for '" + request.getMapName() + "'");
            throw new RuntimeException("No PlanB doc found for '" + request.getMapName() + "'");
        }
        return planBQueryService.getPlanBValue(request, true);
    }
}
