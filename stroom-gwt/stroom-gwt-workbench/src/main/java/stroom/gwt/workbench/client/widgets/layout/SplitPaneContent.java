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

package stroom.gwt.workbench.client.widgets.layout;

import stroom.editor.client.component.CodeEditorController;
import stroom.gwt.workbench.client.widgets.editorsandviewers.EditorWidgets;
import stroom.util.client.DataGridUtil;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.form.client.TextBox;

import com.google.gwt.dom.client.Style;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.ListDataProvider;

import java.util.ArrayList;
import java.util.List;

/// Content for the split pane stories that is like what Stroom's split screens hold: a paged
/// table, a code editor, a form, and plain blocks whose size can be measured to the pixel.
final class SplitPaneContent {

    private static final String[] ROLES = {"Engineer", "Designer", "Product", "QA", "Manager"};

    private SplitPaneContent() {
        // Static utility
    }

    /// A block of colour with a label and nothing else in it (no padding or border), so its size
    /// is the size of the pane or layer it fills, down to nothing.
    ///
    /// @param label  Its label.
    /// @param testId Its test id.
    /// @param tinted Whether it is tinted, to tell it from the block beside it.
    /// @return The block.
    static Widget block(final String label, final String testId, final boolean tinted) {
        final HTML block = new HTML(label);
        block.getElement().setAttribute("data-testid", testId);
        final Style style = block.getElement().getStyle();
        style.setProperty("overflow", "hidden");
        style.setProperty("whiteSpace", "nowrap");
        style.setProperty("fontSize", "0.8rem");
        style.setProperty("width", "100%");
        style.setProperty("height", "100%");
        style.setProperty("boxSizing", "border-box");
        if (tinted) {
            style.setProperty("background", "var(--row__background-color--even)");
        }
        return block;
    }

    /// Stroom's data grid, with a pager, holding many rows, as a list screen shows them.
    ///
    /// @param testId The test id of the grid.
    /// @return The pager and grid.
    static Widget table(final String testId) {
        final DataGrid<String[]> grid =
                new DataGrid<>(DataGrid.DEFAULT_LIST_PAGE_SIZE);
        grid.addResizableColumn(DataGridUtil.textColumnBuilder((String[] row) -> row[0]).build(), "Name", 200);
        grid.addResizableColumn(DataGridUtil.textColumnBuilder((String[] row) -> row[1]).build(), "Role", 120);
        grid.addResizableColumn(DataGridUtil.textColumnBuilder((String[] row) -> row[2]).build(), "Notes", 400);
        final PagerPanel pagerView = new PagerPanel();
        pagerView.setDataWidget(grid);
        final List<String[]> rows = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            rows.add(new String[]{"Person " + i, ROLES[i % ROLES.length], "Notes about person " + i});
        }
        new ListDataProvider<>(rows).addDataDisplay(grid);
        grid.getElement().setAttribute("data-testid", testId);
        return pagerView.asWidget();
    }

    /// Stroom's code editor (Ace), holding a pipeline's XML.
    ///
    /// @return The editor.
    static Widget editor() {
        final CodeEditorController editor = EditorWidgets.codeEditor(new SimpleEventBus());
        final StringBuilder text = new StringBuilder("<pipeline>\n");
        for (int i = 1; i <= 40; i++) {
            text.append("  <element id=\"element").append(i).append("\" type=\"XSLTFilter\"/>\n");
        }
        editor.setText(text.append("</pipeline>\n").toString(), false);
        return editor.asWidget();
    }

    /// A form of two named fields, so a pane has tab stops in it.
    ///
    /// @param testId The test id of the form.
    /// @return The form.
    static Widget form(final String testId) {
        final FlowPanel form = new FlowPanel();
        form.getElement().setAttribute("data-testid", testId);
        form.getElement().getStyle().setProperty("padding", "8px");
        for (final String name : List.of("Name", "Description")) {
            form.add(new Label(name));
            final TextBox field = new TextBox();
            field.getElement().setAttribute("aria-label", name);
            form.add(field);
        }
        return form;
    }
}
