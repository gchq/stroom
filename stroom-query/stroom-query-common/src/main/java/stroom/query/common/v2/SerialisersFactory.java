package stroom.query.common.v2;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class SerialisersFactory {

    private final Provider<ResultStoreConfig> resultStoreConfigProvider;

    @Inject
    public SerialisersFactory(final Provider<ResultStoreConfig> resultStoreConfigProvider) {
        this.resultStoreConfigProvider = resultStoreConfigProvider;
    }

    public SerialisersFactory() {
        this.resultStoreConfigProvider = ResultStoreConfig::new;
    }

    public Serialisers create(final ErrorConsumer errorConsumer) {
        return new Serialisers(
                new InputFactory(),
                new OutputFactory(resultStoreConfigProvider.get(), errorConsumer));
    }
}
