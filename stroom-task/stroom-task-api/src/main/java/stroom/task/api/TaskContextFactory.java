package stroom.task.api;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public interface TaskContextFactory {

    /**
     * Creates a new root task context that will appear at the top level of the server tasks tree.
     * <p>
     * This method returns a runnable object that is expected to be given to am executor of some sort for asynchronous
     * execution. In some cases we want to perform a task within the current thread and make it visible in the server
     * tasks pane (e.g. when the UI threads perform an action), in these cases it is necessary to just call `run()`
     * directly in the returned `Runnable`.
     *
     * @param taskName The name of the task to be displayed in the server tasks pane.
     * @param consumer The inner code to run within the task context.
     * @return A `Runnable` object to be passed to an executor or to `run()` directly.
     */
    Runnable context(String taskName,
                     Consumer<TaskContext> consumer);

    /**
     * Creates a new child task context that will appear nested within the provided parent context in the server tasks
     * tree.
     * <p>
     * This method returns a runnable object that is expected to be given to am executor of some sort for asynchronous
     * execution. In some cases we want to perform a task within the current thread and make it visible in the server
     * tasks pane (e.g. when the UI threads perform an action), in these cases it is necessary to just call `run()`
     * directly in the returned `Runnable`.
     * <p>
     * Note that this method should not be used unless the parent task thread is going to wait for the child task to
     * complete. If the parent task does not wait then the server tasks pane will show a dead parent for the child task.
     *
     * @param taskName The name of the task to be displayed in the server tasks pane.
     * @param consumer The inner code to run within the task context.
     * @return A `Runnable` object to be passed to an executor or to `run()` directly.
     */
    Runnable childContext(TaskContext parentContext,
                          String taskName,
                          Consumer<TaskContext> consumer);

    /**
     * Creates a new root task context that will appear at the top level of the server tasks tree.
     * <p>
     * This method returns a supplier object that is expected to be given to am executor of some sort for asynchronous
     * execution to provide an asynchronous result. In some cases we want to perform a task within the current thread
     * and make it visible in the server tasks pane (e.g. when the UI threads perform an action), in these cases it is
     * necessary to just call `get()` directly in the returned `Supplier`.
     *
     * @param taskName The name of the task to be displayed in the server tasks pane.
     * @param function The inner code to run within the task context that will return a value.
     * @return A `Supplier` object to be passed to an executor or to call `get()` directly.
     */
    <R> Supplier<R> contextResult(String taskName,
                                  Function<TaskContext, R> function);

    /**
     * Creates a new child task context that will appear nested within the provided parent context in the server tasks
     * tree.
     * <p>
     * This method returns a supplier object that is expected to be given to am executor of some sort for asynchronous
     * execution to provide an asynchronous result. In some cases we want to perform a task within the current thread
     * and make it visible in the server tasks pane (e.g. when the UI threads perform an action), in these cases it is
     * necessary to just call `get()` directly in the returned `Supplier`.
     * <p>
     * Note that this method should not be used unless the parent task thread is going to wait for the child task to
     * complete. If the parent task does not wait then the server tasks pane will show a dead parent for the child task.
     *
     * @param taskName The name of the task to be displayed in the server tasks pane.
     * @param function The inner code to run within the task context that will return a value.
     * @return A `Supplier` object to be passed to an executor or to call `get()` directly.
     */
    <R> Supplier<R> childContextResult(TaskContext parentContext,
                                       String taskName,
                                       Function<TaskContext, R> function);


    /**
     * Creates a new root task context that will appear at the top level of the server tasks tree.
     * <p>
     * This method returns a runnable object that is expected to be given to am executor of some sort for asynchronous
     * execution. In some cases we want to perform a task within the current thread and make it visible in the server
     * tasks pane (e.g. when the UI threads perform an action), in these cases it is necessary to just call `run()`
     * directly in the returned `Runnable`.
     *
     * @param taskName The name of the task to be displayed in the server tasks pane.
     * @param consumer The inner code to run within the task context.
     * @return A `Runnable` object to be passed to an executor or to `run()` directly.
     */
    Runnable context(String taskName,
                     TerminateHandlerFactory terminateHandlerFactory,
                     Consumer<TaskContext> consumer);

    /**
     * Creates a new child task context that will appear nested within the provided parent context in the server tasks
     * tree.
     * <p>
     * This method returns a runnable object that is expected to be given to am executor of some sort for asynchronous
     * execution. In some cases we want to perform a task within the current thread and make it visible in the server
     * tasks pane (e.g. when the UI threads perform an action), in these cases it is necessary to just call `run()`
     * directly in the returned `Runnable`.
     * <p>
     * Note that this method should not be used unless the parent task thread is going to wait for the child task to
     * complete. If the parent task does not wait then the server tasks pane will show a dead parent for the child task.
     *
     * @param taskName The name of the task to be displayed in the server tasks pane.
     * @param consumer The inner code to run within the task context.
     * @return A `Runnable` object to be passed to an executor or to `run()` directly.
     */
    Runnable childContext(TaskContext parentContext,
                          String taskName,
                          TerminateHandlerFactory terminateHandlerFactory,
                          Consumer<TaskContext> consumer);

    /**
     * Creates a new root task context that will appear at the top level of the server tasks tree.
     * <p>
     * This method returns a supplier object that is expected to be given to am executor of some sort for asynchronous
     * execution to provide an asynchronous result. In some cases we want to perform a task within the current thread
     * and make it visible in the server tasks pane (e.g. when the UI threads perform an action), in these cases it is
     * necessary to just call `get()` directly in the returned `Supplier`.
     *
     * @param taskName The name of the task to be displayed in the server tasks pane.
     * @param consumer The inner code to run within the task context that will return a value.
     * @return A `Supplier` object to be passed to an executor or to call `get()` directly.
     */
    <R> Supplier<R> contextResult(String taskName,
                                  TerminateHandlerFactory terminateHandlerFactory,
                                  Function<TaskContext, R> function);

    /**
     * Creates a new child task context that will appear nested within the provided parent context in the server tasks
     * tree.
     * <p>
     * This method returns a supplier object that is expected to be given to am executor of some sort for asynchronous
     * execution to provide an asynchronous result. In some cases we want to perform a task within the current thread
     * and make it visible in the server tasks pane (e.g. when the UI threads perform an action), in these cases it is
     * necessary to just call `get()` directly in the returned `Supplier`.
     * <p>
     * Note that this method should not be used unless the parent task thread is going to wait for the child task to
     * complete. If the parent task does not wait then the server tasks pane will show a dead parent for the child task.
     *
     * @param taskName The name of the task to be displayed in the server tasks pane.
     * @param consumer The inner code to run within the task context that will return a value.
     * @return A `Supplier` object to be passed to an executor or to call `get()` directly.
     */
    <R> Supplier<R> childContextResult(TaskContext parentContext,
                                       String taskName,
                                       TerminateHandlerFactory terminateHandlerFactory,
                                       Function<TaskContext, R> function);

    /**
     * Get the current task context if there is one or at least provide an empty one.
     *
     * @return The current task context.
     */
    TaskContext current();
}
