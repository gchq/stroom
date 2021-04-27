package stroom.core.receive;

import stroom.proxy.repo.ErrorReceiver;
import stroom.proxy.repo.ErrorReceiverImpl;
import stroom.proxy.repo.RepoDbDirProvider;
import stroom.proxy.repo.RepoDbDirProviderImpl;
import stroom.proxy.repo.RepoDirProvider;
import stroom.proxy.repo.RepoDirProviderImpl;

import com.google.inject.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyAggregationModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyAggregationModule.class);

    @Override
    protected void configure() {
        super.configure();

        bind(RepoDirProvider.class).to(RepoDirProviderImpl.class);
        bind(RepoDbDirProvider.class).to(RepoDbDirProviderImpl.class);
        bind(ErrorReceiver.class).to(ErrorReceiverImpl.class);
    }
}
