package stroom.query.language.functions;

import java.time.Instant;

public interface LookupProvider {

    Val lookup(String map, String key, Instant effectiveTimeMs);
}
