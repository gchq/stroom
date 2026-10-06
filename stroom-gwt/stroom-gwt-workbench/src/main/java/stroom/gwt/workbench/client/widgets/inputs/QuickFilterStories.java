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
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.shared.filter.FilterFieldDefinition;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.dropdowntree.client.view.QuickFilterPageViewImpl;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.util.client.TableCell;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/// Stories for [QuickFilter], matching `Widgets/Inputs/QuickFilter` in the React Storybook.
///
/// Unlike the React port, Stroom's [QuickFilter] always waits 400ms after the last key press
/// before reporting a change (or reports it at once on Enter or when cleared).
public final class QuickFilterStories {

    private static final String MAX_WIDTH = "400px";
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
                // QuickFilter with the syntax-help popup populated. Click the ? button to open it.
                .story("WithHelp", context -> {
                    final QuickFilter quickFilter = quickFilter(context);
                    // Stroom's screens register their help with this, e.g. TaskManagerPresenter
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
                    return InputWidgets.maxWidth(quickFilter, MAX_WIDTH);
                })
                // QuickFilter with no debounce - onChange fires on every keystroke
                .story("NoDebounce", context -> {
                    // Differs from React: QuickFilter's 400ms debounce can't be turned off (React's
                    // debounceMs={0}), so the value below changes 400ms after typing stops.
                    final QuickFilter quickFilter = quickFilter(context);
                    final InlineLabel value = StoryPanels.note("Value: (empty)", "#aaa", "0.8rem");
                    quickFilter.addValueChangeHandler(event -> value.setText("Value: "
                            + (event.getValue().isEmpty()
                            ? "(empty)"
                            : event.getValue())));
                    final FlowPanel column = StoryPanels.column(8, quickFilter, value);
                    return InputWidgets.maxWidth(column, MAX_WIDTH);
                })
                // QuickFilter wrapped with a label (GWT QuickFilterPageViewImpl.setLabel)
                .story("WithLabel", context -> {
                    final QuickFilterPageViewImpl view = new QuickFilterPageViewImpl(
                            GWT.create(QuickFilterPageViewImpl.Binder.class));
                    final Spy onChange = context.fn(InputWidgets.ON_CHANGE);
                    view.setUiHandlers(text -> onChange.call(text));
                    view.setLabel("User Groups:");
                    view.setHelpText(SafeHtmlUtils.fromString("Filter the list of user groups."));
                    return InputWidgets.maxWidth(view.asWidget(), MAX_WIDTH);
                });
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

    /// Equivalent of the React story's `<li>` rows.
    private static Widget row(final String item) {
        final Label row = new Label(item);
        final Style style = row.getElement().getStyle();
        style.setProperty("padding", "6px 10px");
        style.setProperty("background", "var(--row__background-color,#2a2e38)");
        style.setProperty("borderRadius", "4px");
        style.setProperty("fontSize", "0.85rem");
        return row;
    }
}
