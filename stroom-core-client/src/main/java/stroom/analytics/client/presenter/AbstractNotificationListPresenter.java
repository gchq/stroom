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

package stroom.analytics.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.NotificationConfig;
import stroom.analytics.shared.NotificationEmailDestination;
import stroom.analytics.shared.NotificationStreamDestination;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.config.global.client.presenter.ListDataProvider;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.DataGridSelectionEventManager;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.docref.DocRef;
import stroom.docref.HasDisplayValue;
import stroom.entity.client.presenter.DocPresenter;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MultiSelectEvent;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.event.shared.HandlerRegistration;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractNotificationListPresenter<D extends AbstractAnalyticRuleDoc>
        extends DocPresenter<PagerView, D> {

    private final MyDataGrid<NotificationConfig> dataGrid;
    private final MultiSelectionModelImpl<NotificationConfig> selectionModel;
    private final ButtonView addButton;
    private final ButtonView editButton;
    private final ButtonView removeButton;
    private boolean initialised;
    private final Provider<AnalyticNotificationEditPresenter> editPresenterProvider;
    private final ListDataProvider<NotificationConfig> dataProvider;
    final List<NotificationConfig> list = new ArrayList<>();
    private DocRef docRef;
    private AnalyticProcessType analyticProcessType;

    @Inject
    public AbstractNotificationListPresenter(final EventBus eventBus,
                                             final PagerView view,
                                             final Provider<AnalyticNotificationEditPresenter> editPresenterProvider) {
        super(eventBus, view);
        this.editPresenterProvider = editPresenterProvider;

        dataGrid = new MyDataGrid<>(this);
        dataGrid.setTableName("Notifications");
        selectionModel = new MultiSelectionModelImpl<>();
        final DataGridSelectionEventManager<NotificationConfig> selectionEventManager =
                new DataGridSelectionEventManager<>(
                        dataGrid,
                        selectionModel,
                        false);
        dataGrid.setSelectionModel(selectionModel, selectionEventManager);
        view.setDataWidget(dataGrid);
        dataProvider = new ListDataProvider<>();
        dataProvider.setCompleteList(list);

        addButton = view.addButton(SvgPresets.ADD);
        editButton = view.addButton(SvgPresets.EDIT);
        removeButton = view.addButton(SvgPresets.DELETE);

        addColumns();
        enableButtons();
    }

    @Override
    protected void onBind() {
        registerHandler(addButton.addClickHandler(ignored -> add()));
        registerHandler(editButton.addClickHandler(ignored -> edit()));
        registerHandler(removeButton.addClickHandler(ignored -> remove()));
        registerHandler(selectionModel.addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                edit();
            }
        }));
    }

    private void add() {
        final AnalyticNotificationEditPresenter presenter = editPresenterProvider.get();
        presenter.read(docRef, analyticProcessType, NotificationConfig.builder().build());
        ShowPopupEvent
                .builder(presenter)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(564, 564))
                .caption("Add Notification")
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final NotificationConfig notification = presenter.write();
                        list.add(notification);
                        onChange();
                        refresh();
                    }
                    e.hide();
                })
                .fire();
    }

    private void edit() {
        final NotificationConfig selected = selectionModel.getSelected();
        if (selected != null) {
            final AnalyticNotificationEditPresenter presenter = editPresenterProvider.get();
            presenter.read(docRef, analyticProcessType, selected);
            ShowPopupEvent
                    .builder(presenter)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .popupSize(PopupSize.resizable(564, 564))
                    .caption("Edit Notification")
                    .onHideRequest(e -> {
                        if (e.isOk()) {
                            final NotificationConfig updated = presenter.write();
                            replace(selected, updated);
                            onChange();
                            refresh();
                        }
                        e.hide();
                    })
                    .fire();
        }
    }

    private void remove() {
        ConfirmEvent.fire(this, "Are you sure you want to remove this notification?",
                result -> {
                    if (result) {
                        final NotificationConfig selected = selectionModel.getSelected();
                        if (selected != null) {
                            int index = list.indexOf(selected);
                            list.remove(selected);
                            onChange();
                            refresh();

                            // Select next item.
                            if (NullSafe.hasItems(list)) {
                                index = Math.max(index, 0);
                                index = Math.min(index, list.size() - 1);
                                selectionModel.setSelected(list.get(index));
                            } else {
                                selectionModel.clear();
                            }
                        }
                    }
                });
    }

    private void addColumns() {
        // Enable notifications
        dataGrid.addColumn(
                DataGridUtil.updatableTickBoxColumnBuilder(
                                TickBoxState.createTickBoxFunc(NotificationConfig::isEnabled))
                        .withFieldUpdater((ignored, row, value) -> {
                            final NotificationConfig updated = row.copy()
                                    .enabled(TickBoxState.getAsBoolean(value))
                                    .build();
                            replace(row, updated);
                            onChange();
                            refresh();
                        })
                        .build(),
                DataGridUtil.headingBuilder("Enabled")
                        .withToolTip("Whether notifications will be sent to this destination or not.")
                        .build(),
                80);

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataGridUtil.toStringFunc(
                                NotificationConfig::getDestinationType,
                                HasDisplayValue::getDisplayValue))
                        .enabledWhen(NotificationConfig::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Type")
                        .withToolTip("The type of notification to perform (Email or Stream).")
                        .build(),
                ColumnSizeConstants.MEDIUM_COL);

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(this::getDestinationAsString)
                        .enabledWhen(NotificationConfig::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Destination")
                        .withToolTip("The destination of this notification. Either the Feed for a Stream " +
                                     "destination, or the recipient for an Email destination.")
                        .build(),
                ColumnSizeConstants.BIG_COL);

        // Limit notifications
        dataGrid.addColumn(
                DataGridUtil.updatableTickBoxColumnBuilder(TickBoxState.createTickBoxFunc(
                                NotificationConfig::isLimitNotifications))
                        .enabledWhen(NotificationConfig::isEnabled)
                        .withFieldUpdater((ignored, row, value) -> {
                            final NotificationConfig updated = row.copy()
                                    .limitNotifications(TickBoxState.getAsBoolean(value))
                                    .build();
                            replace(row, updated);
                            onChange();
                            refresh();
                        })
                        .build(),
                DataGridUtil.headingBuilder("Limit")
                        .withToolTip("If set, limits the number of notification to the value of " +
                                     "'Maximum Notifications'.")
                        .build(),
                80);

        // Max notifications
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataGridUtil.toStringFunc(
                                NotificationConfig::getMaxNotifications,
                                String::valueOf))
                        .enabledWhen(NotificationConfig::isEnabled)
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("Max")
                        .withToolTip("If 'Limit' is set, limits the number of notification to this value.")
                        .rightAligned()
                        .build(),
                ColumnSizeConstants.MEDIUM_COL);
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

    private void enableButtons() {
        addButton.setEnabled(true);
        editButton.setEnabled(NullSafe.hasItems(selectionModel.getSelectedItems()));
        removeButton.setEnabled(NullSafe.hasItems(selectionModel.getSelectedItems()));
        addButton.setTitle("Add Notification");
        editButton.setTitle("Edit Notification");
        removeButton.setTitle("Remove Notification");
    }

    /**
     * The processing type lives on the Execution tab, so it can change while this tab is open. Whether the
     * source feed option applies depends on it, so take the new value rather than waiting for the document to
     * be read again.
     */
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

    @Override
    protected void onRead(final DocRef docRef, final D document, final boolean readOnly) {
        this.docRef = docRef;
        // Deliberately not taking the processing type from the document. This tab is read lazily on first
        // open, which can be after the type has been changed on the execution tab, so the document would be
        // stale. The owning presenter tells us instead, see setAnalyticProcessType.
        list.clear();
        if (document.getNotifications() != null) {
            list.addAll(document.getNotifications());
        }
        refresh();
    }

    public void refresh() {
        if (!initialised) {
            initialised = true;
            dataProvider.addDataDisplay(dataGrid);
        }
        dataProvider.setCompleteList(list);
    }

    public HandlerRegistration addSelectionHandler(final MultiSelectEvent.Handler handler) {
        return selectionModel.addSelectionHandler(handler);
    }

    public NotificationConfig getSelected() {
        return selectionModel.getSelected();
    }

    public void setSelected(final NotificationConfig executionSchedule) {
        selectionModel.setSelected(executionSchedule);
    }
}
