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

package stroom.task.client.component;

import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestFactory;
import stroom.node.client.NodeClient;
import stroom.task.client.ServerTaskTreeAction;
import stroom.task.client.TaskProgressUtil;
import stroom.task.shared.FindTaskCriteria;
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.TaskId;
import stroom.task.shared.TaskProgress;
import stroom.task.shared.TaskResource;
import stroom.task.shared.TerminateTaskProgressRequest;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.ResultPage;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Timer;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The user's own Task Manager ([UserTaskManager]): their running tasks on every node, fetched
/// every second while the dialog, made for one use, is shown, each of which can be terminated.
public class UserTaskManagerController extends Controller {

    private static final TaskResource TASK_RESOURCE = GWT.create(TaskResource.class);

    private final UserTaskManager view;
    private final RestFactory restFactory;
    private final NodeClient nodeClient;
    private final Map<TaskProgress, UserTask> taskMap = new HashMap<>();
    private final Map<TaskId, TaskProgress> idMap = new HashMap<>();
    private final Set<String> refreshing = new HashSet<>();
    private final Map<String, List<TaskProgress>> responseMap = new HashMap<>();
    private final FindTaskProgressCriteria criteria;
    private final ServerTaskTreeAction treeAction = new ServerTaskTreeAction();
    private final DelayedUpdate delayedUpdate = new DelayedUpdate(this::update);
    private final Timer refreshTimer = new Timer() {
        @Override
        public void run() {
            refreshTaskStatus();
        }
    };

    /// Made by GIN, with its list.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        The list.
    /// @param restFactory Fetches and terminates the tasks.
    /// @param nodeClient  Lists the nodes to fetch them from.
    @Inject
    public UserTaskManagerController(final EventBus eventBus,
                                     final UserTaskManager view,
                                     final RestFactory restFactory,
                                     final NodeClient nodeClient) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.nodeClient = nodeClient;
        initWidget(view);

        criteria = new FindTaskProgressCriteria();
        criteria.setSort(FindTaskProgressCriteria.FIELD_AGE, true, false);
        // The polling stops when the dialog goes
        onDispose(refreshTimer::cancel);
        onDispose(delayedUpdate::reset);
    }

    /// Shows the user's tasks in a dialog, owned by `opener` until it is closed, fetching them
    /// every second until then.
    ///
    /// @param opener What shows it.
    public void show(final HasControllerNode opener) {
        refreshTimer.scheduleRepeating(1000);
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.CLOSE_DIALOG)
                .caption("Task Manager")
                .onShow(e -> view.focus())
                .fire();
    }

    private void refreshTaskStatus() {
        delayedUpdate.reset();
        nodeClient.listAllNodes(
                this::refresh,
                throwable -> {
                },
                getProvidedTaskMonitor());
    }

    private void refresh(final List<String> nodeNames) {
        if (isDisposed()) {
            return;
        }
        for (final String nodeName : nodeNames) {
            // Stop this refreshing more than once before the call returns.
            if (!refreshing.contains(nodeName)) {
                refreshing.add(nodeName);
                restFactory
                        .create(TASK_RESOURCE)
                        .method(res -> res.userTasks(nodeName))
                        .onSuccess(response -> {
                            responseMap.put(nodeName, response.getValues());
                            delayedUpdate.update();
                            refreshing.remove(nodeName);
                        })
                        .onFailure(throwable -> {
                            responseMap.remove(nodeName);
                            delayedUpdate.update();
                            refreshing.remove(nodeName);
                        })
                        .owner(this)
                        .exec();
            }
        }
    }

    private void update() {
        // Combine data from all nodes.
        final ResultPage<TaskProgress> list = TaskProgressUtil.combine(
                new Range(0, 100),
                criteria,
                responseMap.values(),
                treeAction);
        setData(list);
    }

    private void setData(final ResultPage<TaskProgress> list) {
        final Set<UserTask> tasksToRemove = new HashSet<>(taskMap.values());

        idMap.clear();
        if (list != null) {
            for (final TaskProgress taskProgress : list.getValues()) {
                final TaskId thisId = taskProgress.getId();
                // Avoid processing any duplicate task
                if (idMap.containsKey(thisId)) {
                    break;
                }

                // Get the associated task's row.
                UserTask task = taskMap.get(taskProgress);

                // If there isn't one then create a new one and add it to
                // the display.
                if (task == null) {
                    task = new UserTask();
                    task.setTaskProgress(taskProgress);
                    task.setOnTerminate(() -> onTerminate(taskProgress));

                    taskMap.put(taskProgress, task);
                    view.addTask(task);
                    idMap.put(taskProgress.getId(), taskProgress);
                }

                // We have updated or added this task so don't remove it
                // from the display.
                tasksToRemove.remove(task);
            }
        }

        // Remove old tasks
        for (final UserTask task : tasksToRemove) {
            view.removeTask(task);
            taskMap.values().remove(task);
        }
    }

    private void onTerminate(final TaskProgress taskProgress) {
        final String message = "Are you sure you want to terminate this task?";
        ConfirmEvent.fire(this, message, ok -> {
            if (ok) {
                final FindTaskCriteria findTaskCriteria = new FindTaskCriteria();
                findTaskCriteria.addId(taskProgress.getId());
                final TerminateTaskProgressRequest request = new TerminateTaskProgressRequest(findTaskCriteria);
                restFactory
                        .create(TASK_RESOURCE)
                        .method(res -> res.terminate(taskProgress.getNodeName(), request))
                        .owner(this)
                        .exec();
            }
        });
    }
}
