package stroom.lifecycle.api;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

public class LifecycleBinder {

    private static final int DEFAULT_PRIORITY = 0;

    private MapBinder<StartupTask, Runnable> startupTaskMapBinder;
    private MapBinder<ShutdownTask, Runnable> shutdownTaskMapBinder;

    private LifecycleBinder(final Binder binder) {
        startupTaskMapBinder = MapBinder.newMapBinder(binder, StartupTask.class, Runnable.class);
        shutdownTaskMapBinder = MapBinder.newMapBinder(binder, ShutdownTask.class, Runnable.class);
    }

    public static LifecycleBinder create(final Binder binder) {
        return new LifecycleBinder(binder);
    }

    /**
     * Bind the startup task with the default priority
     */
    public <T extends Runnable> LifecycleBinder bindStartupTaskTo(final Class<T> runnableClass) {
        return bindStartupTaskTo(runnableClass, DEFAULT_PRIORITY);
    }

    /**
     * Bind the startup task with the supplied priority
     * @param priority Higher value will start earlier
     */
    public <T extends Runnable> LifecycleBinder bindStartupTaskTo(final Class<T> runnableClass,
                                                                  final int priority) {
        startupTaskMapBinder.addBinding(new StartupTask(priority))
                .to(runnableClass);
        return this;
    }

    /**
     * Bind the shutdown task with the default priority
     */
    public <T extends Runnable> LifecycleBinder bindShutdownTaskTo(final Class<T> runnableClass) {
        return bindShutdownTaskTo(runnableClass, DEFAULT_PRIORITY);
    }

    /**
     * Bind the shutdown task with the supplied priority
     * @param priority Higher value will shutdown later
     */
    public <T extends Runnable> LifecycleBinder bindShutdownTaskTo(final Class<T> runnableClass,
                                                                   final int priority) {
        shutdownTaskMapBinder.addBinding(new ShutdownTask(priority))
                .to(runnableClass);
        return this;
    }
}
