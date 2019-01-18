package stroom.util.lifecycle;

public interface LifecycleAware {
    /**
     * Starts the object. Called <i>before</i> the application becomes available.
     */
    default void start() {
    }

    /**
     * Stops the object. Called <i>after</i> the application is no longer accepting requests.
     */
    default void stop() {
    }

    default int priority() {
        return 0;
    }
}
