package stroom.query.api.v2;

public class ResultStoreSettingsFactory {

    private static final long ONE_DAY = 24 * 60 * 60 * 1000;

    private static final Lifespan DEFAULT_LIFESPAN = new Lifespan(
            ONE_DAY,
            ONE_DAY,
            true,
            true);

    public ResultStoreSettings get() {
        return new ResultStoreSettings(DEFAULT_LIFESPAN, DEFAULT_LIFESPAN);
    }
}
