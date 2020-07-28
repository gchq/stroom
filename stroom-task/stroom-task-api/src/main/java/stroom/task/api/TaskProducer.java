/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.task.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public abstract class TaskProducer implements Comparable<TaskProducer> {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskProducer.class);

    private final long now = System.currentTimeMillis();

    private final AtomicInteger threadsUsed = new AtomicInteger();

    private final TaskExecutor taskExecutor;
    private final int maxThreadsPerTask;
    private final TaskContextFactory taskContextFactory;
    private final TaskContext parentContext;
    private final String taskName;

    private final AtomicBoolean finishedAddingTasks = new AtomicBoolean();
    private final AtomicInteger tasksTotal = new AtomicInteger();
    private final AtomicInteger tasksCompleted = new AtomicInteger();

    public TaskProducer(final TaskExecutor taskExecutor,
                        final int maxThreadsPerTask,
                        final TaskContextFactory taskContextFactory,
                        final TaskContext parentContext,
                        final String taskName) {
        this.taskExecutor = taskExecutor;
        this.maxThreadsPerTask = maxThreadsPerTask;
        this.taskContextFactory = taskContextFactory;
        this.parentContext = parentContext;
        this.taskName = taskName;
    }

    /**
     * Get the next task to execute or null if the producer has reached a concurrent execution limit or no further tasks
     * are available.
     *
     * @return The next task to execute or null if no tasks are available at this time.
     */
    //todo replace final modifier on method
     protected Runnable next() {
        Runnable runnable = null;

        final int count = threadsUsed.incrementAndGet();
        if (count > maxThreadsPerTask) {
            threadsUsed.decrementAndGet();
        } else {
            final Consumer<TaskContext> task = getNext();
            if (task == null) {
                threadsUsed.decrementAndGet();

                // Auto detach if we are complete.
                if (isComplete()) {
                    detach();
                }
            } else {
                final Consumer<TaskContext> consumer = tc -> {
                    try {
                        LOGGER.trace("Producing a task of class " + task.getClass().getName());
                        task.accept(tc);
                        LOGGER.trace("Task produced");
                    } catch (final Throwable e) {
                        LOGGER.debug(e.getMessage(), e);
                    } finally {
                        threadsUsed.decrementAndGet();
                        incrementTasksCompleted();
                        LOGGER.trace("Now completed " + getTasksCompleted() + " with " + getRemainingTasks() + " remaining.");
                    }
                };

                // Wrap the runnable so that we get task info and execute with the right permissions etc.
                runnable = taskContextFactory.context(parentContext, taskName, consumer);
            }
        }

        if (runnable != null){
            LOGGER.trace("Returning a runnable of class " + runnable.getClass().getName());
        }

        return runnable;
    }

    protected abstract Consumer<TaskContext> getNext();

    /**
     * Test if this task producer will not issue any further tasks and that all of the tasks it has issued have completed processing.
     *
     * @return True if this producer will not issue any further tasks and that all of the tasks it has issued have completed processing.
     */
    protected boolean isComplete() {
        return finishedAddingTasks.get() && getRemainingTasks() == 0;
    }

    protected void attach() {
        taskExecutor.addProducer(this);
    }

    private void detach() {
        taskExecutor.removeProducer(this);
    }

    protected void signalAvailable() {
        taskExecutor.signalAll();
    }

    protected void finishedAddingTasks() {
        this.finishedAddingTasks.set(true);
    }

    protected final int getTasksTotal() {
        return tasksTotal.get();
    }

    protected final void setTasksTotal(int tasksTotal) {
        this.tasksTotal.set(tasksTotal);
    }

    protected void incrementTasksTotal() {
        tasksTotal.incrementAndGet();
    }

    final int getTasksCompleted() {
        return tasksCompleted.get();
    }

    protected void incrementTasksCompleted() {
        tasksCompleted.incrementAndGet();
    }

    public final int getRemainingTasks() {
        return tasksTotal.get() - tasksCompleted.get();
    }

    @Override
    public int compareTo(final TaskProducer o) {
        return Long.compare(now, o.now);
    }
}
