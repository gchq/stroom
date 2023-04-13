package stroom.query.common.v2;

import stroom.util.time.StroomDuration;

public class ResultStoreSettingsFactory {

    private static final Lifespan DEFAULT_LIFESPAN = new Lifespan(
            StroomDuration.parse("24h"),
            StroomDuration.parse("24h"),
            true,
            true);

    public ResultStoreSettings get() {
        return new ResultStoreSettings(DEFAULT_LIFESPAN, DEFAULT_LIFESPAN);
    }
}
