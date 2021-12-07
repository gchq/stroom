package stroom.index.impl;

import stroom.security.api.SecurityContext;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;

import java.util.concurrent.Executor;
import javax.inject.Inject;

public class IndexShardWriterExecutorProviderImpl implements IndexShardWriterExecutorProvider {

    private final Executor asyncExecutor;

    @Inject
    public IndexShardWriterExecutorProviderImpl(final ExecutorProvider executorProvider,
                                                final SecurityContext securityContext) {
        final ThreadPool threadPool = new ThreadPoolImpl("Index Shard Writer Cache", 3);
        asyncExecutor = securityContext.asProcessingUserResult(() -> executorProvider.get(threadPool));
    }

    @Override
    public Executor getAsyncExecutor() {
        return asyncExecutor;
    }

    @Override
    public Executor getSyncExecutor() {
        return Runnable::run;
    }
}
