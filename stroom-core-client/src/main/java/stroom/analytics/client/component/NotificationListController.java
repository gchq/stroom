/*
 * Copyright 2024 Crown Copyright
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

package stroom.analytics.client.component;

import stroom.alert.client.event.ConfirmEvent;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.NotificationConfig;
import stroom.analytics.shared.NotificationDestinationType;
import stroom.analytics.shared.NotificationEmailDestination;
import stroom.analytics.shared.NotificationStreamDestination;
import stroom.config.global.client.presenter.ListDataProvider;
import stroom.docref.DocRef;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;

/// An analytic rule's or a report's notifications ([NotificationList]): New and Edit open a
/// notification's editor, Remove removes the selected one once confirmed, and the tick boxes enable
/// or limit one, unless the document is read only ([Contexts#READ_ONLY]). Changes mark the document
/// changed.
public class NotificationListController extends Controller {

    private final NotificationList view;
    private final Provider<AnalyticNotificationEditController> editProvider;
    private final ListDataProvider<NotificationConfig> dataProvider;
    private final List<NotificationConfig> list = new ArrayList<>();
    private boolean initialised;
    private DocRef docRef;
    private AnalyticProcessType analyticProcessType;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus              The application's event bus.
    /// @param view                  The content.
    /// @param editPresenterProvider Makes a notification's editor.
    @Inject
    public NotificationListController(final EventBus eventBus,
                                      final NotificationList view,
                                      final Provider<AnalyticNotificationEditController> editProvider) {
        super(eventBus);
        this.view = view;
        this.editProvider = editProvider;
        initWidget(view);
        dataProvider = new ListDataProvider<>();
        dataProvider.setCompleteList(list);

        view.addColumns(this::getDestinationAsString);
        view.setOnAdd(this::add);
        view.setOnEdit(this::edit);
        view.setOnRemove(this::remove);
        view.setOnEnabledChange((row, enabled) -> {
            replace(row, row.copy().enabled(enabled).build());
            markChanged();
            refresh();
        });
        view.setOnLimitChange((row, limit) -> {
            replace(row, row.copy().limitNotifications(limit).build());
            markChanged();
            refresh();
        });
        watch(Contexts.READ_ONLY, view::setReadOnly);
    }

    private void add() {
        if (view.isReadOnly()) {
            return;
        }
        // A new notification goes to a stream unless the user chooses otherwise; without a
        // destination type it would be added with none
        final NotificationConfig newNotification = NotificationConfig.builder()
                .destinationType(NotificationDestinationType.STREAM)
                .build();
        editProvider.get().edit(this, docRef, analyticProcessType, newNotification, "Add Notification",
                notification -> {
                    list.add(notification);
                    markChanged();
                    refresh();
                });
    }

    private void edit() {
        final NotificationConfig selected = view.getSelectionModel().getSelected();
        if (selected != null && !view.isReadOnly()) {
            editProvider.get().edit(this, docRef, analyticProcessType, selected, "Edit Notification", updated -> {
                replace(selected, updated);
                markChanged();
                refresh();
            });
        }
    }

    private void remove() {
        if (view.isReadOnly()) {
            return;
        }
        ConfirmEvent.fire(this, "Are you sure you want to remove this notification?",
                result -> {
                    if (result) {
                        final NotificationConfig selected = view.getSelectionModel().getSelected();
                        if (selected != null) {
                            list.remove(selected);
                            markChanged();
                            refresh();

                            // Select next item.
                            int index = list.indexOf(selected);
                            if (NullSafe.hasItems(list)) {
                                index = Math.max(index, 0);
                                index = Math.min(index, list.size() - 1);
                                view.getSelectionModel().setSelected(list.get(index));
                            } else {
                                view.getSelectionModel().clear();
                            }
                        }
                    }
                });
    }

    private String getDestinationAsString(final NotificationConfig row) {
        if (row.getDestination() instanceof final NotificationStreamDestination streamDest) {
            // Say where the detections will actually go, which for a streaming rule using the source feed is
            // not the destination feed shown against the notification.
            if (streamDest.isUsingSourceFeed(analyticProcessType)) {
                return "Source feed";
            }
            return NullSafe.get(streamDest.getDestinationFeed(),
                    DocRef::getDisplayValue);
        } else if (row.getDestination() instanceof final NotificationEmailDestination emailDest) {
            return emailDest.getTo();
        }
        return null;
    }

    private void replace(final NotificationConfig oldConfig,
                         final NotificationConfig newConfig) {
        final int index = list.indexOf(oldConfig);
        if (index >= 0) {
            list.remove(index);
            list.add(index, newConfig);
        } else {
            list.add(newConfig);
        }
    }

    /// The processing type lives on the Execution tab, so it can change while this tab is open. Whether
    /// the source feed option applies depends on it, so take the new value rather than waiting for the
    /// document to be read again.
    ///
    /// @param analyticProcessType The rule's processing type.
    public void setAnalyticProcessType(final AnalyticProcessType analyticProcessType) {
        if (this.analyticProcessType != analyticProcessType) {
            this.analyticProcessType = analyticProcessType;
            // The destination column says where detections will go, which this changes. Only worth redrawing
            // if the grid is already showing, as the first draw will use the new value regardless, and
            // refresh() would otherwise bind the data display before the tab has ever been opened.
            if (initialised) {
                refresh();
            }
        }
    }

    /// Shows a document's notifications.
    ///
    /// @param docRef        The document.
    /// @param notifications Its notifications, or `null` for none.
    public void read(final DocRef docRef, final List<NotificationConfig> notifications) {
        this.docRef = docRef;
        // Deliberately not taking the processing type from the document. This tab is read lazily on first
        // open, which can be after the type has been changed on the execution tab, so the document would be
        // stale. The owning presenter tells us instead, see setAnalyticProcessType.
        list.clear();
        if (notifications != null) {
            list.addAll(notifications);
        }
        refresh();
    }

    /// @return A copy of the notifications shown, for the document.
    public List<NotificationConfig> getNotifications() {
        return new ArrayList<>(list);
    }

    private void refresh() {
        if (!initialised) {
            initialised = true;
            view.setDataProvider(dataProvider);
        }
        dataProvider.setCompleteList(list);
    }
}
