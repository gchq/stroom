package stroom.util;

import io.dropwizard.servlets.tasks.Task;

import java.util.List;

/**
 * Implement this if your class provides Dropwizard Admin tasks.
 */
public interface HasAdminTasks {

    /**
     * Called ONCE on boot after the guice bindings have been done.
     *
     * @return A list of admin tasks that can be executed on the admin port.
     * e.g.
     * <p>
     * <pre>{@code localhost:8091/proxyAdmin/tasks/clear-all-caches}</pre>
     * </p>
     */
    List<Task> getTasks();
}
