package stroom.task.mock;

import stroom.task.api.SimpleTaskContext;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TaskManager;
import stroom.task.shared.TaskId;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class MockTaskModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(TaskContextFactory.class).to(SimpleTaskContextFactory.class);
        bind(TaskContext.class).to(SimpleTaskContext.class);
    }

    @Provides
    TaskManager getTaskManager() {
        return new TaskManager() {
            @Override
            public void startup() {

            }

            @Override
            public void shutdown() {

            }

            @Override
            public boolean isTerminated(final TaskId taskId) {
                return false;
            }

            @Override
            public void terminate(final TaskId taskId) {

            }
        };
    }
}
