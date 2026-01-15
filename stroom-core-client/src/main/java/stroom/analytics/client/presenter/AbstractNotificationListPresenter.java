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
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.config.global.client.presenter.ListDataProvider;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.grid.client.DataGridSelectionEventManager;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.util.client.MultiSelectEvent;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.cellview.client.Column;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractNotificationListPresenter<D extends AbstractAnalyticRuleDoc>
        extends DocumentEditPresenter<PagerView, D> {

    private final MyDataGrid<NotificationConfig> dataGrid;
    private final MultiSelectionModelImpl<NotificationConfig> selectionModel;
    private final DataGridSelectionEventManager<NotificationConfig> selectionEventManager;
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
        selectionEventManager = new DataGridSelectionEventManager<>(dataGrid, selectionModel, false);
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
        registerHandler(addButton.addClickHandler(e -> add()));
        registerHandler(editButton.addClickHandler(e -> edit()));
        registerHandler(removeButton.addClickHandler(e -> remove()));
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
                        setDirty(true);
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
                            setDirty(true);
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
                            setDirty(true);
                            refresh();

                            // Select next item.
                            if (list.size() > 0) {
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
        final Column<NotificationConfig, TickBoxState> enabledColumn = new Column<NotificationConfig, TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue(final NotificationConfig row) {
                return TickBoxState.fromBoolean(row.isEnabled());
            }
        };
        enabledColumn.setFieldUpdater((index, row, value) -> {
            final NotificationConfig updated = row.copy().enabled(!row.isEnabled()).build();
            replace(updated);
            setDirty(true);
            refresh();
        });
        dataGrid.addColumn(enabledColumn, "Enabled", 80);
        dataGrid.addResizableColumn(
                new Column<NotificationConfig, String>(new TextCell()) {
                    @Override
                    public String getValue(final NotificationConfig row) {
                        if (row != null) {
                            return row.getDestinationType().getDisplayValue();
                        }
                        return null;
                    }
                }, "Type", ColumnSizeConstants.MEDIUM_COL);

        dataGrid.addResizableColumn(
                new Column<NotificationConfig, String>(new TextCell()) {
                    @Override
                    public String getValue(final NotificationConfig row) {
                        if (row.getDestination() instanceof NotificationStreamDestination) {
                            final NotificationStreamDestination analyticNotificationStreamDestination =
                                    (NotificationStreamDestination) row.getDestination();
                            return NullSafe.get(analyticNotificationStreamDestination.getDestinationFeed(),
                                    DocRef::getDisplayValue);
                        } else if (row.getDestination() instanceof NotificationEmailDestination) {
                            final NotificationEmailDestination notificationEmailDestination =
                                    (NotificationEmailDestination) row.getDestination();
                            return notificationEmailDestination.getTo();
                        }

                        return null;
                    }
                }, "Destination", ColumnSizeConstants.BIG_COL);

        // Limit notifications
        final Column<NotificationConfig, TickBoxState> limitColumn = new Column<NotificationConfig, TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue(final NotificationConfig row) {
                return TickBoxState.fromBoolean(row.isLimitNotifications());
            }
        };
        limitColumn.setFieldUpdater((index, row, value) -> {
            final NotificationConfig updated = row.copy().limitNotifications(!row.isLimitNotifications()).build();
            replace(updated);
            setDirty(true);
            refresh();
        });
        dataGrid.addColumn(limitColumn, "Limit", 80);

        // Max notifications
        final Column<NotificationConfig, String> maxColumn = new Column<NotificationConfig, String>(
                new TextCell()) {
            @Override
            public String getValue(final NotificationConfig row) {
                return "" + row.getMaxNotifications();
            }
        };
        dataGrid.addResizableColumn(maxColumn, "Max", ColumnSizeConstants.MEDIUM_COL);

        dataGrid.addEndColumn(new EndColumn<>());
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
        editButton.setEnabled(selectionModel.getSelectedItems().size() > 0);
        removeButton.setEnabled(selectionModel.getSelectedItems().size() > 0);
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
