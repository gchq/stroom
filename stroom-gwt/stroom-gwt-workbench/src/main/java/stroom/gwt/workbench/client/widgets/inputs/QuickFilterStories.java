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

package stroom.gwt.workbench.client.widgets.inputs;

import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.shared.filter.FilterFieldDefinition;
import stroom.widget.quickfilter.client.QuickFilter;
import stroom.widget.quickfilter.client.QuickFilterContextHandler;
import stroom.widget.quickfilter.client.QuickFilterPage;
import stroom.widget.quickfilter.client.QuickFilterTooltipUtil;
import stroom.widget.util.client.TableCell;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Frame;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/// Stories for [QuickFilter].
///
/// Stroom's [QuickFilter] always waits 400ms after the last key press
/// before reporting a change (or reports it at once on Enter or when cleared).
public final class QuickFilterStories {

    /// The name of the spy recording each time the quick filter asks for its recent filters.
    static final String ON_SHOW_RECENT_FILTERS = "onShowRecentFilters";
    /// The name of the spy recording the filters the quick filter asks to be remembered.
    static final String ON_RECORD_USE = "onRecordUse";

    private static final String MAX_WIDTH = "400px";
    // The recent filter the stand-in for Stroom's menu picks
    private static final String RECENT_FILTER = "name:alpha";
    // The stand-in server rejects a filter with a bracket in it, as Stroom's parser rejects one
    // that doesn't close
    private static final String REJECTED_CHARACTER = "(";
    private static final String REJECTION = "Expected ')' to close the group opened at 1:1";
    private static final List<String> ITEMS = Arrays.asList(
            "Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta");

    private QuickFilterStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Inputs/QuickFilter", QuickFilterStories.class)
                .layout(StoryLayout.CENTERED)
                // QuickFilter - filters a list in real-time
                .story("FiltersAList", context -> {
                    final QuickFilter quickFilter = quickFilter(context);
                    final FlowPanel list = StoryPanels.column(4);
                    showItems(list, "");
                    quickFilter.addValueChangeHandler(event -> showItems(list, event.getValue()));
                    final FlowPanel column = StoryPanels.column(12, quickFilter, list);
                    return InputWidgets.maxWidth(column, MAX_WIDTH);
                })
                .withPlay(play -> {
                    final Query textBox = play.querySelector(".quick-filter__input");
                    // A search field, named BEM style
                    play.expect(textBox).toHaveAttribute("type", "search");
                    play.expect(play.getByRole("searchbox")).toHaveClass("quick-filter__input");
                    play.expect(play.querySelector(".quick-filter")).not().toHaveClass("quickFilter");
                    play.type(textBox, "al");
                    play.click(play.getByTitle("Clear Filter"));
                    play.expect(textBox).toHaveValue("");
                    // The clear button hides itself, so focus goes back to the text (it was once lost)
                    play.expect(textBox).toHaveFocus();
                })
                // QuickFilter with the syntax-help popup populated. Click the ? button to open it.
                .story("WithHelp", context -> InputWidgets.maxWidth(quickFilterWithHelp(context), MAX_WIDTH))
                // The help ends with a link to the quick filter's documentation, which says what it
                // is and, to screen readers, that it opens a new tab (gchq/stroom#5406)
                .withPlay(play -> {
                    final Query helpButton = play.getByTitle("Quick Filter Syntax Help");
                    // The button says it opens a dialog, and whether it is open
                    play.expect(helpButton).toHaveAttribute("aria-haspopup", "dialog");
                    play.expect(helpButton).toHaveAttribute("aria-expanded", "false");
                    play.click(helpButton);
                    // The shared help popup, a dialog named for what it helps with
                    final Query help = play.screen().findByRole("dialog", "Quick Filter Syntax Help");
                    play.expect(help).toHaveClass("help-popup");
                    play.expect(help).not().toHaveClass("quickFilter-tooltip");
                    // Its tables fit in it: the long descriptions wrap rather than run past its edge
                    play.expect("how far the help's content runs past its right edge",
                            () -> overflowRight(help.element().get())).toBe(0);
                    play.expect(helpButton).toHaveAttribute("aria-expanded", "true");
                    final Query docsLink = play.screen().findByRole("link",
                            "quick filter documentation (opens in new tab)");
                    play.expect(docsLink).toHaveAttribute("href", "https://gchq.github.io/stroom-docs/");
                    play.expect(docsLink).toHaveAttribute("target", "_blank");
                    play.expect(docsLink).toHaveAttribute("rel", "noopener noreferrer");
                    // Sighted users see only the link's text
                    play.expect(play.screen().getByText("quick filter documentation")).toBeVisible();
                    play.expect(play.screen().getByText(TextMatch.containing("(opens in new tab)")))
                            .toHaveClass("sr-only");
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(play.screen().queryByRole("dialog")).toBeNull());
                    play.expect(helpButton).toHaveAttribute("aria-expanded", "false");
                    // Opened from the keyboard, the help takes the focus, so that a screen reader
                    // reads it (#5400), and Escape gives it back to the button
                    play.run("focus the help button", () -> helpButton.element().get().focus());
                    play.keyboard("{Enter}");
                    final Query popup = play.screen().findByRole("dialog", "Quick Filter Syntax Help");
                    play.waitFor(() -> play.expect(popup).toHaveFocus());
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(play.screen().queryByRole("dialog")).toBeNull());
                    play.expect(helpButton).toHaveFocus();
                })
                // The help popup beside a frame, e.g. a document's markdown preview: a click in the
                // frame closes it, as a click anywhere else does (#3550). The frame's click is its
                // own document's, so only a real click can show it (real input check)
                .story("WithHelpBesideFrame", context -> {
                    final Frame frame = new Frame();
                    frame.setTitle("Preview");
                    frame.getElement().setAttribute("srcdoc", "<p>A document's markdown preview.</p>");
                    frame.setSize("240px", "60px");
                    // The frame in the window's bottom left corner, clear of the popup
                    final Style style = frame.getElement().getStyle();
                    style.setProperty("position", "fixed");
                    style.setProperty("left", "0");
                    style.setProperty("bottom", "0");
                    return StoryPanels.column(8,
                            InputWidgets.maxWidth(quickFilterWithHelp(context), MAX_WIDTH),
                            frame);
                })
                // QuickFilter with no debounce - onChange fires on every keystroke
                .story("NoDebounce", context -> {
                    // QuickFilter's 400ms debounce can't be turned off, so the value below changes
                    // 400ms after typing stops.
                    final QuickFilter quickFilter = quickFilter(context);
                    final InlineLabel value = StoryPanels.note("Value: (empty)", "#aaa", "0.8rem");
                    quickFilter.addValueChangeHandler(event -> value.setText("Value: "
                            + (event.getValue().isEmpty()
                            ? "(empty)"
                            : event.getValue())));
                    final FlowPanel column = StoryPanels.column(8, quickFilter, value);
                    return InputWidgets.maxWidth(column, MAX_WIDTH);
                })
                // QuickFilter wrapped with a label (QuickFilterPage.setLabel)
                .story("WithLabel", context -> {
                    final QuickFilterPage page = new QuickFilterPage();
                    final Spy onChange = context.fn(InputWidgets.ON_CHANGE);
                    page.setOnFilterChange(text -> onChange.call(text));
                    page.setLabel("User Groups:");
                    page.setHelpText(SafeHtmlUtils.fromString("Filter the list of user groups."));
                    return InputWidgets.maxWidth(page, MAX_WIDTH);
                })
                // A quick filter with a context: its arrow (or Alt+Down) asks the screen for the
                // recent filters, and the one picked is put in the box, applied and remembered.
                // Stroom's screens show the filters in a menu (QuickFilterContextHandlerFactory);
                // here the stand-in picks one at once
                .story("RecentFilters", context -> withContext(context, quickFilter(context)))
                .withPlay(play -> {
                    final Query textBox = play.querySelector(".quick-filter__input");
                    play.click(play.getByTitle("Show Recent Filters"));
                    play.expect(play.spy(ON_SHOW_RECENT_FILTERS)).toHaveBeenCalledTimes(1);
                    play.expect(textBox).toHaveValue(RECENT_FILTER);
                    play.expect(play.spy(InputWidgets.ON_CHANGE)).toHaveBeenCalledWith(RECENT_FILTER);
                    // A recent filter was accepted when it was first used, so it is remembered at
                    // once rather than waiting for the server to accept it again
                    play.expect(play.spy(ON_RECORD_USE)).toHaveBeenCalledWith(RECENT_FILTER);
                    play.expect(textBox).toHaveFocus();
                    // Alt+Down opens the list from the keyboard, as a combo box's does
                    play.keyboard("{Alt>}{ArrowDown}{/Alt}");
                    play.expect(play.spy(ON_SHOW_RECENT_FILTERS)).toHaveBeenCalledTimes(2);
                })
                // A filter is remembered when it is committed (Enter, or leaving the box) and the
                // server accepted it. The filters applied while typing aren't remembered, nor is
                // one the server rejects. The stand-in server rejects a filter with a '('
                .story("RecordsAcceptedFilters", context -> withContext(context, quickFilter(context)))
                .withPlay(play -> {
                    final Query textBox = play.querySelector(".quick-filter__input");
                    play.type(textBox, "alpha");
                    // Applied after the 400ms pause, and accepted, but not committed
                    play.waitFor(() -> play.expect(play.spy(InputWidgets.ON_CHANGE)).toHaveBeenCalledWith("alpha"));
                    play.expect(play.spy(ON_RECORD_USE)).not().toHaveBeenCalled();
                    play.keyboard("{Enter}");
                    play.expect(play.spy(ON_RECORD_USE)).toHaveBeenCalledWith("alpha");
                    play.expect(play.spy(ON_RECORD_USE)).toHaveBeenCalledTimes(1);
                    // Committing it again doesn't remember it twice
                    play.tab();
                    play.expect(play.spy(ON_RECORD_USE)).toHaveBeenCalledTimes(1);
                    // A rejected filter isn't remembered, committed or not
                    play.type(textBox, " " + REJECTED_CHARACTER + "beta{Enter}");
                    play.expect(play.spy(InputWidgets.ON_CHANGE)).toHaveBeenCalledWith("alpha (beta");
                    play.expect(play.spy(ON_RECORD_USE)).toHaveBeenCalledTimes(1);
                    // Nor is clearing it
                    play.click(play.getByTitle("Clear Filter"));
                    play.expect(textBox).toHaveValue("");
                    play.expect(play.spy(ON_RECORD_USE)).toHaveBeenCalledTimes(1);
                })
                // A filter the server rejects comes back as an empty page, like one that matched
                // nothing, so the box says why: it is marked invalid, the reason is its hover text,
                // and the syntax help leads with it
                .story("RejectedFilter", context -> withContext(context, quickFilter(context)))
                .withPlay(play -> {
                    final Query textBox = play.querySelector(".quick-filter__input");
                    play.type(textBox, REJECTED_CHARACTER + "alpha{Enter}");
                    play.expect(textBox).toHaveAttribute("aria-invalid", "true");
                    play.expect(textBox).toHaveAttribute("title", REJECTION);
                    // Screen readers are told too: the box is invalid and described by the reason,
                    // which is in a live region, as the focus stays in the box (#5409)
                    play.expect(textBox).toHaveAttribute("aria-invalid", "true");
                    play.expect(textBox).toHaveAccessibleDescription(REJECTION);
                    play.expect(play.getByText(REJECTION)).toHaveAttribute("aria-live", "polite");
                    play.click(play.getByTitle("Quick Filter Syntax Help"));
                    play.expect(play.screen().findByText("This filter was not applied")).toBeInTheDocument();
                    // In the popup (the box's screen reader text says it too)
                    play.expect(play.screen().getByText(TextMatch.containing(REJECTION), ".help-popup *"))
                            .toBeInTheDocument();
                    play.keyboard("{Escape}");
                    // Clearing the box clears the error
                    play.click(play.getByTitle("Clear Filter"));
                    play.expect(textBox).not().toHaveAttribute("aria-invalid");
                    play.expect(textBox).not().toHaveAttribute("title");
                    play.expect(textBox).not().toHaveAttribute("aria-invalid");
                    play.expect(textBox).not().toHaveAttribute("aria-describedby");
                })
                // A disabled quick filter can't be typed in, so it offers no recent filters
                // either; its syntax help stays available
                .story("Disabled", context -> {
                    final QuickFilter quickFilter = quickFilter(context);
                    final Widget widget = withContext(context, quickFilter);
                    quickFilter.setEnabled(false);
                    return widget;
                })
                .withPlay(play -> {
                    final Query recentFilters = play.getByTitle("Show Recent Filters");
                    play.expect(recentFilters).toHaveAttribute("aria-disabled", "true");
                    play.click(recentFilters);
                    play.expect(play.spy(ON_SHOW_RECENT_FILTERS)).not().toHaveBeenCalled();
                    play.expect(play.getByTitle("Quick Filter Syntax Help")).not().toHaveAttribute("aria-disabled");
                });
    }

    // How many pixels the popup's widest content runs past its right edge (0 if none does)
    private static native int overflowRight(Element popup) /*-{
        var right = popup.getBoundingClientRect().right;
        var most = 0;
        var cells = popup.querySelectorAll('table, td, th');
        for (var i = 0; i < cells.length; i++) {
            most = Math.max(most, Math.ceil(cells[i].getBoundingClientRect().right - right));
        }
        return most;
    }-*/;

    // A quick filter with the syntax help popup populated, as Stroom's screens register it, e.g.
    // ServerTasksController
    private static QuickFilter quickFilterWithHelp(final StoryContext context) {
        final QuickFilter quickFilter = quickFilter(context);
        quickFilter.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                "Server Tasks Quick Filter",
                tableBuilder -> tableBuilder
                        .row(TableCell.data("Matched tasks are shown in black.", 2))
                        .row(TableCell.data("Related tasks are shown in grey.", 2))
                        .row(),
                Arrays.asList(
                        FilterFieldDefinition.defaultField("Name", "name"),
                        FilterFieldDefinition.qualifiedField("Node", "node"),
                        FilterFieldDefinition.qualifiedField("Status", "status")),
                "https://gchq.github.io/stroom-docs/"));
        return quickFilter;
    }

    // Gives the quick filter a context, with a stand-in for the server that accepts or rejects
    // each filter applied, as Stroom's list controllers report the server's answer
    private static Widget withContext(final StoryContext context, final QuickFilter quickFilter) {
        quickFilter.setContextHandler(new RecentFiltersHandler(
                context.fn(ON_SHOW_RECENT_FILTERS), context.fn(ON_RECORD_USE)));
        quickFilter.addValueChangeHandler(event -> quickFilter.setFilterError(
                event.getValue().contains(REJECTED_CHARACTER)
                        ? REJECTION
                        : null));
        return InputWidgets.maxWidth(quickFilter, MAX_WIDTH);
    }

    private static QuickFilter quickFilter(final StoryContext context) {
        final QuickFilter quickFilter = new QuickFilter();
        final Spy onChange = context.fn(InputWidgets.ON_CHANGE);
        quickFilter.addValueChangeHandler(event -> onChange.call(event.getValue()));
        return quickFilter;
    }

    /// Shows the items containing the filter (ignoring case), one row each.
    private static void showItems(final FlowPanel list, final String filter) {
        list.clear();
        final String lowerFilter = filter.toLowerCase(Locale.ROOT);
        for (final String item : ITEMS) {
            if (item.toLowerCase(Locale.ROOT).contains(lowerFilter)) {
                list.add(row(item));
            }
        }
    }

    /// A row of the filtered list.
    private static Widget row(final String item) {
        final Label row = new Label(item);
        final Style style = row.getElement().getStyle();
        style.setProperty("padding", "6px 10px");
        style.setProperty("background", "var(--row__background-color--even)");
        style.setProperty("borderRadius", "4px");
        style.setProperty("fontSize", "0.85rem");
        return row;
    }


    // --------------------------------------------------------------------------------


    // Stands in for QuickFilterContextHandlerFactory's handler: rather than fetch the recent
    // filters and show them in a menu, it picks one at once
    private static final class RecentFiltersHandler implements QuickFilterContextHandler {

        private final Spy onShowRecentFilters;
        private final Spy onRecordUse;

        private RecentFiltersHandler(final Spy onShowRecentFilters, final Spy onRecordUse) {
            this.onShowRecentFilters = onShowRecentFilters;
            this.onRecordUse = onRecordUse;
        }

        @Override
        public void showRecentFilters(final QuickFilter quickFilter, final Consumer<String> onSelect) {
            onShowRecentFilters.call();
            onSelect.accept(RECENT_FILTER);
        }

        @Override
        public void recordUse(final String filterText) {
            onRecordUse.call(filterText);
        }
    }
}
