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
import stroom.data.table.client.MyCellTable;
import stroom.widget.util.client.BasicSelectionEventManager;
import stroom.widget.util.client.MySingleSelectionModel;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.ScrollPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.CellPreviewEvent;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.List;

public class AiChatHistoryViewImpl
        extends ViewWithUiHandlers<AiChatHistoryUiHandlers>
        implements AiChatHistoryView {

    interface Binder extends UiBinder<Widget, AiChatHistoryViewImpl> {

    }

    private final Widget widget;

    @UiField
    ScrollPanel tableContainer;

    private final CellTable<AiChat> cellTable;
    private final MySingleSelectionModel<AiChat> selectionModel = new MySingleSelectionModel<>();

    @Inject
    public AiChatHistoryViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        cellTable = new MyCellTable<>(Integer.MAX_VALUE);
        cellTable.setSelectionModel(selectionModel, new BasicSelectionEventManager<AiChat>(cellTable) {
            @Override
            protected void onExecute(final CellPreviewEvent<AiChat> e) {
                final AiChat chat = e.getValue();
                selectionModel.setSelected(chat, true);
                if (getUiHandlers() != null) {
                    getUiHandlers().onOpen(chat);
                }
            }
        });

        // Title column.
        final Column<AiChat, String> titleColumn = new Column<AiChat, String>(new TextCell()) {
            @Override
            public String getValue(final AiChat chat) {
                return chat.getTitle();
            }
        };
        cellTable.addColumn(titleColumn, "Title");
        cellTable.setColumnWidth(titleColumn, "100%");

        // Last updated column.
        final Column<AiChat, String> dateColumn = new Column<AiChat, String>(new TextCell()) {
            @Override
            public String getValue(final AiChat chat) {
                return formatRelativeTime(chat.getUpdateTimeMs());
            }
        };
        cellTable.addColumn(dateColumn, "Last Active");

        tableContainer.setWidget(cellTable);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setData(final List<AiChat> chats) {
        cellTable.setRowData(0, chats);
        cellTable.setRowCount(chats.size(), true);
    }

    @Override
    public AiChat getSelected() {
        return selectionModel.getSelectedObject();
    }

    @Override
    public void setSelected(final AiChat chat) {
        selectionModel.setSelected(chat, true);
    }

    private static String formatRelativeTime(final long timeMs) {
        if (timeMs <= 0) {
            return "";
        }
        final long now = System.currentTimeMillis();
        final long diff = now - timeMs;
        final long seconds = diff / 1000;
        final long minutes = seconds / 60;
        final long hours = minutes / 60;
        final long days = hours / 24;

        if (days > 0) {
            return days == 1
                    ? "Yesterday"
                    : days + " days ago";
        } else if (hours > 0) {
            return hours + (hours == 1
                    ? " hour ago"
                    : " hours ago");
        } else if (minutes > 0) {
            return minutes + (minutes == 1
                    ? " minute ago"
                    : " minutes ago");
        } else {
            return "Just now";
        }
    }
}
