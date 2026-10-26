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

import stroom.ai.client.AiChatCell;
import stroom.ai.shared.AiChat;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.FindResultLists;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.function.Consumer;

/// The chats found: a striped list of a page of them, the one with the focus chosen. Enter, or a
/// double click, opens one, and Up on the first goes back to the filter.
public class AiChatHistoryResultList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<AiChat> resultList;

    private final MultiSelectionModelImpl<AiChat> selectionModel = new MultiSelectionModelImpl<>();
    private Consumer<AiChat> onOpen = chat -> {
    };
    private Runnable onUpFromFirst = () -> {
    };

    /// Makes an empty list, with no column until [#addColumn(AiChatCell)].
    public AiChatHistoryResultList() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Adds the list's one column.
    ///
    /// @param cell Draws a chat.
    public void addColumn(final AiChatCell cell) {
        FindResultLists.wire(
                resultList,
                cell,
                selectionModel,
                chat -> onOpen.accept(chat),
                () -> onUpFromFirst.run());
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<AiChat> dataProvider) {
        dataProvider.addDataDisplay(resultList);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection: the chat with the focus.
    public MultiSelectionModel<AiChat> getSelectionModel() {
        return selectionModel;
    }

    /// @return The absolute index of the first row shown.
    public int getPageStart() {
        return resultList.getPageStart();
    }

    /// @param pageStart The absolute index of the first row to show.
    public void setPageStart(final int pageStart) {
        resultList.setPageStart(pageStart);
    }

    /// Moves the focus to a row.
    ///
    /// @param row        The row on the page.
    /// @param stealFocus Whether to take the focus from where it is.
    public void setKeyboardSelectedRow(final int row, final boolean stealFocus) {
        resultList.setKeyboardSelectedRow(row, stealFocus);
    }

    /// @param onOpen Given a chat to open (Enter, or a double click, on it).
    public void setOnOpen(final Consumer<AiChat> onOpen) {
        this.onOpen = onOpen;
    }

    /// @param onUpFromFirst Run when Up is pressed on the first chat.
    public void setOnUpFromFirst(final Runnable onUpFromFirst) {
        this.onUpFromFirst = onUpFromFirst;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AiChatHistoryResultList> {

    }
}
