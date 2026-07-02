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

package stroom.ai.client;

import stroom.ai.shared.GetAttachmentDataRequest;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.List;
import java.util.function.Consumer;

/**
 * Displays attachment table data in a pageable data grid inside a popup dialog.
 * Columns are dynamically created from the markdown table header row returned by
 * the server. Uses {@link MyDataGrid} for fixed headers and column resizing.
 */
public class AiAttachmentDataPresenter extends MyPresenterWidget<PagerView> {

    private final AskStroomAiClient askStroomAiClient;
    private final MyDataGrid<List<String>> dataGrid;
    private RestDataProvider<List<String>, ResultPage<List<String>>> dataProvider;

    private int chatId;
    private int attachmentId;
    private boolean columnsInitialised;

    @Inject
    public AiAttachmentDataPresenter(final EventBus eventBus,
                                     final PagerView view,
                                     final AskStroomAiClient askStroomAiClient) {
        super(eventBus, view);
        this.askStroomAiClient = askStroomAiClient;

        dataGrid = new MyDataGrid<>(this);
        view.setDataWidget(dataGrid);
    }

    /**
     * Configure the presenter for a specific attachment and show as a popup.
     */
    public void show(final int chatId, final int attachmentId, final String description) {
        this.chatId = chatId;
        this.attachmentId = attachmentId;
        this.columnsInitialised = false;

        // Remove any columns from a previous display.
        while (dataGrid.getColumnCount() > 0) {
            dataGrid.removeColumn(0);
        }

        // Create a new data provider for this attachment.
        if (dataProvider != null) {
            dataProvider.removeDataDisplay(dataGrid);
        }
        dataProvider = new RestDataProvider<List<String>, ResultPage<List<String>>>(getEventBus()) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<List<String>>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
                final GetAttachmentDataRequest request = new GetAttachmentDataRequest(
                        AiAttachmentDataPresenter.this.chatId,
                        AiAttachmentDataPresenter.this.attachmentId,
                        pageRequest);
                askStroomAiClient.getAttachmentData(request, dataPage -> {
                    // Create columns on first load.
                    if (!columnsInitialised && dataPage.getHeaders() != null) {
                        initColumns(dataPage.getHeaders());
                        columnsInitialised = true;
                    }

                    // Wrap the response as a ResultPage for the data provider.
                    final PageResponse pageResponse = new PageResponse(
                            dataPage.getOffset(),
                            dataPage.getRows().size(),
                            (long) dataPage.getTotalRowCount(),
                            true);
                    final ResultPage<List<String>> resultPage = new ResultPage<>(
                            dataPage.getRows(), pageResponse);

                    if (resultPage.getPageStart() != dataGrid.getPageStart()) {
                        dataGrid.setPageStart(resultPage.getPageStart());
                    }
                    dataConsumer.accept(resultPage);
                }, errorHandler, getView());
            }
        };
        dataProvider.addDataDisplay(dataGrid);

        // Add standard dialog border/background styling to the pager view.
        getView().asWidget().addStyleName("form-control-border form-control-background");

        // Show as popup.
        final String caption = description != null && !description.isBlank()
                ? description
                : "Attachment Data";
        ShowPopupEvent.builder(this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(PopupSize.resizable(900, 600))
                .caption(caption)
                .fire();
    }

    private void initColumns(final List<String> headers) {
        for (int i = 0; i < headers.size(); i++) {
            final int colIndex = i;
            final Column<List<String>, String> column =
                    new Column<List<String>, String>(new TextCell()) {
                        @Override
                        public String getValue(final List<String> row) {
                            if (row != null && colIndex < row.size()) {
                                return row.get(colIndex);
                            }
                            return "";
                        }
                    };
            dataGrid.addColumn(column, headers.get(colIndex));
        }
    }
}
