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

package stroom.task.client.presenter;

import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.Presenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.entity.shared.BaseResultList;
import stroom.task.client.event.OpenTaskManagerEvent;
import stroom.task.client.event.OpenTaskManagerHandler;
import stroom.task.shared.FindTaskCriteria;
import stroom.task.shared.FindUserTaskProgressAction;
import stroom.task.shared.TaskProgress;
import stroom.task.shared.TerminateTaskProgressAction;
import stroom.util.shared.TaskId;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TaskManagerPresenter
        extends Presenter<TaskManagerPresenter.TaskManagerView, TaskManagerPresenter.TaskManagerProxy>
        implements OpenTaskManagerHandler, TaskUiHandlers {
    private final Provider<TaskPresenter> taskPresenterProvider;
    private final ClientDispatchAsync dispatcher;
    private final Map<TaskProgress, TaskPresenter> taskPresenterMap = new HashMap<>();
    private final Map<TaskId, TaskProgress> idMap = new HashMap<>();
    private final Set<TaskId> requestTaskKillSet = new HashSet<>();
    private final Timer refreshTimer;
    private boolean visible;
    private boolean refreshing;
    @Inject
    public TaskManagerPresenter(final EventBus eventBus, final TaskManagerView view, final TaskManagerProxy proxy,
                                final Provider<TaskPresenter> taskPresenterProvider, final ClientDispatchAsync dispatcher) {
        super(eventBus, view, proxy);
        this.taskPresenterProvider = taskPresenterProvider;
        this.dispatcher = dispatcher;

        refreshTimer = new Timer() {
            @Override
            public void run() {
                refreshTaskStatus();
            }
        };
    }

    @ProxyEvent
    @Override
    public void onOpen(final OpenTaskManagerEvent event) {
        refresh(null);
        refreshTimer.scheduleRepeating(1000);
        refreshing = false;
        visible = true;

        final PopupUiHandlers popupUiHandlers = new DefaultPopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                refreshTimer.cancel();
                refreshing = false;
                visible = false;

                HidePopupEvent.fire(TaskManagerPresenter.this, TaskManagerPresenter.this);
            }
        };

        ShowPopupEvent.fire(this, this, PopupType.CLOSE_DIALOG, "Task Manager", popupUiHandlers, false);
    }

    private void refreshTaskStatus() {
        // Stop this refreshing more than once before the call returns.
        if (!refreshing) {
            refreshing = true;

            final FindUserTaskProgressAction action = new FindUserTaskProgressAction();
            dispatcher.exec(action)
                    .onSuccess(result -> {
                        final HashSet<TaskId> idSet = new HashSet<>();
                        for (final TaskProgress value : result) {
                            idSet.add(value.getId());
                        }
                        requestTaskKillSet.retainAll(idSet);

                        // Refresh the display.
                        refresh(result);

                        refreshing = false;
                    })
                    .onFailure(caught -> refreshing = false);
        }
    }

    private void refresh(final BaseResultList<TaskProgress> result) {
        if (visible) {
            final Set<TaskPresenter> tasksToRemove = new HashSet<>(taskPresenterMap.values());

            idMap.clear();
            if (result != null) {
                for (final TaskProgress taskProgress : result) {
                    final TaskId thisId = taskProgress.getId();
                    // Avoid processing any duplicate task
                    if (idMap.containsKey(thisId)) {
                        break;
                    }

                    // Get the associated task presenter.
                    TaskPresenter taskPresenter = taskPresenterMap.get(taskProgress);

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
                    // fromthe display.
                    tasksToRemove.remove(taskPresenter);
                }
            }

            // Remove old tasks.
            for (final TaskPresenter actionPresenter : tasksToRemove) {
                getView().removeTask(actionPresenter.getView());
            }
        }
    }

    @Override
    public void onTerminate(final TaskId terminateId, final String taskName) {
        String message = "Are you sure you want to terminate this task?";
        if (requestTaskKillSet.contains(terminateId)) {
            message = "Are you sure you want to kill this task?";
        }
        ConfirmEvent.fire(TaskManagerPresenter.this, message, result -> {
            final boolean kill = requestTaskKillSet.contains(terminateId);
            final FindTaskCriteria findTaskCriteria = new FindTaskCriteria();
            findTaskCriteria.addId(terminateId);
            final TerminateTaskProgressAction action = new TerminateTaskProgressAction("Terminate: " + taskName,
                    findTaskCriteria, kill);

            requestTaskKillSet.add(terminateId);
            dispatcher.exec(action);
        });
    }

    @Override
    protected void revealInParent() {
    }

    public interface TaskManagerView extends View {
        void addTask(View task);

        void removeTask(View task);
    }

    @ProxyCodeSplit
    public interface TaskManagerProxy extends Proxy<TaskManagerPresenter> {
    }
}
