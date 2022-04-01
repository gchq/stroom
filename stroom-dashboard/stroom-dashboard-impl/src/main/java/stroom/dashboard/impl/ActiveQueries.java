package stroom.dashboard.impl;

import stroom.query.api.v2.QueryKey;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ActiveQueries {

    private final Map<QueryKey, ActiveQuery> activeQueries = new ConcurrentHashMap<>();

    public void destroy() {
        activeQueries.forEach((queryKey, activeQuery) -> activeQuery.destroy());
    }

    public void keepAlive() {
        activeQueries.forEach((queryKey, activeQuery) -> activeQuery.keepAlive());
    }

    public Optional<ActiveQuery> getActiveQuery(final QueryKey queryKey) {
        return Optional.ofNullable(activeQueries.get(queryKey));
    }

    public void addActiveQuery(final QueryKey queryKey, final ActiveQuery activeQuery) {
        activeQueries.put(queryKey, activeQuery);
    }

    public Optional<ActiveQuery> destroyActiveQuery(final QueryKey queryKey) {
        final ActiveQuery activeQuery = activeQueries.remove(queryKey);
        if (activeQuery != null) {
            activeQuery.destroy();
        }
        return Optional.ofNullable(activeQuery);
    }
}
