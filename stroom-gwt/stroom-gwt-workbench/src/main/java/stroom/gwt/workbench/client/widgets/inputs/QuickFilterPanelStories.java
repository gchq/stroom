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

import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.dropdowntree.client.view.QuickFilterDialogViewImpl;
import stroom.widget.dropdowntree.client.view.QuickFilterPageViewImpl;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.gwtplatform.mvp.client.ViewImpl;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/// Stories for Stroom's two quick filter layouts, the page layout ([QuickFilterPageViewImpl]) and
/// the dialog layout ([QuickFilterDialogViewImpl]): a quick filter above a data panel. These
/// stories use the real views, with a list of rows as the data view.
public final class QuickFilterPanelStories {

    private static final List<String> ROWS = Arrays.asList(
            "England", "Scotland", "Wales", "Northern Ireland", "Isle of Man", "Jersey", "Guernsey");

    // The plays find the panel by the views' outermost class (max) and the results by their
    // position (the data panel, the panel's dock-max child).
    private static final String PANEL = ".max";
    private static final String RESULTS = ":scope > .dock-max";

    private QuickFilterPanelStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Inputs/QuickFilterPanel", QuickFilterPanelStories.class)
                .layout(StoryLayout.CENTERED)
                // The PAGE layout - GWT QuickFilterPageViewImpl: the filter sits in a form-padding
                // FormGroup over a bare data panel. No border anywhere.
                .story("Default", context -> {
                    final QuickFilterPageViewImpl view = new QuickFilterPageViewImpl(
                            GWT.create(QuickFilterPageViewImpl.Binder.class));
                    final RowsView rows = new RowsView();
                    view.setDataView(rows);
                    view.setUiHandlers(filterHandler(context, rows));
                    return frame(view.asWidget());
                })
                .withPlay(play -> {
                    final Query panel = play.querySelector(PANEL);
                    play.expect(panel).not().toBeNull();
                    play.expect(panel).toHaveClass("dock-container-vertical");
                    // No card: the border belongs to neither the outer nor the results in the page
                    // layout
                    play.expect(panel).not().toHaveClass("form-control-border");
                    play.expect(play.within(panel).querySelector(RESULTS)).not().toHaveClass("form-control-border");
                    // GWT's FormGroup contributes the padding
                    final Query group = play.within(panel).querySelector(".form-group.form-padding");
                    play.expect(group).not().toBeNull();
                    play.expect(play.within(group).querySelector(".quickFilter-textBox")).not().toBeNull();
                    play.expect(play.getByText("Scotland")).toBeInTheDocument();
                })
                // The DIALOG layout - GWT QuickFilterDialogViewImpl: no FormGroup and no padding, and
                // form-control-border form-control-background on the DATA PANEL alone
                .story("DialogVariant", context -> {
                    final QuickFilterDialogViewImpl view = new QuickFilterDialogViewImpl(
                            GWT.create(QuickFilterDialogViewImpl.Binder.class));
                    final RowsView rows = new RowsView();
                    view.setDataView(rows);
                    view.setUiHandlers(filterHandler(context, rows));
                    return frame(view.asWidget());
                })
                .withPlay(play -> {
                    final Query panel = play.querySelector(PANEL);
                    play.expect(panel).not().toBeNull();
                    play.expect(panel).toHaveClass("quickFilter-container");
                    // The filter is a direct child - no FormGroup wrapper in this variant
                    play.expect(play.within(panel).querySelector(".form-group.form-padding")).toBeNull();
                    final Query results = play.within(panel).querySelector(RESULTS);
                    play.expect(results).toHaveClass("form-control-border");
                    play.expect(results).toHaveClass("form-control-background");
                });
    }

    /// The stories' filtering: rows containing the trimmed filter, ignoring case.
    private static QuickFilterUiHandlers filterHandler(final StoryContext context, final RowsView rows) {
        final Spy onChange = context.fn(InputWidgets.ON_CHANGE);
        return text -> {
            onChange.call(text);
            rows.show(text);
        };
    }

    /// Equivalent of `<div style={{width: 320, height: 300, display: 'flex'}}>`.
    private static Widget frame(final Widget panel) {
        final FlowPanel frame = new FlowPanel();
        final Style style = frame.getElement().getStyle();
        style.setProperty("width", "320px");
        style.setProperty("height", "300px");
        style.setProperty("display", "flex");
        panel.getElement().getStyle().setProperty("flex", "1");
        frame.add(panel);
        return frame;
    }


    // --------------------------------------------------------------------------------


    /// The data view: `hoverable-row` rows, filtered.
    private static class RowsView extends ViewImpl {

        private final FlowPanel panel = new FlowPanel();

        RowsView() {
            show("");
        }

        @Override
        public Widget asWidget() {
            return panel;
        }

        /// Shows the rows containing the filter.
        ///
        /// @param filter The filter text, which is trimmed and matched ignoring case.
        void show(final String filter) {
            panel.clear();
            final String lowerFilter = filter == null
                    ? ""
                    : filter.trim().toLowerCase(Locale.ROOT);
            for (final String row : ROWS) {
                if (row.toLowerCase(Locale.ROOT).contains(lowerFilter)) {
                    final Label label = new Label(row);
                    label.setStyleName("hoverable-row");
                    label.getElement().getStyle().setProperty("padding", "4px 8px");
                    label.getElement().getStyle().setProperty("cursor", "pointer");
                    panel.add(label);
                }
            }
        }
    }
}
