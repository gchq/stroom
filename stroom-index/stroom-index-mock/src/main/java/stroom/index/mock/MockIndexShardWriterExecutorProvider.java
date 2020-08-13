package stroom.index.mock;

import stroom.index.impl.IndexShardWriterExecutorProvider;

import java.util.concurrent.Executor;

public class MockIndexShardWriterExecutorProvider implements IndexShardWriterExecutorProvider {
    @Override
    public Executor getAsyncExecutor() {
        return Runnable::run;
    }

    @Override
    public Executor getSyncExecutor() {
        return Runnable::run;
    }
}