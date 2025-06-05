package stroom.planb.impl.data;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
public class PlanBRemoteQueryResourceImpl implements PlanBRemoteQueryResource {

    private final Provider<PlanBQueryService> planBQueryServiceProvider;
    private final Provider<PlanBShardInfoServiceImpl> planBShardInfoServiceProvider;

    @Inject
    public PlanBRemoteQueryResourceImpl(final Provider<PlanBQueryService> planBQueryServiceProvider,
                                        final Provider<PlanBShardInfoServiceImpl> planBShardInfoServiceProvider) {
        this.planBQueryServiceProvider = planBQueryServiceProvider;
        this.planBShardInfoServiceProvider = planBShardInfoServiceProvider;
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public PlanBValue getValue(final GetRequest request) {
        return planBQueryServiceProvider.get().getPlanBValue(request);
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public PlanBShardInfoResponse getStoreInfo(final PlanBShardInfoRequest request) {
        return new PlanBShardInfoResponse(planBShardInfoServiceProvider.get().getStoreInfo(request.getFields()));
    }
}
