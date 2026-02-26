/*
 * Copyright 2016-2026 Crown Copyright
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
import stroom.core.client.presenter.Plugin;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.DocumentTabData;
import stroom.document.client.event.OpenDocumentEvent;
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
import stroom.task.client.HasTaskMonitorFactory;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.presenter.TextBoxPopup;
import stroom.widget.tab.client.event.RequestCloseAllTabsEvent;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Singleton
public class TabSessionManager extends Plugin implements TaskMonitorFactory, HasTaskMonitorFactory {

    private TaskMonitorFactory taskMonitorFactory = new DefaultTaskMonitorFactory(this);

    private static final TabSessionResource TAB_SESSION_RESOURCE = GWT.create(TabSessionResource.class);

    private final RestFactory restFactory;
    private final TabSessionChooserPresenter<TabSession> tabSessionChooserPresenter;
    private final Provider<TextBoxPopup> textBoxPopupProvider;
    private List<TabSession> userTabSessions;

    @Inject
    public TabSessionManager(final EventBus eventBus,
                             final RestFactory restFactory,
                             final TabSessionChooserPresenter<TabSession> tabSessionChooserPresenter,
                             final Provider<TextBoxPopup> textBoxPopupProvider) {
        super(eventBus);
        this.restFactory = restFactory;
        this.tabSessionChooserPresenter = tabSessionChooserPresenter;
        this.textBoxPopupProvider = textBoxPopupProvider;
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(getEventBus().addHandler(OpenTabSessionEvent.getType(),
                event -> {
                    checkDirtyTabsThenRun(() ->
                            chooseTabSessionThenAccept("Select Tab Session To Open:", this::openTabSession)
                    );
                }));

        registerHandler(getEventBus().addHandler(DeleteTabSessionEvent.getType(),
                event ->
                        chooseTabSessionThenAccept("Select Tab Session To Delete:", this::delete)
                ));


        registerHandler(getEventBus().addHandler(SaveTabSessionEvent.getType(), event -> {
            final TextBoxPopup textBoxPopup = textBoxPopupProvider.get();
            textBoxPopup.show("Save New Tab Session", sessionName -> {
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
        }));
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

            tabSessionChooserPresenter.setSelectionList(sessions);
            tabSessionChooserPresenter.setDisplayValueFunction(s -> SafeHtmlUtils.fromString(
                    s.getName()));

            final PopupSize popupSize = PopupSize.resizable(650, 340);
            ShowPopupEvent.builder(tabSessionChooserPresenter)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .popupSize(popupSize)
                    .caption(caption)
                    .onHideRequest(e -> {
                        final Optional<TabSession> tabSession = tabSessionChooserPresenter.getSelected();
                        if (e.isOk() && tabSession.isPresent()) {
                            consumer.accept(tabSession.get());
                        }
                        e.hide();
                    })
                    .fire();
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
                    .callbackOnFailure(child::fire);
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

    @Override
    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        this.taskMonitorFactory = taskMonitorFactory;
    }

    @Override
    public TaskMonitor createTaskMonitor() {
        return taskMonitorFactory.createTaskMonitor();
    }
}
