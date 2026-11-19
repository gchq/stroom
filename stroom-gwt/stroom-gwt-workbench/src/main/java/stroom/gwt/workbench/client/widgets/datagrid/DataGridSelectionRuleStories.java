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

package stroom.gwt.workbench.client.widgets.datagrid;

import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.framework.client.play.EventInit;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.Button;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.text.client.Label;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AsyncDataProvider;
import com.google.gwt.view.client.HasData;
import com.google.gwt.view.client.Range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// What a paged list selects after each action (R-S14 in `DATA_GRID_DESIGN.md`, "What a list
/// selects" in `UI-GUIDE.md`): Delete selects the row now in the first deleted row's place, else
/// the new last row; a change of page drops rows no longer shown; Add or a rename selects the item
/// if the page it lands on is shown, else nothing.
///
/// The list is sorted by name and fetched a page of five at a time, as a server's list is: each
/// reply arrives later, its rows first, then the count, as `RestDataProvider` gives them. Delete
/// and Add change the "server's" rows, then fetch the page again.
public final class DataGridSelectionRuleStories {

    private static final String GRID = "grid";
    private static final String SELECTED = "selected";
    private static final String SELECTED_PREFIX = "Selected: ";
    private static final int PAGE_SIZE = 5;

    private DataGridSelectionRuleStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Data Grid/DataGridSelectionRules", DataGridSelectionRuleStories.class)
                .layout(StoryLayout.PADDED)
                .story("PagedList", context -> render())
                .story("DeleteSelectsNext", context -> render())
                .withPlay(DataGridSelectionRuleStories::playDeleteSelectsNext)
                .story("DeleteLastSelectsPrevious", context -> render())
                .withPlay(DataGridSelectionRuleStories::playDeleteLastSelectsPrevious)
                .story("MultiDeleteSelectsNext", context -> render())
                .withPlay(DataGridSelectionRuleStories::playMultiDeleteSelectsNext)
                .story("DeleteEverySelectsNothing", context -> render())
                .withPlay(DataGridSelectionRuleStories::playDeleteEverySelectsNothing)
                .story("PageChangeDropsSelection", context -> render())
                .withPlay(DataGridSelectionRuleStories::playPageChangeDropsSelection)
                .story("AddSelectsIt", context -> render())
                .withPlay(DataGridSelectionRuleStories::playAddSelectsIt)
                .story("AddOnAnotherPageSelectsNothing", context -> render())
                .withPlay(DataGridSelectionRuleStories::playAddOnAnotherPageSelectsNothing)
                .story("RenameOntoAnotherPageSelectsNothing", context -> render())
                .withPlay(DataGridSelectionRuleStories::playRenameOntoAnotherPageSelectsNothing);
    }

    // --------------------------------------------------------------------------------
    // Plays

    private static void playDeleteSelectsNext(final Play play) {
        choose(play, "Row 02");
        play.click(play.getByRole("button", "Delete"));
        expectSelected(play, "Row 03");
        expectRowSelected(play, "Row 03");
        play.waitFor(() -> play.expect(play.within(play.getByTestId(GRID)).queryByText("Row 02")).toBeNull());
    }

    // The pager always shows a full last page, so deleting the last row moves the page back one row:
    // the new last row is selected
    private static void playDeleteLastSelectsPrevious(final Play play) {
        play.click(play.getByTitle("Last"));
        choose(play, "Row 12");
        play.click(play.getByRole("button", "Delete"));
        expectSelected(play, "Row 11");
        expectRowSelected(play, "Row 11");
    }

    private static void playMultiDeleteSelectsNext(final Play play) {
        choose(play, "Row 02");
        play.fireEvent().mouseDown(play.within(play.getByTestId(GRID)).getByText("Row 04"),
                EventInit.create().ctrlKey());
        expectSelected(play, "Row 04, Row 02");
        play.click(play.getByRole("button", "Delete"));
        // Row 03 is now where Row 02 was
        expectSelected(play, "Row 03");
        expectRowSelected(play, "Row 03");
    }

    private static void playDeleteEverySelectsNothing(final Play play) {
        play.click(play.getByRole("button", "Delete All"));
        play.waitFor(() -> play.expect(play.within(play.getByTestId(GRID)).queryByText("Row 01")).toBeNull());
        choose(play, "Only");
        play.click(play.getByRole("button", "Delete"));
        expectSelected(play, "none");
    }

    private static void playPageChangeDropsSelection(final Play play) {
        choose(play, "Row 02");
        play.click(play.getByTitle("Forward"));
        play.waitFor(() -> play.expect(play.within(play.getByTestId(GRID)).getByText("Row 06")).toBeInTheDocument());
        expectSelected(play, "none");
        play.click(play.getByTitle("First"));
        play.waitFor(() -> play.expect(play.within(play.getByTestId(GRID)).getByText("Row 02")).toBeInTheDocument());
        expectSelected(play, "none");
        play.expect(row(play, "Row 02")).toHaveAttribute("aria-selected", "false");
    }

    private static void playAddSelectsIt(final Play play) {
        choose(play, "Row 02");
        play.click(play.getByRole("button", "Add Row 00"));
        expectSelected(play, "Row 00");
        expectRowSelected(play, "Row 00");
    }

    private static void playAddOnAnotherPageSelectsNothing(final Play play) {
        choose(play, "Row 02");
        play.click(play.getByRole("button", "Add Row 99"));
        expectSelected(play, "none");
    }

    private static void playRenameOntoAnotherPageSelectsNothing(final Play play) {
        choose(play, "Row 02");
        play.click(play.getByRole("button", "Rename to Row 99"));
        play.waitFor(() -> play.expect(play.within(play.getByTestId(GRID)).queryByText("Row 02")).toBeNull());
        expectSelected(play, "none");
    }

    private static void choose(final Play play, final String name) {
        play.click(play.within(play.getByTestId(GRID)).getByText(name));
        expectSelected(play, name);
    }

    private static Query row(final Play play, final String name) {
        return play.within(play.getByTestId(GRID)).getByText(name).closest("[role='row']");
    }

    private static void expectRowSelected(final Play play, final String name) {
        play.waitFor(() -> play.expect(row(play, name)).toHaveAttribute("aria-selected", "true"));
    }

    private static void expectSelected(final Play play, final String names) {
        play.waitFor(() -> play.expect(play.getByTestId(SELECTED)).toHaveTextContent(SELECTED_PREFIX + names));
    }

    // --------------------------------------------------------------------------------
    // Rendering

    // A list of twelve names fetched five at a time, with buttons that change the "server's" names
    // and a note of what is selected
    private static Widget render() {
        final List<String> names = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            names.add("Row " + (i < 10 ? "0" : "") + i);
        }
        final DataGrid<String> grid = new DataGrid<>(PAGE_SIZE);
        grid.setKeyProvider(name -> name);
        grid.addColumn(DataGridUtil.textColumnBuilder((String name) -> name).build(), "Name", 160);
        final MultiSelectionModelImpl<String> selectionModel = grid.addDefaultSelectionModel(true);

        final AsyncDataProvider<String> dataProvider = new AsyncDataProvider<String>() {
            @Override
            protected void onRangeChanged(final HasData<String> display) {
                fetch(this, names, display.getVisibleRange());
            }
        };
        dataProvider.addDataDisplay(grid);
        final Runnable refresh = () -> fetch(dataProvider, names, grid.getVisibleRange());

        final Button delete = new Button();
        delete.setText("Delete");
        delete.addClickHandler(event -> {
            final List<String> selected = selectionModel.getSelectedItems();
            selectionModel.selectNextAfterRemove(selected);
            names.removeAll(selected);
            refresh.run();
        });
        final Button deleteAll = new Button();
        deleteAll.setText("Delete All");
        deleteAll.addClickHandler(event -> {
            names.clear();
            names.add("Only");
            refresh.run();
        });
        final Button addFirst = addButton("Row 00", names, selectionModel, refresh);
        final Button addLast = addButton("Row 99", names, selectionModel, refresh);
        final Button rename = new Button();
        rename.setText("Rename to Row 99");
        rename.addClickHandler(event -> {
            final String selected = selectionModel.getSelected();
            if (selected != null) {
                names.remove(selected);
                names.add("Row 99");
                Collections.sort(names);
                selectionModel.setSelected("Row 99");
                refresh.run();
            }
        });

        final Label selectedNote = new Label(SELECTED_PREFIX + "none");
        selectedNote.getElement().setAttribute("data-testid", SELECTED);
        selectionModel.addSelectionChangeHandler(event -> Scheduler.get().scheduleDeferred(() -> {
            final List<String> selected = selectionModel.getSelectedItems();
            selectedNote.setText(SELECTED_PREFIX + (selected.isEmpty()
                    ? "none"
                    : String.join(", ", selected)));
        }));

        grid.setWidth("100%");
        grid.setHeight("100%");
        final PagerPanel pagerPanel = new PagerPanel();
        pagerPanel.setDataWidget(grid);
        final SimplePanel frame = new SimplePanel(pagerPanel.asWidget());
        frame.getElement().setAttribute("data-testid", GRID);
        frame.getElement().getStyle().setProperty("height", "220px");
        frame.getElement().getStyle().setProperty("width", "320px");
        return StoryPanels.column(12,
                frame,
                StoryPanels.row(8, delete, deleteAll, addFirst, addLast, rename),
                selectedNote);
    }

    // Adds a name and selects it, then fetches the page again, as a screen does after its New dialog
    private static Button addButton(final String name,
                                    final List<String> names,
                                    final MultiSelectionModelImpl<String> selectionModel,
                                    final Runnable refresh) {
        final Button button = new Button();
        button.setText("Add " + name);
        button.addClickHandler(event -> {
            names.add(name);
            Collections.sort(names);
            selectionModel.setSelected(name);
            refresh.run();
        });
        return button;
    }

    // The page's names, later, as a server's reply: the rows, then the count
    private static void fetch(final AsyncDataProvider<String> dataProvider,
                              final List<String> names,
                              final Range range) {
        Scheduler.get().scheduleDeferred(() -> {
            final int start = Math.min(range.getStart(), names.size());
            final int end = Math.min(start + range.getLength(), names.size());
            dataProvider.updateRowData(start, new ArrayList<>(names.subList(start, end)));
            dataProvider.updateRowCount(names.size(), true);
        });
    }
}
