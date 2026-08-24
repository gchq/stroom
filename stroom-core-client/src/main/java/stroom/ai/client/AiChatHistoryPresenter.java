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
import stroom.explorer.client.presenter.FindDocResultListHandler;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DialogAction;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class AiChatHistoryPresenter
        extends MyPresenterWidget<AiChatHistoryView>
        implements AiChatHistoryUiHandlers, FindDocResultListHandler<AiChat> {


    private final AiChatHistoryResultListPresenter findResultListPresenter;
    private boolean showing;
    private Consumer<AiChat> selectionConsumer;


    @Inject
    public AiChatHistoryPresenter(final EventBus eventBus,
                         final AiChatHistoryView view,
                         final AiChatHistoryResultListPresenter findResultListPresenter) {
        super(eventBus, view);
        this.findResultListPresenter = findResultListPresenter;

        // Ensure list has a border in the popup view.
        findResultListPresenter.getView().asWidget().addStyleName("form-control-border form-control-background");
        view.setResultView(findResultListPresenter.getView());
        view.setUiHandlers(this);
        findResultListPresenter.setFindResultListHandler(this);
    }

    @Override
    public void openDocument(final AiChat match) {
        if (match != null) {
            HidePopupRequestEvent.builder(this).action(DialogAction.OK).fire();
        }
    }

    @Override
    public void focus() {
        getView().focus();
    }

    @Override
    public void changeQuickFilter(final String name) {
        if (findResultListPresenter.setFilter(name)) {
            refresh();
        }
    }

    @Override
    public void onFilterKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            openDocument(findResultListPresenter.getSelected());
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_DOWN) {
            findResultListPresenter.setKeyboardSelectedRow(0, true);
        }
    }

    public void refresh() {
        findResultListPresenter.refresh();
    }

    private void hide() {
        HidePopupRequestEvent.builder(this).fire();
    }
























//    @ProxyEvent
//    @Override
//    public void onShow(final ShowFindEvent event) {
//        if (!showing) {
//            showing = true;
//
//            // Make sure we are set to focus text next time we show.
//            getFindResultListPresenter().setFocusText(true);
//
//            final ExplorerTreeFilter.Builder explorerTreeFilterBuilder =
//                    getFindResultListPresenter().getExplorerTreeFilterBuilder();
//            // Don't want favourites in the recent items as they are effectively duplicates
//            explorerTreeFilterBuilder.includedRootTypes(ExplorerConstants.SYSTEM_TYPE);
//            explorerTreeFilterBuilder.setNameFilter(
//                    explorerTreeFilterBuilder.build().getNameFilter(),
//                    true);
//
//            // Refresh the results.
//            refresh();
//
//            final PopupSize popupSize = PopupSize.resizable(800, 600);
//            ShowPopupEvent.builder(this)
//                    .popupType(PopupType.CLOSE_DIALOG)
//                    .popupSize(popupSize)
//                    .caption("Chat History")
//                    .onShow(e -> getView().focus())
//                    .onHideRequest(HidePopupRequestEvent::hide)
//                    .onHide(e -> showing = false)
//                    .fire();
//        }
//    }

//    private final AskStroomAiClient askStroomAiClient;
//    private Consumer<AiChat> selectionConsumer;
//
//    @Inject
//    public AiChatHistoryPresenter(final EventBus eventBus,
//                                   final AiChatHistoryView view,
//                                   final AskStroomAiClient askStroomAiClient) {
//        super(eventBus, view);
//        this.askStroomAiClient = askStroomAiClient;
//        view.setUiHandlers(this);
//    }
//
    public void show(final Consumer<AiChat> selectionConsumer) {
        this.selectionConsumer = selectionConsumer;

        // Refresh the results.
        refresh();

        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(500, 400))
                .caption("Chat History")
                .onShow(e -> getView().focus())
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final AiChat selected = findResultListPresenter.getSelected();
                        if (selected != null && selectionConsumer != null) {
                            selectionConsumer.accept(selected);
                        }
                    }
                    e.hide();
                })
                .fire();
    }
//
//    private void refreshList() {
//        askStroomAiClient.listChats(chats -> getView().setData(chats), this);
//    }
//
//    @Override
//    public void onSelect(final AiChat chat) {
//        getView().setSelected(chat);
//    }
//
//    @Override
//    public void onDelete(final AiChat chat) {
//        ConfirmEvent.fire(this,
//                "Are you sure you want to delete \"" + chat.getTitle() + "\"?",
//                ok -> {
//                    if (ok) {
//                        askStroomAiClient.deleteChat(chat.getId(), success -> refreshList(), this);
//                    }
//                });
//    }
//
//    @Override
//    public void onOpen(final AiChat chat) {
//        // Set the selection so the onHideRequest handler picks it up.
//        getView().setSelected(chat);
//        // Close the popup with OK action — the onHideRequest handler will invoke the selectionConsumer.
//        HidePopupRequestEvent.builder(this).action(DialogAction.OK).fire();
//    }
//

    public interface AiChatHistoryView extends View, Focus, HasUiHandlers<AiChatHistoryUiHandlers> {

//        void setData(List<AiChat> chats);
//
//        AiChat getSelected();
//
//        void setSelected(AiChat chat);

        void setResultView(View view);
    }
//
//
//

















    //    private final Widget widget;
//    private List<AiChat> allChats = List.of();
//
//    @UiField
//    TextBox searchBox;
//    @UiField
//    ScrollPanel tableContainer;
//
//    private final CellTable<AiChat> cellTable;
//    private final MySingleSelectionModel<AiChat> selectionModel = new MySingleSelectionModel<>();
//
//    @Inject
//    public AiChatHistoryViewImpl(final Binder binder) {
//        widget = binder.createAndBindUi(this);
//
//        searchBox.getElement().setAttribute("placeholder", "Search conversations");
//
//        cellTable = new MyCellTable<>(Integer.MAX_VALUE);
//        cellTable.setSelectionModel(selectionModel, new BasicSelectionEventManager<AiChat>(cellTable) {
//            @Override
//            protected void onExecute(final CellPreviewEvent<AiChat> e) {
//                final AiChat chat = e.getValue();
//                selectionModel.setSelected(chat, true);
//                if (getUiHandlers() != null) {
//                    getUiHandlers().onOpen(chat);
//                }
//            }
//        });
//
//        // Title column.
//        final Column<AiChat, String> titleColumn = new Column<AiChat, String>(new TextCell()) {
//            @Override
//            public String getValue(final AiChat chat) {
//                return chat.getTitle();
//            }
//        };
//        cellTable.addColumn(titleColumn, "Title");
//        cellTable.setColumnWidth(titleColumn, "100%");
//
//        // Last updated column.
//        final Column<AiChat, String> dateColumn = new Column<AiChat, String>(new TextCell()) {
//            @Override
//            public String getValue(final AiChat chat) {
//                return formatRelativeTime(chat.getUpdateTimeMs());
//            }
//        };
//        cellTable.addColumn(dateColumn, "Last Active");
//
//        tableContainer.setWidget(cellTable);
//    }
//
//    @Override
//    public Widget asWidget() {
//        return widget;
//    }
//
//    @Override
//    public void setData(final List<AiChat> chats) {
//        this.allChats = chats != null
//                ? chats
//                : List.of();
//        searchBox.setText("");
//        applyFilter();
//    }
//
//    @UiHandler("searchBox")
//    public void onSearchKeyUp(final KeyUpEvent event) {
//        applyFilter();
//    }
//
//    private void applyFilter() {
//        final String filter = searchBox.getText() != null
//                ? searchBox.getText().trim().toLowerCase(Locale.ROOT)
//                : "";
//        if (filter.isEmpty()) {
//            cellTable.setRowData(0, allChats);
//            cellTable.setRowCount(allChats.size(), true);
//        } else {
//            final List<AiChat> filtered = new ArrayList<>();
//            for (final AiChat chat : allChats) {
//                if (chat.getTitle() != null
//                        && chat.getTitle().toLowerCase(Locale.ROOT).contains(filter)) {
//                    filtered.add(chat);
//                }
//            }
//            cellTable.setRowData(0, filtered);
//            cellTable.setRowCount(filtered.size(), true);
//        }
//    }
//
//    @Override
//    public AiChat getSelected() {
//        return selectionModel.getSelectedObject();
//    }
//
//    @Override
//    public void setSelected(final AiChat chat) {
//        selectionModel.setSelected(chat, true);
//    }
//
//    private static String formatRelativeTime(final long timeMs) {
//        if (timeMs <= 0) {
//            return "";
//        }
//        final long now = System.currentTimeMillis();
//        final long diff = now - timeMs;
//        final long seconds = diff / 1000;
//        final long minutes = seconds / 60;
//        final long hours = minutes / 60;
//        final long days = hours / 24;
//
//        if (days > 0) {
//            return days == 1
//                    ? "Yesterday"
//                    : days + " days ago";
//        } else if (hours > 0) {
//            return hours + (hours == 1
//                    ? " hour ago"
//                    : " hours ago");
//        } else if (minutes > 0) {
//            return minutes + (minutes == 1
//                    ? " minute ago"
//                    : " minutes ago");
//        } else {
//            return "Just now";
//        }
//    }
//
//    interface Binder extends UiBinder<Widget, AiChatHistoryViewImpl> {
//
//    }
}
