/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.task.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.RestFactory;
import stroom.node.client.NodeManager;
import stroom.task.client.event.OpenUserTaskManagerEvent;
import stroom.task.client.event.OpenUserTaskManagerHandler;
import stroom.task.client.presenter.UserTaskManagerPresenter.UserTaskManagerProxy;
import stroom.task.client.presenter.UserTaskManagerPresenter.UserTaskManagerView;
import stroom.task.shared.FindTaskCriteria;
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.TaskId;
import stroom.task.shared.TaskProgress;
import stroom.task.shared.TaskResource;
import stroom.task.shared.TerminateTaskProgressRequest;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.ResultPage;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UserTaskManagerPresenter
        extends MyPresenter<UserTaskManagerView, UserTaskManagerProxy>
        implements OpenUserTaskManagerHandler, UserTaskUiHandlers {

    private static final TaskResource TASK_RESOURCE = GWT.create(TaskResource.class);

    private final Provider<UserTaskPresenter> taskPresenterProvider;
    private final RestFactory restFactory;
    private final NodeManager nodeManager;
    private final Map<TaskProgress, UserTaskPresenter> taskPresenterMap = new HashMap<>();
    private final Map<TaskId, TaskProgress> idMap = new HashMap<>();
    private final Timer refreshTimer;
    private boolean visible;
    private final Set<String> refreshing = new HashSet<>();

    private final Map<String, List<TaskProgress>> responseMap = new HashMap<>();

    private final FindTaskProgressCriteria criteria;

    private final TaskManagerTreeAction treeAction = new TaskManagerTreeAction();
    private final DelayedUpdate delayedUpdate = new DelayedUpdate(this::update);

    @Inject
    public UserTaskManagerPresenter(final EventBus eventBus,
                                    final UserTaskManagerView view,
                                    final UserTaskManagerProxy proxy,
                                    final Provider<UserTaskPresenter> taskPresenterProvider,
                                    final RestFactory restFactory,
                                    final NodeManager nodeManager) {
        super(eventBus, view, proxy);
        this.taskPresenterProvider = taskPresenterProvider;
        this.restFactory = restFactory;
        this.nodeManager = nodeManager;

        refreshTimer = new Timer() {
            @Override
            public void run() {
                refreshTaskStatus();
            }
        };

        criteria = new FindTaskProgressCriteria();
        criteria.setSort(FindTaskProgressCriteria.FIELD_AGE, true, false);
    }

    @ProxyEvent
    @Override
    public void onOpen(final OpenUserTaskManagerEvent event) {
        setData(null);
        refreshTimer.scheduleRepeating(1000);
        refreshing.clear();
        visible = true;

        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .caption("Task Manager")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    refreshTimer.cancel();
                    refreshing.clear();
                    visible = false;
                    e.hide();
                })
                .fire();
    }

    private void refreshTaskStatus() {
        delayedUpdate.reset();
        // Stop this refreshing more than once before the call returns.
        nodeManager.listAllNodes(
                this::refresh,
                throwable -> {
                },
                this);
    }

    private void refresh(final List<String> nodeNames) {
        // Store node list for suture queries.
        for (final String nodeName : nodeNames) {
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
                        .taskMonitorFactory(this)
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

        // Refresh the display.
        setData(list);
    }

    private void setData(final ResultPage<TaskProgress> list) {
        if (visible) {
            final Set<UserTaskPresenter> tasksToRemove = new HashSet<>(taskPresenterMap.values());

            idMap.clear();
            if (list != null) {
                for (final TaskProgress taskProgress : list.getValues()) {
                    final TaskId thisId = taskProgress.getId();
                    // Avoid processing any duplicate task
                    if (idMap.containsKey(thisId)) {
                        break;
                    }

                    // Get the associated task presenter.
                    UserTaskPresenter taskPresenter = taskPresenterMap.get(taskProgress);

                    // If there isn't one then create a new one and add it to
                    // the display.
                    if (taskPresenter == null) {
                        taskPresenter = taskPresenterProvider.get();
                        taskPresenter.setUiHandlers(this);
                        taskPresenter.setTaskProgress(taskProgress);
                        taskPresenter.setTerminateVisible(true);

                        taskPresenterMap.put(taskProgress, taskPresenter);
                        getView().addTask(taskPresenter.getView());
                        idMap.put(taskProgress.getId(), taskProgress);
                    }

                    // We have updated or added this task so don't remove it
                    // from the display.
                    tasksToRemove.remove(taskPresenter);
                }
            }

            // Remove old tasks.
            for (final UserTaskPresenter actionPresenter : tasksToRemove) {
                getView().removeTask(actionPresenter.getView());
            }
        }
    }

    @Override
    public void onTerminate(final TaskProgress taskProgress) {
        final String message = "Are you sure you want to terminate this task?";
        ConfirmEvent.fire(UserTaskManagerPresenter.this, message, result -> {
            final FindTaskCriteria findTaskCriteria = new FindTaskCriteria();
            findTaskCriteria.addId(taskProgress.getId());
            final TerminateTaskProgressRequest request = new TerminateTaskProgressRequest(findTaskCriteria);
            restFactory
                    .create(TASK_RESOURCE)
                    .method(res -> res.terminate(taskProgress.getNodeName(), request))
                    .taskMonitorFactory(this)
                    .exec();
        });
    }

    @Override
    protected void revealInParent() {
    }


    // --------------------------------------------------------------------------------


    public interface UserTaskManagerView extends View, Focus {

        void addTask(View task);

        void removeTask(View task);
    }


    // --------------------------------------------------------------------------------


    @ProxyCodeSplit
    public interface UserTaskManagerProxy extends Proxy<UserTaskManagerPresenter> {

    }
}
