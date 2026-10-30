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

package stroom.ai.client.component;

import stroom.ai.client.AskStroomAiClient;
import stroom.ai.shared.GetAttachmentDataRequest;
import stroom.dispatch.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import java.util.function.Consumer;

/// An Ask Stroom AI attachment's table ([AiAttachmentData]) in a dialog made for one use, its
/// columns made from the headings the server gives with the first page.
public class AiAttachmentDataController extends Controller {

    private final AiAttachmentData view;
    private final AskStroomAiClient askStroomAiClient;

    private boolean columnsInitialised;

    /// Made by GIN, with its table.
    ///
    /// @param eventBus          The application's event bus.
    /// @param view              The table.
    /// @param askStroomAiClient Fetches the table's rows.
    @Inject
    public AiAttachmentDataController(final EventBus eventBus,
                                      final AiAttachmentData view,
                                      final AskStroomAiClient askStroomAiClient) {
        super(eventBus);
        this.view = view;
        this.askStroomAiClient = askStroomAiClient;
        initWidget(view);
    }

    /// Shows an attachment's table in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener       What shows it.
    /// @param chatId       The chat the attachment is in.
    /// @param attachmentId The attachment.
    /// @param description  What the attachment is, the dialog's caption, or null for a general one.
    public void show(final HasControllerNode opener,
                     final int chatId,
                     final int attachmentId,
                     final String description) {
        view.setDataProvider(new RestDataProvider<List<String>, ResultPage<List<String>>>(getEventBus()) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<List<String>>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                fetch(chatId, attachmentId, range, dataConsumer, errorHandler);
            }
        });

        final String caption = description != null && !description.isBlank()
                ? description
                : "Attachment Data";
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.CLOSE_DIALOG)
                .popupSize(PopupSize.resizable(900, 600))
                .caption(caption)
                .fire();
    }

    private void fetch(final int chatId,
                       final int attachmentId,
                       final Range range,
                       final Consumer<ResultPage<List<String>>> dataConsumer,
                       final RestErrorHandler errorHandler) {
        final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
        final GetAttachmentDataRequest request = new GetAttachmentDataRequest(chatId, attachmentId, pageRequest);
        askStroomAiClient.getAttachmentData(this, request, dataPage -> {
            // Create columns on first load.
            if (!columnsInitialised && dataPage.getHeaders() != null) {
                view.addColumns(dataPage.getHeaders());
                columnsInitialised = true;
            }

            // Wrap the response as a ResultPage for the data provider.
            final PageResponse pageResponse = new PageResponse(
                    (long) dataPage.getOffset(),
                    dataPage.getRows().size(),
                    (long) dataPage.getTotalRowCount(),
                    true);
            final ResultPage<List<String>> resultPage = new ResultPage<>(dataPage.getRows(), pageResponse);
            view.showPageStartingAt(resultPage.getPageStart());
            dataConsumer.accept(resultPage);
        }, errorHandler, view.getTaskMonitor());
    }
}
