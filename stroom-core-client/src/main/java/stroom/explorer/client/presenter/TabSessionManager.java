/*
 * Copyright 2026 Crown Copyright
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

package stroom.explorer.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.core.client.HasSave;
import stroom.core.client.Plugin;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.DocumentTabData;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.explorer.client.component.TabSessionChooserController;
import stroom.explorer.client.event.DeleteTabSessionEvent;
import stroom.explorer.client.event.GetCurrentTabsEvent;
import stroom.explorer.client.event.OpenTabSessionEvent;
import stroom.explorer.client.event.SaveTabSessionEvent;
import stroom.explorer.client.event.TabSessionChangeEvent;
import stroom.explorer.shared.TabSession;
import stroom.explorer.shared.TabSessionAddRequest;
import stroom.explorer.shared.TabSessionDeleteRequest;
import stroom.explorer.shared.TabSessionResource;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.component.client.AppRoot;
import stroom.widget.popup.client.component.NameDialogController;
import stroom.widget.tab.client.event.RequestCloseAllTabsEvent;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Singleton
public class TabSessionManager extends Plugin implements TaskMonitorFactory {

    private TaskMonitorFactory taskMonitorFactory = new DefaultTaskMonitorFactory(this);

    private static final TabSessionResource TAB_SESSION_RESOURCE = GWT.create(TabSessionResource.class);

    private final RestFactory restFactory;
    private final Provider<TabSessionChooserController> tabSessionChooserProvider;
    private final Provider<NameDialogController> nameDialogProvider;
    private List<TabSession> userTabSessions;

    private final AppRoot appRoot;

    @Inject
    public TabSessionManager(final EventBus eventBus,
                             final RestFactory restFactory,
                             final Provider<TabSessionChooserController> tabSessionChooserProvider,
                             final Provider<NameDialogController> nameDialogProvider,
                             final AppRoot appRoot) {
        super(eventBus);
        this.appRoot = appRoot;
        this.restFactory = restFactory;
        this.tabSessionChooserProvider = tabSessionChooserProvider;
        this.nameDialogProvider = nameDialogProvider;

        getEventBus().addHandler(OpenTabSessionEvent.getType(),
                event -> {
                    checkDirtyTabsThenRun(() ->
                            chooseTabSessionThenAccept("Select Tab Session To Open:", this::openTabSession)
                    );
                });

        getEventBus().addHandler(DeleteTabSessionEvent.getType(),
                event ->
                        chooseTabSessionThenAccept("Select Tab Session To Delete:", this::delete));

        getEventBus().addHandler(SaveTabSessionEvent.getType(), event -> {
            nameDialogProvider.get().show(appRoot, "Save New Tab Session", null, sessionName -> {
                if (contains(userTabSessions, sessionName)) {
                    ConfirmEvent.fire(this,
                            "You are going to overwrite an existing tab session. Do you wish to continue?",
                            ok -> {
                            if (ok) {
                                getTabsAndSave(sessionName);
                            }
                        });
                } else {
                    getTabsAndSave(sessionName);
                }
            });
        });
    }

    private void checkDirtyTabsThenRun(final Runnable runnable) {
        GetCurrentTabsEvent.fire(this, tabDataList -> {
            final boolean anyDirty = tabDataList.stream()
                    .filter(t -> t instanceof HasSave)
                    .map(t -> (HasSave) t)
                    .anyMatch(HasSave::isDirty);

            if (anyDirty) {
                AlertEvent.fireInfo(this, "Please close or save any modified tabs before trying again.",
                        () -> {});
            } else {
                runnable.run();
            }
        });
    }

    private void chooseTabSessionThenAccept(final String caption, final Consumer<TabSession> consumer) {
        getTabSessionsThenAccept(sessions -> {
            if (sessions == null || sessions.isEmpty()) {
                AlertEvent.fireInfo(this, "You do not have any saved tab sessions.", null);
                return;
            }

            if (sessions.size() == 1) {
                consumer.accept(sessions.get(0));
                return;
            }

            tabSessionChooserProvider.get().choose(appRoot, caption, sessions, consumer);
        });
    }

    private void openTabSession(final TabSession tabSession) {
        final List<DocRef> docRefs = tabSession.getDocRefs();

        if (docRefs == null || docRefs.isEmpty()) {
            return;
        }

        RequestCloseAllTabsEvent.fire(this);

        // Chain the OpenDocumentEvents so we have the tabs opened in the same order as they were saved
        buildOpenDocumentEvents(docRefs, docRefs.size() - 1, null).fire();
    }

    private OpenDocumentEvent.Builder buildOpenDocumentEvents(
            final List<DocRef> docRefs, final int index, final OpenDocumentEvent.Builder child) {
        if (index < 0) {
            return child;
        }

        final OpenDocumentEvent.Builder builder = OpenDocumentEvent.builder(this, docRefs.get(index));
        if (child != null) {
            builder.callbackOnOpen(p -> child.fire())
                    .callbackOnFailure(child::fire)
                    .duplicate(true);
        }

        return buildOpenDocumentEvents(docRefs, index - 1, builder);
    }

    public void getTabSessionsThenAccept(final Consumer<List<TabSession>> consumer) {
        restFactory
                .create(TAB_SESSION_RESOURCE)
                .method(TabSessionResource::getForCurrentUser)
                .onSuccess(ts -> {
                    userTabSessions = new ArrayList<>(ts);
                    consumer.accept(ts);
                })
                .onFailure(error ->
                        AlertEvent.fireError(this,
                                error.getMessage(),
                                null))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    private void saveTabSession(final String sessionName, final List<DocRef> docRefs) {
        restFactory.create(TAB_SESSION_RESOURCE)
                .method(res -> res.add(new TabSessionAddRequest(sessionName, docRefs)))
                .onSuccess(ts -> {
                    userTabSessions = new ArrayList<>(ts);
                    TabSessionChangeEvent.fire(this, ts);
                })
                .onFailure(error ->
                            AlertEvent.fireError(this,
                                    error.getMessage(),
                                    null))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    private void delete(final TabSession tabSession) {
        ConfirmEvent.fire(this,
                "Are you sure you want to delete the tab session '" + tabSession.getName() + "'?", ok -> {
                if (ok) {
                    restFactory.create(TAB_SESSION_RESOURCE)
                            .method(res -> res.delete(
                                    new TabSessionDeleteRequest(tabSession.getSessionId(), tabSession.getName())
                            ))
                            .onSuccess(ts -> {
                                userTabSessions = new ArrayList<>(ts);
                                TabSessionChangeEvent.fire(this, ts);
                            })
                            .onFailure(error ->
                                    AlertEvent.fireError(this,
                                            error.getMessage(),
                                            null))
                            .taskMonitorFactory(taskMonitorFactory)
                            .exec();
                }
            });
    }

    private boolean contains(final List<TabSession> tabSessions, final String name) {
        return tabSessions.stream().map(TabSession::getName).anyMatch(name::equalsIgnoreCase);
    }

    private void getTabsAndSave(final String sessionName) {
        GetCurrentTabsEvent.fire(this, tabDataList -> {

            final List<DocRef> docRefs = tabDataList.stream()
                    .filter(t -> t instanceof DocumentTabData)
                    .map(t -> (DocumentTabData) t)
                    .map(DocumentTabData::getDocRef)
                    .collect(Collectors.toList());

            saveTabSession(sessionName, docRefs);
        });
    }

    /// Says what shows the progress of the requests this makes, e.g. the presenter that uses it.
    ///
    /// @param taskMonitorFactory What shows their progress.
    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        this.taskMonitorFactory = taskMonitorFactory;
    }

    @Override
    public TaskMonitor createTaskMonitor() {
        return taskMonitorFactory.createTaskMonitor();
    }
}
