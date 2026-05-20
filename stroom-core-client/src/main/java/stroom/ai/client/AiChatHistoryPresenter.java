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

import stroom.ai.client.AiChatHistoryPresenter.AiChatHistoryView;
import stroom.ai.shared.AiChat;
import stroom.alert.client.event.ConfirmEvent;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DialogAction;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.List;
import java.util.function.Consumer;

public class AiChatHistoryPresenter
        extends MyPresenterWidget<AiChatHistoryView>
        implements AiChatHistoryUiHandlers {

    private final AskStroomAiClient askStroomAiClient;
    private Consumer<AiChat> selectionConsumer;

    @Inject
    public AiChatHistoryPresenter(final EventBus eventBus,
                                   final AiChatHistoryView view,
                                   final AskStroomAiClient askStroomAiClient) {
        super(eventBus, view);
        this.askStroomAiClient = askStroomAiClient;
        view.setUiHandlers(this);
    }

    public void show(final Consumer<AiChat> selectionConsumer) {
        this.selectionConsumer = selectionConsumer;

        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(500, 400))
                .caption("Chat History")
                .onShow(e -> refreshList())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final AiChat selected = getView().getSelected();
                        if (selected != null && selectionConsumer != null) {
                            selectionConsumer.accept(selected);
                        }
                    }
                    e.hide();
                })
                .fire();
    }

    private void refreshList() {
        askStroomAiClient.listChats(chats -> getView().setData(chats), this);
    }

    @Override
    public void onSelect(final AiChat chat) {
        getView().setSelected(chat);
    }

    @Override
    public void onDelete(final AiChat chat) {
        ConfirmEvent.fire(this,
                "Are you sure you want to delete \"" + chat.getTitle() + "\"?",
                ok -> {
                    if (ok) {
                        askStroomAiClient.deleteChat(chat.getId(), success -> refreshList(), this);
                    }
                });
    }

    @Override
    public void onOpen(final AiChat chat) {
        // Set the selection so the onHideRequest handler picks it up.
        getView().setSelected(chat);
        // Close the popup with OK action — the onHideRequest handler will invoke the selectionConsumer.
        HidePopupRequestEvent.builder(this).action(DialogAction.OK).fire();
    }

    public interface AiChatHistoryView extends View, HasUiHandlers<AiChatHistoryUiHandlers> {

        void setData(List<AiChat> chats);

        AiChat getSelected();

        void setSelected(AiChat chat);
    }
}
