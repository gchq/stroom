package stroom.index.impl;

import java.util.concurrent.Executor;

public interface IndexShardWriterExecutorProvider {
    Executor getAsyncExecutor();

    Executor getSyncExecutor();
}
