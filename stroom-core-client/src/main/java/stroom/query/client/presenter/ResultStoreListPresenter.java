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

package stroom.query.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.preferences.client.DateTimeFormatter;
import stroom.query.api.DestroyReason;
import stroom.query.api.ResultStoreInfo;
import stroom.query.api.SearchRequestSource.SourceType;
import stroom.security.client.api.ClientSecurityContext;
import stroom.svg.client.SvgPresets;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef.DisplayType;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;

public class ResultStoreListPresenter extends MyPresenterWidget<PagerView> {

    private final MultiSelectionModelImpl<ResultStoreInfo> selectionModel;
    private final RestDataProvider<ResultStoreInfo, ResultPage<ResultStoreInfo>> dataProvider;
    private final ResultStoreModel resultStoreModel;
    private final ResultStoreSettingsPresenter resultStoreSettingsPresenter;

    private ButtonView terminateButton;
    private ButtonView deleteButton;
    private ButtonView settingsButton;

    @Inject
    public ResultStoreListPresenter(final EventBus eventBus,
                                    final PagerView view,
                                    final ResultStoreModel resultStoreModel,
                                    final DateTimeFormatter dateTimeFormatter,
                                    final ResultStoreSettingsPresenter resultStoreSettingsPresenter,
                                    final ClientSecurityContext securityContext) {
        super(eventBus, view);
        this.resultStoreModel = resultStoreModel;
        this.resultStoreSettingsPresenter = resultStoreSettingsPresenter;

        final MyDataGrid<ResultStoreInfo> dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);

        terminateButton = view.addButton(SvgPresets.STOP);
        terminateButton.setTitle("Terminate Search");
        terminateButton.setEnabled(false);

        deleteButton = view.addButton(SvgPresets.DELETE);
        deleteButton.setTitle("Delete Store");
        deleteButton.setEnabled(false);

        settingsButton = view.addButton(SvgPresets.SETTINGS_BLUE);
        settingsButton.setTitle("Store Settings");
        settingsButton.setEnabled(false);

        // User Id
        dataGrid.addResizableColumn(
                DataGridUtil.userRefColumnBuilder(
                                ResultStoreInfo::getOwner, getEventBus(), securityContext, DisplayType.AUTO)
                        .enabledWhen(taskProgress -> taskProgress.getOwner().isEnabled())
                        .build(),
                "User Display Name", 300);

        // Store size
        dataGrid.addResizableColumn(new Column<ResultStoreInfo, String>(new TextCell()) {
            @Override
            public String getValue(final ResultStoreInfo resultStoreInfo) {
                return ModelStringUtil.formatIECByteSizeString(resultStoreInfo.getStoreSize());
            }
        }, "Size", ColumnSizeConstants.SMALL_COL);

        // Age
        dataGrid.addResizableColumn(new Column<ResultStoreInfo, String>(new TextCell()) {
            @Override
            public String getValue(final ResultStoreInfo resultStoreInfo) {
                return ModelStringUtil
                        .formatDurationString(System.currentTimeMillis() - resultStoreInfo.getCreationTime());
            }
        }, "Age", ColumnSizeConstants.SMALL_COL);

        // Creation time
        dataGrid.addResizableColumn(new Column<ResultStoreInfo, String>(new TextCell()) {
            @Override
            public String getValue(final ResultStoreInfo resultStoreInfo) {
                return dateTimeFormatter.format(resultStoreInfo.getCreationTime());
            }
        }, "Creation Time", ColumnSizeConstants.DATE_COL);

        // Node name
        dataGrid.addResizableColumn(new Column<ResultStoreInfo, String>(new TextCell()) {
            @Override
            public String getValue(final ResultStoreInfo resultStoreInfo) {
                return resultStoreInfo.getNodeName();
            }
        }, "Node", ColumnSizeConstants.MEDIUM_COL);

        // Complete
        dataGrid.addResizableColumn(new Column<ResultStoreInfo, String>(new TextCell()) {
            @Override
            public String getValue(final ResultStoreInfo resultStoreInfo) {
                return Boolean.toString(resultStoreInfo.isComplete());
            }
        }, "Complete", ColumnSizeConstants.SMALL_COL);

        dataGrid.addEndColumn(new EndColumn<>());

        dataProvider =
                new RestDataProvider<ResultStoreInfo, ResultPage<ResultStoreInfo>>(getEventBus()) {
                    @Override
                    protected void exec(final Range range,
                                        final Consumer<ResultPage<ResultStoreInfo>> dataConsumer,
                                        final RestErrorHandler errorHandler) {
                        resultStoreModel.fetch(range, dataConsumer, errorHandler, getView());
                    }
                };
        dataProvider.addDataDisplay(dataGrid);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(terminateButton.addClickHandler(event -> {
            final ResultStoreInfo selected = getSelectionModel().getSelected();
            if (selected != null) {
                ConfirmEvent.fire(this, "Are you sure you want to terminate this search?", ok -> {
                    if (ok) {
                        resultStoreModel.terminate(selected.getNodeName(), selected.getQueryKey(), (done) -> {
                            if (done) {
                                AlertEvent.fireInfo(this, "Terminated", null);
                            } else {
                                AlertEvent.fireWarn(this, "Failed to terminate", null);
                            }
                            refresh();
                        }, getView());
                    }
                });
            }
        }));

        registerHandler(deleteButton.addClickHandler(event -> {
            final ResultStoreInfo selected = getSelectionModel().getSelected();
            if (selected != null) {
                ConfirmEvent.fire(this, "Are you sure you want to delete this result store?", ok -> {
                    if (ok) {
                        resultStoreModel.destroy(
                                selected.getNodeName(),
                                selected.getQueryKey(),
                                DestroyReason.MANUAL,
                                (done) -> {
                                    if (done) {
                                        AlertEvent.fireInfo(this, "Destroyed store", null);
                                    } else {
                                        AlertEvent.fireWarn(this, "Failed to destroy store", null);
                                    }
                                    refresh();
                                }, getView());
                    }
                });
            }
        }));

        registerHandler(settingsButton.addClickHandler(event -> {
            edit();
        }));

        registerHandler(getSelectionModel().addSelectionHandler(event -> {
            final ResultStoreInfo selected = getSelectionModel().getSelected();
            if (selected == null ||
                SourceType.TABLE_BUILDER_ANALYTIC.equals(selected.getSearchRequestSource().getSourceType())) {
                terminateButton.setEnabled(false);
                deleteButton.setEnabled(false);
                settingsButton.setEnabled(false);
            } else {
                terminateButton.setEnabled(!selected.isComplete());
                deleteButton.setEnabled(true);
                settingsButton.setEnabled(true);
            }
            if (event.getSelectionType().isDoubleSelect()) {
                edit();
            }
        }));
    }

    private void edit() {
        final ResultStoreInfo selected = getSelectionModel().getSelected();
        if (selected != null) {
            resultStoreSettingsPresenter.show(selected, "Change Result Store Settings", ok -> refresh());
        }
    }

    public void refresh() {
        dataProvider.refresh();
    }

    public MultiSelectionModel<ResultStoreInfo> getSelectionModel() {
        return selectionModel;
    }
}
