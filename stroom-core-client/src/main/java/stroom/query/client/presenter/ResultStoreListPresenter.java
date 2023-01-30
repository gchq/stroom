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

package stroom.query.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.preferences.client.DateTimeFormatter;
import stroom.query.api.v2.DestroyReason;
import stroom.query.api.v2.Lifespan;
import stroom.query.api.v2.ResultStoreInfo;
import stroom.query.api.v2.ResultStoreSettings;
import stroom.query.api.v2.SearchTaskProgress;
import stroom.svg.client.SvgPresets;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
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

    private ButtonView terminateButton;
    private ButtonView deleteButton;

    @Inject
    public ResultStoreListPresenter(final EventBus eventBus,
                                    final PagerView view,
                                    final ResultStoreModel resultStoreModel,
                                    final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);
        this.resultStoreModel = resultStoreModel;

        final MyDataGrid<ResultStoreInfo> dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        view.setDataWidget(dataGrid);

        terminateButton = view.addButton(SvgPresets.STOP);
        terminateButton.setTitle("Terminate Search");
        terminateButton.setEnabled(false);

        deleteButton = view.addButton(SvgPresets.DELETE);
        deleteButton.setTitle("Delete Store");
        deleteButton.setEnabled(false);

        // Query key
        dataGrid.addResizableColumn(new Column<ResultStoreInfo, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final ResultStoreInfo resultStoreInfo) {
                final SafeHtmlBuilder sb = new SafeHtmlBuilder();
                sb.appendHtmlConstant("<b>UUID: </b>");
                sb.appendEscaped(resultStoreInfo.getQueryKey().getUuid());
                sb.appendHtmlConstant("<br/>");
                sb.appendHtmlConstant("<b>User Id: </b>");
                sb.appendEscaped(resultStoreInfo.getUserId());
                sb.appendHtmlConstant("<br/>");
                sb.appendHtmlConstant("<b>Creation Time: </b>");
                sb.appendEscaped(dateTimeFormatter.format(resultStoreInfo.getCreationTime()));
                sb.appendHtmlConstant("<br/>");
                sb.appendHtmlConstant("<b>Age: </b>");
                sb.appendEscaped(ModelStringUtil
                        .formatDurationString(System.currentTimeMillis() - resultStoreInfo.getCreationTime()));
                sb.appendHtmlConstant("<br/>");
                sb.appendHtmlConstant("<b>Node Name: </b>");
                sb.appendEscaped(resultStoreInfo.getNodeName());
                sb.appendHtmlConstant("<br/>");
                sb.appendHtmlConstant("<b>Store Size: </b>");
                sb.appendEscaped(ModelStringUtil.formatIECByteSizeString(resultStoreInfo.getStoreSize()));
                sb.appendHtmlConstant("<br/>");
                sb.appendHtmlConstant("<b>Complete: </b>");
                sb.appendEscaped(Boolean.toString(resultStoreInfo.isComplete()));
                sb.appendHtmlConstant("<br/>");

                final ResultStoreSettings resultStoreSettings = resultStoreInfo.getResultStoreSettings();
                addLifespan(sb, resultStoreSettings.getStoreLifespan(), "Store");
                addLifespan(sb, resultStoreSettings.getSearchProcessLifespan(), "Search process");

                final SearchTaskProgress taskProgress = resultStoreInfo.getTaskProgress();
                if (taskProgress != null) {
                    sb.appendHtmlConstant("<b>Task Info: </b>");
                    sb.appendEscaped(taskProgress.getTaskInfo());
                    sb.appendHtmlConstant("<br/>");
                }

                return sb.toSafeHtml();
            }
        }, "Store", 1000);

//
//        // Query key
//        dataGrid.addResizableColumn(new Column<ResultStoreInfo, String>(new TextCell()) {
//            @Override
//            public String getValue(final ResultStoreInfo resultStoreInfo) {
//                return resultStoreInfo.getQueryKey().getUuid();
//            }
//        }, "UUID", 250);
//
        // User Id
        dataGrid.addResizableColumn(new Column<ResultStoreInfo, String>(new TextCell()) {
            @Override
            public String getValue(final ResultStoreInfo resultStoreInfo) {
                return resultStoreInfo.getUserId();
            }
        }, "User Id", 250);

        // Creation time
        dataGrid.addResizableColumn(new Column<ResultStoreInfo, String>(new TextCell()) {
            @Override
            public String getValue(final ResultStoreInfo resultStoreInfo) {
                return dateTimeFormatter.format(resultStoreInfo.getCreationTime());
            }
        }, "Creation Time", 250);

        // Age
        dataGrid.addResizableColumn(new Column<ResultStoreInfo, String>(new TextCell()) {
            @Override
            public String getValue(final ResultStoreInfo resultStoreInfo) {
                return ModelStringUtil
                        .formatDurationString(System.currentTimeMillis() - resultStoreInfo.getCreationTime());
            }
        }, "Age", 250);

//
//        // Node name
//        dataGrid.addResizableColumn(new Column<ResultStoreInfo, String>(new TextCell()) {
//            @Override
//            public String getValue(final ResultStoreInfo resultStoreInfo) {
//                return resultStoreInfo.getNodeName();
//            }
//        }, "Node Name", 250);
//
//        // Store size
//        dataGrid.addResizableColumn(new Column<ResultStoreInfo, String>(new TextCell()) {
//            @Override
//            public String getValue(final ResultStoreInfo resultStoreInfo) {
//                return ModelStringUtil.formatIECByteSizeString(resultStoreInfo.getStoreSize());
//            }
//        }, "Store Size", 250);
//
//        // Complete
//        dataGrid.addResizableColumn(new Column<ResultStoreInfo, String>(new TextCell()) {
//            @Override
//            public String getValue(final ResultStoreInfo resultStoreInfo) {
//                return Boolean.toString(resultStoreInfo.isComplete());
//            }
//        }, "Complete", 250);
//
//        // Task Info
//        dataGrid.addResizableColumn(new Column<ResultStoreInfo, String>(new TextCell()) {
//            @Override
//            public String getValue(final ResultStoreInfo resultStoreInfo) {
//                final SearchTaskProgress taskProgress = resultStoreInfo.getTaskProgress();
//                if (taskProgress != null) {
//                    return taskProgress.getTaskInfo();
//                }
//                return null;
//            }
//        }, "Task Info", 250);

        dataGrid.addEndColumn(new EndColumn<>());

        dataProvider =
                new RestDataProvider<ResultStoreInfo, ResultPage<ResultStoreInfo>>(getEventBus()) {
                    @Override
                    protected void exec(final Range range,
                                        final Consumer<ResultPage<ResultStoreInfo>> dataConsumer,
                                        final Consumer<Throwable> throwableConsumer) {
                        resultStoreModel.fetch(range, dataConsumer, throwableConsumer);
                    }
                };
        dataProvider.addDataDisplay(dataGrid);
    }

    private void addLifespan(final SafeHtmlBuilder sb, final Lifespan lifespan, final String type) {
        sb.appendHtmlConstant("<b>" + type + " TTL: </b>");
        sb.appendEscaped(ModelStringUtil
                .formatDurationString(lifespan.getTimeToLiveMs()));
        sb.appendHtmlConstant("<br/>");
        sb.appendHtmlConstant("<b>" + type + " TTI: </b>");
        sb.appendEscaped(ModelStringUtil
                .formatDurationString(lifespan.getTimeToIdleMs()));
        sb.appendHtmlConstant("<br/>");
        sb.appendHtmlConstant("<b>" + type + " destroy on tab close: </b>");
        sb.appendEscaped(Boolean.toString(lifespan.isDestroyOnTabClose()));
        sb.appendHtmlConstant("<br/>");
        sb.appendHtmlConstant("<b>" + type + " destroy on window close: </b>");
        sb.appendEscaped(Boolean.toString(lifespan.isDestroyOnWindowClose()));
        sb.appendHtmlConstant("<br/>");
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
                        });
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
                                });
                    }
                });
            }
        }));

        registerHandler(getSelectionModel().addSelectionHandler(event -> {
            final ResultStoreInfo selected = getSelectionModel().getSelected();
            if (selected == null) {
                terminateButton.setEnabled(false);
                deleteButton.setEnabled(false);
            } else {
                terminateButton.setEnabled(!selected.isComplete());
                deleteButton.setEnabled(true);
            }
        }));
    }

    public void refresh() {
        dataProvider.refresh();
    }

//    public void show() {
//        dataProvider.refresh();
//
//        final PopupSize popupSize = PopupSize.resizable(1000, 600);
//        ShowPopupEvent.builder(this)
//                .popupType(PopupType.OK_CANCEL_DIALOG)
//                .popupSize(popupSize)
//                .caption("Search Result Stores")
////                .onShow(e -> getView().focus())
////                .onHide(e -> {
////                    if (e.isOk() && groupConsumer != null) {
////                        final User selected = getSelectionModel().getSelected();
////                        if (selected != null) {
////                            groupConsumer.accept(selected);
////                        }
////                    }
////                })
//                .fire();
//    }

    public MultiSelectionModel<ResultStoreInfo> getSelectionModel() {
        return selectionModel;
    }
}
