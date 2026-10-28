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

import stroom.ai.shared.AiChat;
import stroom.ai.shared.FindAiChatHistoryCriteria;
import stroom.explorer.client.component.FindPanel;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.DialogAction;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// Chat History ([FindPanel] over an [AiChatHistoryResultList]): the user's Ask Stroom AI chats,
/// filtered by name, in a dialog made for one use; OK, Enter or a double click opens the one chosen.
public class AiChatHistoryController extends Controller {

    private final FindPanel view;
    private final AiChatHistoryResultListController resultList;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                         The application's event bus.
    /// @param view                             The content.
    /// @param resultList                       Finds the chats, and lists them.
    /// @param quickFilterContextHandlerFactory Makes the filter's recent filters.
    @Inject
    public AiChatHistoryController(final EventBus eventBus,
                                   final FindPanel view,
                                   final AiChatHistoryResultListController resultList,
                                   final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus);
        this.view = view;
        this.resultList = own(resultList);
        initWidget(view);

        // Named for screen readers, as its placeholder is only a hint, lost once something is typed
        view.setFilterGuidance("Filter chats", null);
        view.setResultView(resultList);
        view.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                FindAiChatHistoryCriteria.QUICK_FILTER_CONTEXT, this, () -> taskMonitor().createTaskMonitor()));
        view.setOnFilterChange(this::changeQuickFilter);
        view.setOnFilterKeyDown(this::onFilterKeyDown);
        resultList.setOnFilterError(view::setFilterError);
        resultList.setOnOpen(this::openChat);
        resultList.setOnFocusFilter(view::focus);
    }

    /// Shows the chats in a dialog, owned by `opener` until it is closed.
    ///
    /// @param opener            What shows it.
    /// @param selectionConsumer Given the chat chosen, on OK.
    public void show(final HasControllerNode opener, final Consumer<AiChat> selectionConsumer) {
        resultList.refresh();
        ControllerDialog.showOnce(opener, this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(500, 400))
                .caption("Chat History")
                .onShow(e -> view.focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final AiChat selected = resultList.getSelected();
                        if (selected != null && selectionConsumer != null) {
                            selectionConsumer.accept(selected);
                        }
                    }
                    e.hide();
                })
                .fire();
    }

    private void openChat(final AiChat match) {
        if (match != null) {
            ControllerDialog.pressAction(this, DialogAction.OK);
        }
    }

    private void changeQuickFilter(final String name) {
        if (resultList.setFilter(name)) {
            resultList.refresh();
        }
    }

    private void onFilterKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            openChat(resultList.getSelected());
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_DOWN) {
            resultList.setKeyboardSelectedRow(0, true);
        }
    }

}
