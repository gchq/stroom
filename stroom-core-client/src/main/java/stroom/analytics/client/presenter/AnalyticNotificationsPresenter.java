/*
 * Copyright 2022 Crown Copyright
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
 *
 */

package stroom.analytics.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.analytics.shared.AnalyticNotification;
import stroom.analytics.shared.AnalyticNotificationResource;
import stroom.analytics.shared.AnalyticNotificationRow;
import stroom.analytics.shared.AnalyticNotificationState;
import stroom.analytics.shared.AnalyticNotificationStreamConfig;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.FindAnalyticNotificationCriteria;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.ResultPage;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.button.client.ButtonView;
import stroom.widget.customdatebox.client.ClientDateUtil;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.CheckboxCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Optional;
import java.util.function.Consumer;

public class AnalyticNotificationsPresenter
        extends DocumentEditPresenter<PagerView, AnalyticRuleDoc>
        implements DirtyUiHandlers {

    private static final AnalyticNotificationResource ANALYTIC_NOTIFICATION_RESOURCE =
            GWT.create(AnalyticNotificationResource.class);

    private final MyDataGrid<AnalyticNotificationRow> dataGrid;
    private final MultiSelectionModelImpl<AnalyticNotificationRow> selectionModel;
    private final RestFactory restFactory;
    private final Provider<AnalyticNotificationEditPresenter> editProvider;
    private final ButtonView newButton;
    private final ButtonView openButton;
    private final ButtonView deleteButton;
    private final FindAnalyticNotificationCriteria criteria;
    private final RestDataProvider<AnalyticNotificationRow, ResultPage<AnalyticNotificationRow>> dataProvider;
    private boolean initialised;

    @Inject
    public AnalyticNotificationsPresenter(final EventBus eventBus,
                                          final PagerView view,
                                          final RestFactory restFactory,
                                          final Provider<AnalyticNotificationEditPresenter> editProvider) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.editProvider = editProvider;

        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        view.setDataWidget(dataGrid);

        // Add a border to the list.
        getWidget().getElement().addClassName("stroom-border");

        newButton = view.addButton(SvgPresets.NEW_ITEM);
        openButton = view.addButton(SvgPresets.EDIT);
        deleteButton = view.addButton(SvgPresets.DELETE);

        initTableColumns();

        criteria = new FindAnalyticNotificationCriteria();
        dataProvider = new RestDataProvider<AnalyticNotificationRow, ResultPage<AnalyticNotificationRow>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<AnalyticNotificationRow>> dataConsumer,
                                final Consumer<Throwable> throwableConsumer) {
                if (criteria.getAnalyticDocUuid() != null) {
                    CriteriaUtil.setRange(criteria, range);
                    final Rest<ResultPage<AnalyticNotificationRow>> rest = restFactory.create();
                    rest
                            .onSuccess(dataConsumer)
                            .onFailure(throwableConsumer)
                            .call(ANALYTIC_NOTIFICATION_RESOURCE)
                            .find(criteria);
                }
            }
        };
    }

    private void enableButtons() {
        final AnalyticNotificationRow selected = selectionModel.getSelected();
        final boolean enabled = !isReadOnly() && selected != null;
        newButton.setEnabled(!isReadOnly());
        openButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
    }

    @Override
    protected void onBind() {
        registerHandler(selectionModel.addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                onEdit(selectionModel.getSelected());
            }
        }));
        if (newButton != null) {
            registerHandler(newButton.addClickHandler(event -> {
                if (MouseUtil.isPrimary(event)) {
                    onNew();
                }
            }));
        }
        if (openButton != null) {
            registerHandler(openButton.addClickHandler(event -> {
                if (MouseUtil.isPrimary(event)) {
                    onEdit(selectionModel.getSelected());
                }
            }));
        }
        if (deleteButton != null) {
            registerHandler(deleteButton.addClickHandler(event -> {
                if (MouseUtil.isPrimary(event)) {
                    onDelete();
                }
            }));
        }

        super.onBind();
    }

    private void initTableColumns() {
        // Enabled.
        final Column<AnalyticNotificationRow, Boolean> enabledColumn = new Column<AnalyticNotificationRow, Boolean>(new CheckboxCell()) {
            @Override
            public Boolean getValue(final AnalyticNotificationRow row) {
                return Optional.ofNullable(row)
                        .map(AnalyticNotificationRow::getAnalyticNotification)
                        .map(AnalyticNotification::isEnabled)
                        .orElse(null);
            }
        };
//        enabledColumn.setFieldUpdater((index, row, value) -> {
//            row.
//            row.vgetMask().setRollUpState(fieldPositionNumber, value);
//
//            DirtyEvent.fire(StroomStatsStoreCustomMaskListPresenter.this, true);
//        });

        dataGrid.addResizableColumn(enabledColumn, "Enabled", 100);


        // Time to wait for data.
        final Column<AnalyticNotificationRow, String> delayColumn = new Column<AnalyticNotificationRow, String>(new TextCell()) {
            @Override
            public String getValue(final AnalyticNotificationRow row) {
                return Optional.ofNullable(row)
                        .map(AnalyticNotificationRow::getAnalyticNotification)
                        .map(AnalyticNotification::getConfig)
                        .map(config -> {
                            if (config instanceof AnalyticNotificationStreamConfig) {
                                return (AnalyticNotificationStreamConfig) config;
                            }
                            return null;
                        })
                        .map(AnalyticNotificationStreamConfig::getTimeToWaitForData)
                        .map(timeToWaitForData ->
                                timeToWaitForData.getTime() +
                                        " " +
                                        timeToWaitForData.getTimeUnit().getDisplayValue())
                        .orElse(null);
            }
        };
        dataGrid.addResizableColumn(delayColumn, "Time To Wait", 300);

        // Feed.
        final Column<AnalyticNotificationRow, String> feedColumn = new Column<AnalyticNotificationRow, String>(new TextCell()) {
            @Override
            public String getValue(final AnalyticNotificationRow row) {
                return Optional.ofNullable(row)
                        .map(AnalyticNotificationRow::getAnalyticNotification)
                        .map(AnalyticNotification::getConfig)
                        .map(config -> {
                            if (config instanceof AnalyticNotificationStreamConfig) {
                                return (AnalyticNotificationStreamConfig) config;
                            }
                            return null;
                        })
                        .map(AnalyticNotificationStreamConfig::getDestinationFeed)
                        .map(DocRef::getName)
                        .orElse(null);
            }
        };
        dataGrid.addResizableColumn(feedColumn, "Feed", 300);

        // Message.
        final Column<AnalyticNotificationRow, String> messageColumn = new Column<AnalyticNotificationRow, String>(new TextCell()) {
            @Override
            public String getValue(final AnalyticNotificationRow row) {
                return Optional.ofNullable(row)
                        .map(AnalyticNotificationRow::getAnalyticNotificationState)
                        .map(AnalyticNotificationState::getMessage)
                        .orElse(null);
            }
        };
        dataGrid.addResizableColumn(messageColumn, "Message", 300);

        // Last Execution.
        final Column<AnalyticNotificationRow, String> lastExecutionColumn = new Column<AnalyticNotificationRow, String>(
                new TextCell()) {
            @Override
            public String getValue(final AnalyticNotificationRow row) {
                return Optional.ofNullable(row)
                        .map(AnalyticNotificationRow::getAnalyticNotificationState)
                        .map(AnalyticNotificationState::getLastExecutionTime)
                        .map(ClientDateUtil::toDateString)
                        .orElse(null);
            }
        };
        dataGrid.addResizableColumn(lastExecutionColumn, "Last Execution", 300);

        dataGrid.addEndColumn(new EndColumn<>());
    }

    @Override
    protected void onRead(final DocRef docRef, final AnalyticRuleDoc doc, final boolean readOnly) {
        criteria.setAnalyticDocUuid(doc.getUuid());
        refresh();
    }

    public void refresh() {
        if (!initialised) {
            initialised = true;
            dataProvider.addDataDisplay(dataGrid);
        } else {
            dataProvider.refresh();
        }
    }

    @Override
    protected AnalyticRuleDoc onWrite(final AnalyticRuleDoc alertRule) {
        return alertRule;
    }

    private void onNew() {
        final AnalyticNotificationEditPresenter editor = editProvider.get();
        final AnalyticNotificationStreamConfig config = AnalyticNotificationStreamConfig
                .builder()
                .timeToWaitForData(SimpleDuration.builder().time(1).timeUnit(TimeUnit.HOURS).build())
                .useSourceFeedIfPossible(true)
                .build();
        final AnalyticNotification newNotification = AnalyticNotification
                .builder()
                .analyticUuid(criteria.getAnalyticDocUuid())
                .config(config)
                .build();
        editor.show(newNotification, notification -> {
            refresh();
//            selectionModel.setSelected(notification);
            setDirty(true);
        }, true);
    }

    private void onEdit(final AnalyticNotificationRow row) {
        if (row != null) {
            final AnalyticNotificationEditPresenter editor = editProvider.get();
            editor.show(row.getAnalyticNotification(), notification -> {
                refresh();
//                selectionModel.setSelected(row);
                setDirty(true);
            }, false);
        }
    }

    private void onDelete() {
        final AnalyticNotificationRow selected = selectionModel.getSelected();
        if (selected != null) {
            ConfirmEvent.fire(this, "Are you sure you want to delete the selected notification?",
                    result -> {
                        if (result) {
                            final Rest<AnalyticNotification> rest = restFactory.create();
                            rest
                                    .onSuccess(r -> {
                                        refresh();
                                        setDirty(true);
                                    })
                                    .call(ANALYTIC_NOTIFICATION_RESOURCE)
                                    .delete(selected.getAnalyticNotification().getUuid(),
                                            selected.getAnalyticNotification());
                        }
                    });
        }
    }

    @Override
    public void onDirty() {
        setDirty(true);
    }

    @Override
    public String getType() {
        return AnalyticRuleDoc.DOCUMENT_TYPE;
    }
}
