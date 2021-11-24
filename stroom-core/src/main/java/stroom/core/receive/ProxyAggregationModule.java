package stroom.core.receive;

import stroom.proxy.repo.ErrorReceiver;
import stroom.proxy.repo.ErrorReceiverImpl;
import stroom.proxy.repo.ProgressLog;
import stroom.proxy.repo.ProgressLogImpl;
import stroom.proxy.repo.RepoDbDirProvider;
import stroom.proxy.repo.RepoDbDirProviderImpl;
import stroom.proxy.repo.RepoDirProvider;
import stroom.proxy.repo.RepoDirProviderImpl;
import stroom.proxy.repo.Sender;
import stroom.proxy.repo.SenderImpl;

import com.google.inject.AbstractModule;

public class ProxyAggregationModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(RepoDirProvider.class).to(RepoDirProviderImpl.class);
        bind(RepoDbDirProvider.class).to(RepoDbDirProviderImpl.class);
        bind(ErrorReceiver.class).to(ErrorReceiverImpl.class);
        bind(Sender.class).to(SenderImpl.class);
        bind(ProgressLog.class).to(ProgressLogImpl.class);
    }
}
