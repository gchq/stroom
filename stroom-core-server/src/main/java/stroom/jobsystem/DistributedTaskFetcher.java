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

package stroom.jobsystem;

import com.caucho.hessian.client.HessianRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.jobsystem.JobNodeTrackerCache.Trackers;
import stroom.jobsystem.shared.Job;
import stroom.jobsystem.shared.JobNode;
import stroom.jobsystem.shared.JobNode.JobType;
import stroom.node.NodeCache;
import stroom.node.shared.Node;
import stroom.streamtask.TaskStatusTraceLog;
import stroom.task.GenericServerTask;
import stroom.task.TaskCallbackAdaptor;
import stroom.task.TaskManager;
import stroom.task.cluster.ClusterCallEntry;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.cluster.DefaultClusterResultCollector;
import stroom.task.cluster.TargetNodeSetFactory.TargetType;
import stroom.util.shared.Task;
import stroom.util.shared.VoidResult;
import stroom.guice.StroomBeanStore;
import stroom.util.spring.StroomFrequencySchedule;
import stroom.util.spring.StroomShutdown;
import stroom.util.thread.ThreadUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class executes all tasks that are currently queued for execution. This
 * class may execute many tasks concurrently if required, e.g. using separate
 * threads for transforming multiple XML files.
 */
@Singleton
public class DistributedTaskFetcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(DistributedTaskFetcher.class);
    private static final long ONE_MINUTE = 60 * 1000;
    // Wait time for master to return tasks (5 minutes)
    private static final long WAIT_TIME = 5;
    private final TaskStatusTraceLog taskStatusTraceLog = new TaskStatusTraceLog();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final AtomicBoolean fetchingTasks = new AtomicBoolean();
    private final AtomicBoolean waitingToFetchTasks = new AtomicBoolean();
    private final Set<Task<?>> runningTasks = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private final StroomBeanStore beanStore;
    private final TaskManager taskManager;
    private final JobNodeTrackerCache jobNodeTrackerCache;
    private final NodeCache nodeCache;

    private long lastFetch;

    @Inject
    DistributedTaskFetcher(final StroomBeanStore beanStore,
                           final TaskManager taskManager,
                           final JobNodeTrackerCache jobNodeTrackerCache,
                           final NodeCache nodeCache) {
        this.beanStore = beanStore;
        this.taskManager = taskManager;
        this.jobNodeTrackerCache = jobNodeTrackerCache;
        this.nodeCache = nodeCache;
    }

    /**
     * Tells tasks to stop and waits for all tasks to stop before cleaning up
     * the executors.
     */
    @StroomShutdown(priority = 999)
    public void shutdown() {
        stopping.set(true);

        ThreadUtil.sleep(1000);

        // Wait until we have stopped.
        while (runningTasks.size() > 0) {
            for (final Task<?> task : runningTasks) {
                task.terminate();
            }

            ThreadUtil.sleep(1000);
        }
    }

    /**
     * Every 10 seconds the Stroom lifecycle service will try and fetch new tasks
     * for execution.
     */
    @StroomFrequencySchedule("10s")
    public void execute() {
        fetch();
    }

    /**
     * Tries to fetch tasks asynchronously if we aren't already fetching tasks.
     * If we are it will make sure we immediately try and fetch tasks again
     * after the previous fetch.
     */
    public void fetch() {
        try {
            if (!stopped.get()) {
                // Only allow one set of tasks to be fetched at any one time.
                if (fetchingTasks.compareAndSet(false, true)) {
                    if (!stopping.get()) {
                        final GenericServerTask genericServerTask = GenericServerTask.create("Fetch Tasks", "fetching tasks");
                        genericServerTask.setRunnable(() -> {
                            try {
                                LOGGER.trace("Trying to fetch tasks");

                                // We will force a fetch if it has been more than one minute since
                                // our last fetch. This allows the master node to know that the
                                // worker nodes are still alive and that it is still going to be
                                // required to distribute tasks. If it did not get a call every
                                // minute it might try and release cached tasks back to the database
                                // event though this doesn't happen in the current implementation.
                                final long now = System.currentTimeMillis();
                                final long elapsed = now - lastFetch;
                                final boolean forceFetch = elapsed > ONE_MINUTE;

                                // Get the trackers.
                                final Trackers trackers = jobNodeTrackerCache.getTrackers();

                                // Get this node.
                                final Node node = jobNodeTrackerCache.getNode();

                                // Create an array of runnable jobs sorted into priority order.
                                final DistributedRequiredTask[] requiredTasks = getDistributedRequiredTasks(trackers);

                                // Find out how many tasks we need in total.
                                int count = 0;
                                for (final DistributedRequiredTask requiredTask : requiredTasks) {
                                    count += requiredTask.getRequiredTaskCount();
                                }

                                // If there are some tasks we need to get then get them.
                                if (count > 0 || forceFetch) {
                                    final DistributedTaskRequestClusterTask request = new DistributedTaskRequestClusterTask(genericServerTask, "DistributedTaskRequestClusterTask", node,
                                            requiredTasks);

                                    if (LOGGER.isDebugEnabled()) {
                                        LOGGER.debug("Task request: node=\"" + request.getNode().getName() + "\"");
                                        if (LOGGER.isTraceEnabled()) {
                                            final String trace = "\nTask request: node=\"" + request.getNode().getName() + "\"\n"
                                                    + request.toString();
                                            LOGGER.trace(trace);
                                        }
                                    }

                                    // Remember the last fetch time.
                                    lastFetch = now;

                                    // Perform a fetch from the master node.
                                    final ClusterDispatchAsyncHelper dispatchHelper = beanStore.getBean(ClusterDispatchAsyncHelper.class);
                                    if (dispatchHelper.isClusterStateInitialised()) {
                                        final DefaultClusterResultCollector<DistributedTaskRequestResult> collector = dispatchHelper
                                                .execAsync(request, WAIT_TIME, TimeUnit.MINUTES, TargetType.MASTER);

                                        final ClusterCallEntry<DistributedTaskRequestResult> response = collector.getSingleResponse();

                                        if (response == null) {
                                            LOGGER.error("No response from master while trying to fetch tasks");
                                        } else if (response.getError() != null) {
                                            try {
                                                throw response.getError();
                                            } catch (final MalformedURLException e) {
                                                LOGGER.warn(response.getError().getMessage());
                                            } catch (final ConnectException | HessianRuntimeException e) {
                                                LOGGER.error(response.getError().getMessage());
                                            } catch (final RuntimeException e) {
                                                LOGGER.error(response.getError().getMessage(), response.getError());
                                            }
                                        } else {
                                            final DistributedTaskRequestResult taskRequestResult = response.getResult();
                                            if (taskRequestResult == null) {
                                                LOGGER.error("No response object received from master while trying to fetch tasks");
                                            } else {
                                                handleResult(request, taskRequestResult);
                                            }
                                        }
                                    }
                                }
                            } catch (final Throwable t) {
                                LOGGER.error(t.getMessage(), t);
                            }
                        });

                        taskManager.execAsync(genericServerTask, new TaskCallbackAdaptor<VoidResult>() {
                            @Override
                            public void onSuccess(final VoidResult result) {
                                afterFetch();
                            }

                            @Override
                            public void onFailure(final Throwable t) {
                                afterFetch();
                            }
                        });
                    } else {
                        stopped.set(true);
                    }
                } else {
                    waitingToFetchTasks.set(true);
                }
            }
        } catch (final Exception e) {
            LOGGER.error("Unable to fetch task!", e);
        }
    }

    /**
     * Try and fetch more tasks after we have fetched some if there has been
     * another request for tasks in the mean time.
     */
    private void afterFetch() {
        fetchingTasks.set(false);

        // Fetch more tasks if we have other threads that wanted
        // to fetch more tasks.
        if (waitingToFetchTasks.compareAndSet(true, false)) {
            fetch();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void handleResult(final DistributedTaskRequestClusterTask request,
                              final DistributedTaskRequestResult response) {
        try {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Task response: node=\"" + request.getNode().getName() + "\"");
                if (LOGGER.isTraceEnabled()) {
                    final String trace = "\nTask response: node=\"" + request.getNode().getName() + "\"\n"
                            + response.toString();
                    LOGGER.trace(trace);
                }
            }

            // Get the current time to record execution.
            final long now = System.currentTimeMillis();

            // Execute all of the returned tasks.
            final Trackers trackers = jobNodeTrackerCache.getTrackers();
            for (final Entry<JobNode, List<DistributedTask<?>>> entry : response.getTaskMap().entrySet()) {
                // Get the latest local tracker.
                final JobNode jobNode = entry.getKey();
                final JobNodeTracker tracker = trackers.getTrackerForJobNode(jobNode);

                // Get the returned tasks.
                final List<DistributedTask<?>> tasks = entry.getValue();

                taskStatusTraceLog.receiveOnWorkerNode(DistributedTaskFetcher.class, tasks, jobNode.getJob().getName());

                // Try and get more tasks.
                tasks.stream().filter(task -> !stopping.get()).forEach(task -> {
                    runningTasks.add(task);
                    tracker.incrementTaskCount();
                    tracker.setLastExecutedTime(now);

                    if (!stopping.get()) {
                        taskManager.execAsync(task, new TaskCallbackAdaptor() {
                            @Override
                            public void onSuccess(final Object result) {
                                runningTasks.remove(task);
                                tracker.decrementTaskCount();

                                // Try and get more tasks.
                                fetch();
                            }

                            @Override
                            public void onFailure(final Throwable t) {
                                runningTasks.remove(task);
                                tracker.decrementTaskCount();
                            }
                        });
                    } else {
                        runningTasks.remove(task);
                        tracker.decrementTaskCount();
                    }
                });
            }
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }
    }

    /**
     * Creates an array of runnable jobs sorted into priority order.
     */
    private DistributedRequiredTask[] getDistributedRequiredTasks(final Trackers trackers) {
        final Collection<JobNodeTracker> trackerList = trackers.getTrackerList();

        // Create a shortlist of runnable jobs.
        DistributedRequiredTask[] requiredTasks = new DistributedRequiredTask[trackerList.size()];
        int length = 0;

        for (final JobNodeTracker tracker : trackerList) {
            final JobNode jobNode = tracker.getJobNode();
            final Job job = jobNode.getJob();

            if (JobType.DISTRIBUTED.equals(jobNode.getJobType())) {
                // Update the number of tasks that are still required by this
                // tracker.
                final int requiredTaskCount = jobNode.getTaskLimit() - tracker.getCurrentTaskCount();

                // The job and job node must be enabled in order for us to
                // request tasks. If they are then we still want to request
                // tasks even if the number of tasks required is 0 (or less...).
                // This is to ensure that the job cache on the distributor keeps
                // tasks cached even though we aren't actually requesting any at
                // this time.
                if (job.isEnabled() && jobNode.isEnabled()) {
                    // Store the task request.
                    requiredTasks[length++] = new DistributedRequiredTask(jobNode, requiredTaskCount);
                }
            }
        }

        // Trim the array.
        final DistributedRequiredTask[] tmp = new DistributedRequiredTask[length];
        System.arraycopy(requiredTasks, 0, tmp, 0, length);
        requiredTasks = tmp;

        return requiredTasks;
    }
}
