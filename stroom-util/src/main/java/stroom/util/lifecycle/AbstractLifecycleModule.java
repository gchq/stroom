package stroom.util.lifecycle;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;

import java.util.Objects;

public abstract class AbstractLifecycleModule extends AbstractModule {
    private MapBinder<StartupTask, Runnable> startupTaskMapBinder;
    private MapBinder<ShutdownTask, Runnable> shutdownTaskMapBinder;

    @Override
    protected void configure() {
        super.configure();
        startupTaskMapBinder = MapBinder.newMapBinder(binder(), StartupTask.class, Runnable.class);
        shutdownTaskMapBinder = MapBinder.newMapBinder(binder(), ShutdownTask.class, Runnable.class);
    }

    public StartupBuilder bindStartup() {
        Objects.requireNonNull(startupTaskMapBinder, "super.configure() has not been called");
        return new StartupBuilder(startupTaskMapBinder);
    }

    public ShutdownBuilder bindShutdown() {
        Objects.requireNonNull(shutdownTaskMapBinder, "super.configure() has not been called");
        return new ShutdownBuilder(shutdownTaskMapBinder);
    }

    public static final class StartupBuilder {
        private final MapBinder<StartupTask, Runnable> mapBinder;

        private int priority;

        StartupBuilder(final MapBinder<StartupTask, Runnable> mapBinder) {
            this.mapBinder = mapBinder;
        }

        public StartupBuilder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public <T extends Runnable> void to(final Class<T> runnableClass) {
            final StartupTask startupTask = new StartupTask(priority);
            mapBinder.addBinding(startupTask).to(runnableClass);
        }
    }

    public static final class ShutdownBuilder {
        private final MapBinder<ShutdownTask, Runnable> mapBinder;

        private int priority;

        ShutdownBuilder(final MapBinder<ShutdownTask, Runnable> mapBinder) {
            this.mapBinder = mapBinder;
        }

        public ShutdownBuilder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public <T extends Runnable> void to(final Class<T> runnableClass) {
            final ShutdownTask shutdownTask = new ShutdownTask(priority);
            mapBinder.addBinding(shutdownTask).to(runnableClass);
        }
    }
}
