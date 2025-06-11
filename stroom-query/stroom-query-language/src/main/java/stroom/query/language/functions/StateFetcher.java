package stroom.query.language.functions;

public interface StateFetcher {

    Val getState(String map, String key, long effectiveTimeMs);
}
