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


package stroom.gwt.workbench.client.widgets.tree;

import stroom.explorer.client.presenter.EntityTreeUiHandlers;
import stroom.explorer.client.presenter.ExplorerTree;
import stroom.explorer.client.view.EntityTreeViewImpl;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.widget.util.client.MultiSelectEvent;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// Stories for Stroom's [ExplorerTree]. The tree is Stroom's real explorer tree (its
/// `ExplorerTreeModel` fetches the nodes from the explorer service), answered by an
/// [ExplorerFixture] of a fixed tree.
///
/// The quick filter bar is the filter of Stroom's [EntityTreeViewImpl] (the explorer
/// tree with a quick filter, used in Stroom's explorer popups), shown with the stories' tree in it.
public final class ExplorerTreeStories {

    private static final String ON_OPEN = "onOpen";
    private static final String ON_SELECTION_CHANGE = "onSelectionChange";
    private static final String ON_NAME_FILTER_CHANGE = "onNameFilterChange";
    private static final String SELECTED_ROW = "cellTableSelectedRow";
    private static final String EXPANDER = ".explorerCell-expander";

    private ExplorerTreeStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // The open items and selection are internal state of Stroom's tree, and its nodes are
        // always fetched from the explorer service.
        registry.component("Widgets/Tree/ExplorerTree", ExplorerTreeStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // `ensureVisible` reveals a deep, collapsed node: its ancestors open, and it is
                // selected and scrolled into view
                .story("EnsureVisible", ExplorerTreeStories::ensureVisible)
                .withPlay(play -> {
                    // Stroom opens the root on the first fetch (minDepth 1), so 'Sub' is shown, but
                    // closed
                    play.findByText("Sub");
                    play.expect(play.queryByText("Deep Doc")).toBeNull();
                    play.click(play.getByRole("button", TextMatch.regex("reveal deep doc", "i")));
                    final Query deep = play.findByText("Deep Doc");
                    play.expect(deep).toBeInTheDocument();
                    // The row is the cell table's <tr>
                    play.waitFor(() -> play.expect(deep.closest("tr")).toHaveClass(SELECTED_ROW));
                })
                // Basic tree - open/close folders, click to select, double-click to "open"
                .story("Basic", context -> {
                    final Paragraph echo = echo("Selected: none — Opened: none");
                    final String[] opened = {"none"};
                    final Widget tree = tree(context, TreeFixtures.fixtureTree(), new Options()
                            .quickFilter(true)
                            .onSelection((names, doubleSelect) -> {
                                if (doubleSelect && !names.isEmpty()) {
                                    opened[0] = names.get(0);
                                }
                                echo.setText("Selected: " + (names.isEmpty()
                                        ? "none"
                                        : names.get(0)) + " — Opened: " + opened[0]);
                            }));
                    return padded(frame(tree), echo);
                })
                .withPlay(play -> {
                    // System is opened by the first fetch; click a child row to select it
                    play.click(play.findByText("Feeds"));
                    play.waitFor(() -> play.expect(play.getByText(TextMatch.regex("Selected:\\s*Feeds", ""), "p"))
                            .toBeInTheDocument());
                })
                // Quick filter - type in the bar to filter the tree (folders open to reveal matches)
                .story("Filtered", context -> {
                    final Paragraph echo = echo("Filter: \"feed\"");
                    final Widget tree = tree(context, TreeFixtures.fixtureTree(), new Options()
                            .quickFilter(true)
                            .nameFilter("feed", filter -> echo.setText("Filter: \"" + filter + "\"")));
                    return padded(frame(tree), echo);
                })
                // Type filter - only Feed and Pipeline documents (and their folders) are shown
                .story("TypeFiltered", context -> padded(frame(tree(context, TreeFixtures.fixtureTree(),
                        new Options().includedTypes("Feed", "Pipeline"))), null))
                // Alerts - nodes with issues show the alert icon (hover for the severity tooltip)
                .story("WithAlerts", context -> padded(frame(tree(context, TreeFixtures.fixtureTree(),
                                new Options().quickFilter(true).showAlerts())),
                        echo("Expand Dashboards → \"Error Report\" has alerts.")))
                // Multi-select - Ctrl/Shift+click over the rows
                .story("MultiSelect", context -> {
                    final Paragraph echo = echo("Selected 0 item(s).");
                    final Widget tree = tree(context, TreeFixtures.fixtureTree(), new Options()
                            .quickFilter(true)
                            .multiSelect()
                            .onSelection((names, doubleSelect) ->
                                    echo.setText("Selected " + names.size() + " item(s).")));
                    return padded(frame(tree), echo);
                })
                // Collapsing a folder the first fetch opened takes one click
                .story("CollapseViaLoadNodes", context -> frame(
                        tree(context, TreeFixtures.fixtureTree(), new Options())))
                .withPlay(play -> playCollapse(play, "Feeds"))
                // As above, with the first fetch opening System and its four folders
                .story("CollapseViaLoadNodesManyOpen", context -> frame(
                        tree(context, TreeFixtures.fixtureTree().openToDepth(2), new Options())))
                .withPlay(play -> playCollapse(play, "TEST_FEED"))
                // As above, with System opened before the first fetch, as the explorer opens its
                // first root
                .story("CollapseViaLoadNodesSeeded", context -> frame(
                        tree(context, TreeFixtures.fixtureTree().openToDepth(2), new Options().seedRoot())))
                .withPlay(play -> playCollapse(play, "TEST_FEED"))
                // Stroom's tree only fetches its nodes, so this is the seeded story again
                .story("CollapseViaLoadNodesWithStaticNodes", context -> frame(
                        tree(context, TreeFixtures.fixtureTree().openToDepth(2), new Options().seedRoot())))
                .withPlay(play -> playCollapse(play, "TEST_FEED"));
    }

    private static void playCollapse(final Play play, final String shownText) {
        // Arrives open: System's children are visible and its arrow points down
        play.findByText(shownText);
        play.expect(play.getByText("Feeds")).toBeInTheDocument();
        final Query expander = play.within(play.getByText("System").closest(".explorerCell")).querySelector(EXPANDER);
        play.expect(expander.className()).toMatch("arrow-down");
        // One click collapses it
        play.click(expander);
        play.waitFor(() -> play.expect(play.queryByText("Feeds")).toBeNull());
    }

    private static Widget ensureVisible(final StoryContext context) {
        final ExplorerFixture fixture = new ExplorerFixture(ExplorerFixture.folder("Root",
                ExplorerFixture.folder("Sub",
                        ExplorerFixture.doc("Deep Doc", "Dictionary"))));
        final Options options = new Options();
        final Widget treeWidget = tree(context, fixture, options);
        final ExplorerTree tree = options.tree;
        final Button reveal = new Button("Reveal Deep Doc");
        reveal.getElement().setAttribute("type", "button");
        reveal.addClickHandler(event -> {
            tree.getTreeModel().setEnsureVisible(fixture.toExplorerNode("Deep Doc"));
            tree.refresh();
        });
        final FlowPanel frame = frame(treeWidget);
        frame.insert(reveal, 0);
        return frame;
    }

    /// Creates the tree, fetching from the fixture, in Stroom's quick filter view if asked.
    private static Widget tree(final StoryContext context,
                               final ExplorerFixture fixture,
                               final Options options) {
        final ScreenHarness harness = ScreenHarness.create(context, TreeFixtures.explorerRoutes(fixture).build());
        final ExplorerTree tree = new ExplorerTree(harness.getRestFactory(),
                new DefaultTaskMonitorFactory(harness.getHasHandlers()),
                options.multiSelect,
                options.showAlerts);
        options.tree = tree;
        if (options.includedTypes != null) {
            tree.getTreeModel().setIncludedTypes(options.includedTypes);
        }
        if (options.nameFilter != null) {
            tree.getTreeModel().setInitialNameFilter(options.nameFilter);
        }

        final Spy onSelectionChange = context.fn(ON_SELECTION_CHANGE);
        final Spy onOpen = context.fn(ON_OPEN);
        harness.addRegistration(tree.addHandler(event -> {
            // The rows are styled when the table redraws, after the event
            Scheduler.get().scheduleDeferred(() -> {
                final List<String> names = selectedNames(tree);
                final boolean doubleSelect = event.getSelectionType().isDoubleSelect();
                onSelectionChange.call(String.join(", ", names));
                if (doubleSelect && !names.isEmpty()) {
                    onOpen.call(names.get(0));
                }
                if (options.onSelection != null) {
                    options.onSelection.accept(names, doubleSelect);
                }
            });
        }, MultiSelectEvent.getType()));

        if (options.seedRoot) {
            // As Stroom's explorer opens its first root before the first fetch
            tree.getTreeModel().setItemOpen(fixture.toExplorerNode("System"), true);
        } else {
            tree.refresh();
        }

        final Widget widget;
        if (options.quickFilter) {
            final EntityTreeViewImpl view = new EntityTreeViewImpl(GWT.create(EntityTreeViewImpl.Binder.class));
            view.setCellTree(tree);
            final Spy onNameFilterChange = context.fn(ON_NAME_FILTER_CHANGE);
            final EntityTreeUiHandlers handlers = filter -> {
                onNameFilterChange.call(filter);
                if (options.onNameFilterChange != null) {
                    options.onNameFilterChange.accept(filter);
                }
                tree.changeNameFilter(filter);
            };
            view.setUiHandlers(handlers);
            if (options.nameFilter != null) {
                // The filter's text, as Stroom's ExplorerPopupPresenter.setInitialQuickFilter shows it
                final NodeList<Element> inputs = view.asWidget().getElement().getElementsByTagName("input");
                if (inputs.getLength() > 0) {
                    InputElement.as(inputs.getItem(0)).setValue(options.nameFilter);
                }
            }
            widget = view.asWidget();
        } else {
            widget = tree;
        }
        widget.getElement().getStyle().setProperty("flex", "1");
        widget.getElement().getStyle().setProperty("minHeight", "0");
        return widget;
    }

    /// The names of the selected rows, read from the table as the user sees them.
    private static List<String> selectedNames(final ExplorerTree tree) {
        final List<String> names = new ArrayList<>();
        final NodeList<Element> rows = tree.getElement().getElementsByTagName("tr");
        for (int i = 0; i < rows.getLength(); i++) {
            final Element row = rows.getItem(i);
            if (row.hasClassName(SELECTED_ROW)) {
                final NodeList<Element> divs = row.getElementsByTagName("div");
                for (int j = 0; j < divs.getLength(); j++) {
                    if (divs.getItem(j).hasClassName("explorerCell-text")) {
                        names.add(divs.getItem(j).getInnerText());
                    }
                }
            }
        }
        return names;
    }

    /// The stories' frame around the tree.
    private static FlowPanel frame(final Widget tree) {
        final FlowPanel frame = new FlowPanel();
        final Style style = frame.getElement().getStyle();
        style.setProperty("width", "340px");
        style.setProperty("height", "460px");
        style.setProperty("border", "1px solid var(--panel__border-color, #444)");
        style.setProperty("display", "flex");
        style.setProperty("flexDirection", "column");
        style.setProperty("overflow", "auto");
        style.setProperty("resize", "both");
        frame.add(tree);
        return frame;
    }

    /// Equivalent of `<div style={{padding: 12}}>{frame}<p ...>{echo}</p></div>`.
    private static Widget padded(final Widget frame, final Paragraph echo) {
        final FlowPanel panel = new FlowPanel();
        panel.getElement().getStyle().setProperty("padding", "12px");
        panel.add(frame);
        if (echo != null) {
            panel.add(echo);
        }
        return panel;
    }

    /// Equivalent of `<p style={{fontSize: 12, marginTop: 8}}>`.
    private static Paragraph echo(final String text) {
        final Paragraph echo = new Paragraph();
        echo.getElement().getStyle().setProperty("fontSize", "12px");
        echo.getElement().getStyle().setProperty("marginTop", "8px");
        echo.setText(text);
        return echo;
    }


    // --------------------------------------------------------------------------------


    /// A `<p>` element.
    private static final class Paragraph extends Widget {

        private Paragraph() {
            setElement(Document.get().createPElement());
        }

        void setText(final String text) {
            getElement().setInnerText(text);
        }
    }


    // --------------------------------------------------------------------------------


    /// The tree's options that a story sets.
    private static final class Options {

        private ExplorerTree tree;
        private boolean quickFilter;
        private boolean multiSelect;
        private boolean showAlerts;
        private boolean seedRoot;
        private String[] includedTypes;
        private String nameFilter;
        private Consumer<String> onNameFilterChange;
        private SelectionListener onSelection;

        Options quickFilter(final boolean quickFilter) {
            this.quickFilter = quickFilter;
            return this;
        }

        Options multiSelect() {
            multiSelect = true;
            return this;
        }

        Options showAlerts() {
            showAlerts = true;
            return this;
        }

        Options seedRoot() {
            seedRoot = true;
            return this;
        }

        Options includedTypes(final String... types) {
            includedTypes = types;
            return this;
        }

        Options nameFilter(final String filter, final Consumer<String> onChange) {
            nameFilter = filter;
            onNameFilterChange = onChange;
            return this;
        }

        Options onSelection(final SelectionListener listener) {
            onSelection = listener;
            return this;
        }
    }


    // --------------------------------------------------------------------------------


    /// Told the selected names when the selection changes.
    private interface SelectionListener {

        void onSelection(List<String> names, boolean doubleSelect);

        default void accept(final List<String> names, final boolean doubleSelect) {
            onSelection(names, doubleSelect);
        }
    }
}
