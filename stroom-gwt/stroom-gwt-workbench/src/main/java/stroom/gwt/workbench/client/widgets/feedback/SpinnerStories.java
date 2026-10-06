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


package stroom.gwt.workbench.client.widgets.feedback;

import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.button.client.Button;
import stroom.widget.spinner.client.SpinnerLarge;
import stroom.widget.spinner.client.SpinnerSmall;

import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;

import java.util.concurrent.atomic.AtomicBoolean;

/// Stories for Stroom's [SpinnerSmall] and [SpinnerLarge], matching `Widgets/Feedback/Spinner`
/// in the React Storybook.
public final class SpinnerStories {

    private SpinnerStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Feedback/Spinner", SpinnerStories.class)
                .layout(StoryLayout.CENTERED)
                // The inline loading spinner (SpinnerSmall)
                .story("Inline", context -> inline())
                // The centred overlay spinner (SpinnerLarge with spinner-center)
                .story("Overlay", context -> overlay());
    }

    private static Widget inline() {
        final SpinnerSmall spinner = new SpinnerSmall();
        // React's `visible` is SpinnerSmall's `refreshing` style
        spinner.setRefreshing(true);
        final InlineLabel text = StoryPanels.note("Inline loading spinner", "#ccc", "0.85rem");
        final FlowPanel spinnerRow = StoryPanels.row(12, spinner, text);
        spinnerRow.getElement().getStyle().setProperty("flexWrap", "nowrap");

        final InlineLabel state = StoryPanels.note("visible", "#ccc", "0.8rem");
        state.getElement().getStyle().setProperty("alignSelf", "center");
        final Button toggle = button("Toggle");
        final AtomicBoolean running = new AtomicBoolean(true);
        toggle.addClickHandler(event -> {
            running.set(!running.get());
            spinner.setRefreshing(running.get());
            state.setText(running.get()
                    ? "visible"
                    : "hidden");
        });
        final FlowPanel buttonRow = StoryPanels.row(8, toggle, state);
        buttonRow.getElement().getStyle().clearProperty("alignItems");
        buttonRow.getElement().getStyle().setProperty("flexWrap", "nowrap");
        return StoryPanels.column(16, spinnerRow, buttonRow);
    }

    private static Widget overlay() {
        final FlowPanel box = new FlowPanel();
        final Style style = box.getElement().getStyle();
        style.setProperty("position", "relative");
        style.setProperty("width", "200px");
        style.setProperty("height", "100px");
        style.setProperty("background", "var(--panel__background-color,#1e2233)");
        style.setProperty("borderRadius", "6px");
        style.setProperty("display", "flex");
        style.setProperty("alignItems", "center");
        style.setProperty("justifyContent", "center");
        final InlineLabel text = StoryPanels.note("Content behind overlay", "#ccc", "0.8rem");
        text.getElement().getStyle().setProperty("opacity", "0.4");
        box.add(text);

        // As Stroom's views add it in their UiBinder templates, e.g. DataViewImpl
        final SpinnerLarge spinner = new SpinnerLarge();
        spinner.addStyleName("spinner-center");
        // A new SpinnerLarge thinks it is visible but has no `spinner__visible` style, so is
        // transparent until its task monitor starts a task; hide it then show it, as when a task
        // starts after an earlier one ended
        spinner.setVisible(false);
        spinner.setVisible(true);
        box.add(spinner);

        final AtomicBoolean loading = new AtomicBoolean(true);
        final Button toggle = button("Toggle overlay");
        toggle.addClickHandler(event -> {
            loading.set(!loading.get());
            spinner.setVisible(loading.get());
        });
        return StoryPanels.column(16, box, toggle);
    }

    private static Button button(final String text) {
        final Button button = new Button();
        button.setText(text);
        return button;
    }
}
