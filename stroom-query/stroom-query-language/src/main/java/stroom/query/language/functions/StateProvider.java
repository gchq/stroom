package stroom.query.language.functions;

public interface StateProvider {

    Val getState(String map, String key, long effectiveTimeMs);
}
