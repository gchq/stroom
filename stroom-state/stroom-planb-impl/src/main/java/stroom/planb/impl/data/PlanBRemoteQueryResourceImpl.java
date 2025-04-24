package stroom.planb.impl.data;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.planb.impl.db.PlanBValue;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
public class PlanBRemoteQueryResourceImpl implements PlanBRemoteQueryResource {

    private final Provider<PlanBQueryService> planBQueryServiceProvider;

    @Inject
    public PlanBRemoteQueryResourceImpl(final Provider<PlanBQueryService> planBQueryServiceProvider) {
        this.planBQueryServiceProvider = planBQueryServiceProvider;
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public PlanBValue getValue(final GetRequest request) {
        return planBQueryServiceProvider.get().getPlanBValue(request);
    }
}
