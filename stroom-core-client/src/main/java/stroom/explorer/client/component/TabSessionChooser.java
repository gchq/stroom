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

package stroom.explorer.client.component;

import stroom.explorer.shared.TabSession;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.DataGridSelectionEventManager;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// The tab sessions to choose from: a list of their names, the one with the focus chosen. Enter, or
/// a double click, on one confirms it.
public class TabSessionChooser extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final Template TEMPLATE = GWT.create(Template.class);

    @UiField
    DataGrid<TabSession> list;

    private final MultiSelectionModelImpl<TabSession> selectionModel = new MultiSelectionModelImpl<>();
    private Runnable onConfirm = () -> {
    };

    /// Makes an empty list.
    public TabSessionChooser() {
        initWidget(BINDER.createAndBindUi(this));
        // Escape goes on to the dialog (Cancel)
        list.setSelectionModel(selectionModel, new DataGridSelectionEventManager<>(list, selectionModel, false));
        selectionModel.addSelectionHandler(event -> {
            if (event.getSelectionType().isDoubleSelect()) {
                onConfirm.run();
            }
        });
        list.addColumn(new Column<TabSession, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final TabSession session) {
                return TEMPLATE.item(session == null
                        ? SafeHtmlUtils.EMPTY_SAFE_HTML
                        : SafeHtmlUtils.fromString(session.getName()));
            }
        }, "", 200);
    }

    /// @param sessions The sessions, a row each.
    public void setSessions(final List<TabSession> sessions) {
        list.setRowData(0, sessions);
        list.setRowCount(sessions.size());
    }

    /// @return The session chosen, or `null`.
    public TabSession getSelected() {
        return selectionModel.getSelected();
    }

    /// @param onConfirm Run when Enter, or a double click, confirms a session.
    public void setOnConfirm(final Runnable onConfirm) {
        this.onConfirm = onConfirm;
    }

    /// Puts the focus on the list.
    public void focus() {
        list.setFocus(true);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, TabSessionChooser> {

    }

    // A session's name in the list
    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"tab-session-chooser__item\">{0}</div>")
        SafeHtml item(SafeHtml name);
    }
}
