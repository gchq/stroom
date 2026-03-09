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

package stroom.analytics.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.analytics.shared.AbstractAnalyticRuleDoc;
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

    @Inject
    public AbstractNotificationListPresenter(final EventBus eventBus,
                                             final PagerView view,
                                             final Provider<AnalyticNotificationEditPresenter> editPresenterProvider) {
        super(eventBus, view);
        this.editPresenterProvider = editPresenterProvider;

        dataGrid = new MyDataGrid<>(this);
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
        presenter.read(docRef, NotificationConfig.builder().build());
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
            presenter.read(docRef, selected);
            ShowPopupEvent
                    .builder(presenter)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .popupSize(PopupSize.resizable(564, 564))
                    .caption("Edit Notification")
                    .onHideRequest(e -> {
                        if (e.isOk()) {
                            final NotificationConfig updated = presenter.write();
                            replace(updated);
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
                            replace(updated);
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
                            replace(updated);
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

        DataGridUtil.addEndColumn(dataGrid);
    }

    private String getDestinationAsString(final NotificationConfig row) {
        if (row.getDestination() instanceof final NotificationStreamDestination streamDest) {
            return NullSafe.get(streamDest.getDestinationFeed(),
                    DocRef::getDisplayValue);
        } else if (row.getDestination() instanceof final NotificationEmailDestination emailDest) {
            return emailDest.getTo();
        }
        return null;
    }

    private void replace(final NotificationConfig notificationConfig) {
        final int index = list.indexOf(notificationConfig);
        if (index >= 0) {
            list.remove(notificationConfig);
            list.add(index, notificationConfig);
        } else {
            list.add(notificationConfig);
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

    @Override
    protected void onRead(final DocRef docRef, final D document, final boolean readOnly) {
        this.docRef = docRef;
        list.clear();
        if (document.getNotifications() != null) {
            list.addAll(document.getNotifications());
        }
        refresh();
    }

    public void clear() {
        list.clear();
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
