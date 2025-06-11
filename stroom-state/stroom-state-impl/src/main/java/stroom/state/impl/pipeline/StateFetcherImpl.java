package stroom.state.impl.pipeline;

import stroom.query.language.functions.StateFetcher;
import stroom.query.language.functions.StateProvider;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;

import jakarta.inject.Inject;

import java.util.Set;

public class StateFetcherImpl implements StateFetcher {

    private final Set<StateProvider> providers;

    @Inject
    public StateFetcherImpl(final Set<StateProvider> providers) {
        this.providers = providers;
    }

    @Override
    public Val getState(String map, String key, long effectiveTimeMs) {
        for (final StateProvider provider : providers) {
            final Val val = provider.getState(map, key, effectiveTimeMs);
            if (val != null && !Type.NULL.equals(val.type())) {
                return val;
            }
        }
        return ValNull.INSTANCE;
    }
}
