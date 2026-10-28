/*
 * Copyright 2023 Crown Copyright
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

package stroom.query.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.data.client.RestDataProvider;
import stroom.data.client.component.RefCellsController;
import stroom.data.client.component.UserRefCell;
import stroom.dispatch.client.RestErrorHandler;
import stroom.preferences.client.DateTimeFormatter;
import stroom.query.api.DestroyReason;
import stroom.query.api.ResultStoreInfo;
import stroom.query.client.ResultStoreModel;
import stroom.util.shared.ResultPage;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The search result stores ([ResultStoreList]): fetched from every node; a store can be
/// terminated, deleted or its settings changed.
public class ResultStoreListController extends Controller {

    private final ResultStoreList view;
    private final RestDataProvider<ResultStoreInfo, ResultPage<ResultStoreInfo>> dataProvider;
    private final ResultStoreModel resultStoreModel;
    private final Provider<ResultStoreSettingsEditorController> resultStoreSettingsProvider;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                     The application's event bus.
    /// @param view                         The content.
    /// @param resultStoreModel             Fetches, terminates and deletes the stores.
    /// @param dateTimeFormatter            Formats times in the user's preferences.
    /// @param resultStoreSettingsProvider  Makes the dialog that changes a store's settings.
    /// @param refCells                     Lets the owner cells open their users.
    @Inject
    public ResultStoreListController(final EventBus eventBus,
                                     final ResultStoreList view,
                                     final ResultStoreModel resultStoreModel,
                                     final DateTimeFormatter dateTimeFormatter,
                                     final Provider<ResultStoreSettingsEditorController> resultStoreSettingsProvider,
                                     final RefCellsController refCells) {
        super(eventBus);
        this.view = view;
        own(refCells);
        this.resultStoreModel = resultStoreModel;
        this.resultStoreSettingsProvider = resultStoreSettingsProvider;
        initWidget(view);

        view.addColumns(dateTimeFormatter::format, refCells.configure(new UserRefCell.Builder<ResultStoreInfo>()));
        view.setOnTerminate(this::onTerminate);
        view.setOnDelete(this::onDelete);
        view.setOnSettings(this::edit);

        dataProvider =
                new RestDataProvider<ResultStoreInfo, ResultPage<ResultStoreInfo>>(getEventBus()) {
                    @Override
                    protected void exec(final Range range,
                                        final Consumer<ResultPage<ResultStoreInfo>> dataConsumer,
                                        final RestErrorHandler errorHandler) {
                        resultStoreModel.fetch(range, dataConsumer, errorHandler, view.getTaskMonitor());
                    }
                };
        view.setDataProvider(dataProvider);
    }

    private void onTerminate() {
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
                    }, view.getTaskMonitor());
                }
            });
        }
    }

    private void onDelete() {
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
                            }, view.getTaskMonitor());
                }
            });
        }
    }

    private void edit() {
        final ResultStoreInfo selected = getSelectionModel().getSelected();
        if (selected != null) {
            resultStoreSettingsProvider.get()
                    .show(this, selected, "Change Result Store Settings", ok -> refresh());
        }
    }

    /// Fetches the stores again.
    public void refresh() {
        dataProvider.refresh();
    }

    /// @return The selection, one store at most.
    public MultiSelectionModel<ResultStoreInfo> getSelectionModel() {
        return view.getSelectionModel();
    }
}
