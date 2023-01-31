package stroom.query.common.v2;

import stroom.util.time.StroomDuration;

class Lifespan {

    private final StroomDuration timeToIdle;
    private final StroomDuration timeToLive;
    private final boolean destroyOnTabClose;
    private final boolean destroyOnWindowClose;

    public Lifespan(final StroomDuration timeToIdle,
                    final StroomDuration timeToLive,
                    final boolean destroyOnTabClose,
                    final boolean destroyOnWindowClose) {
        this.timeToIdle = timeToIdle;
        this.timeToLive = timeToLive;
        this.destroyOnTabClose = destroyOnTabClose;
        this.destroyOnWindowClose = destroyOnWindowClose;
    }

    public StroomDuration getTimeToIdle() {
        return timeToIdle;
    }

    public StroomDuration getTimeToLive() {
        return timeToLive;
    }

    public boolean isDestroyOnTabClose() {
        return destroyOnTabClose;
    }

    public boolean isDestroyOnWindowClose() {
        return destroyOnWindowClose;
    }
}
