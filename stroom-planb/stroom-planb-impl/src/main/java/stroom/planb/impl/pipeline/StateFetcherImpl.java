package stroom.planb.impl.pipeline;

import stroom.query.language.functions.StateFetcher;
import stroom.query.language.functions.StateProvider;
import stroom.query.language.functions.Val;

import jakarta.inject.Inject;

public class StateFetcherImpl implements StateFetcher {

    private final StateProvider provider;

    @Inject
    public StateFetcherImpl(final StateProvider provider) {
        // A single provider, deliberately. This used to iterate a Set<StateProvider> returning the first non
        // null value, but a ValErr counted as a value, so one provider's failure could mask another provider's
        // data, see gh-5692. With a plain binding a second provider is a duplicate binding error at startup
        // rather than a silent precedence problem.
        this.provider = provider;
    }

    @Override
    public Val getState(final String map, final String key, final long effectiveTimeMs) {
        // The provider contract is that this is never null, see StateProvider.getState.
        return provider.getState(map, key, effectiveTimeMs);
    }
}
