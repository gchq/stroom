package stroom.query.common.v2;

class ResolvedResultStoreSettings {

    private final Lifespan searchProcessLifespan;
    private final Lifespan storeLifespan;

    public ResolvedResultStoreSettings(final Lifespan searchProcessLifespan,
                                       final Lifespan storeLifespan) {
        this.searchProcessLifespan = searchProcessLifespan;
        this.storeLifespan = storeLifespan;
    }

    public Lifespan getSearchProcessLifespan() {
        return searchProcessLifespan;
    }

    public Lifespan getStoreLifespan() {
        return storeLifespan;
    }
}
